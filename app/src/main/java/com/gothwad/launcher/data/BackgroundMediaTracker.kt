package com.gothwad.launcher.data

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.TvNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class BackgroundMediaState(
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val isStockAdCandidate: Boolean = false,
)

object BackgroundMediaTracker {

    /**
     * Polls and observes audio/media playback across the system.
     * Updates smoothly every ~1.5 seconds.
     */
    fun backgroundMediaFlow(context: Context): Flow<BackgroundMediaState> = flow {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mediaSessionManager = runCatching {
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }.getOrNull()
        val notificationComponent = ComponentName(context, TvNotificationListenerService::class.java)

        while (true) {
            val isMusicActive = audioManager?.isMusicActive == true
            var activePkg: String? = null
            var activeTitle: String? = null
            var activeAppName: String? = null

            if (mediaSessionManager != null) {
                runCatching {
                    val controllers = mediaSessionManager.getActiveSessions(notificationComponent)
                    for (controller in controllers) {
                        val state = controller.playbackState?.state
                        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                            activePkg = controller.packageName
                            activeTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                                ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                            break
                        }
                    }
                }
            }

            if (activePkg != null) {
                activeAppName = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(activePkg, 0)).toString()
                }.getOrNull() ?: activePkg
            }

            val isStock = activePkg != null && LauncherAccessibilityService.isStockTvLauncher(activePkg)
            val isPlaying = isMusicActive || activePkg != null

            emit(
                BackgroundMediaState(
                    isPlaying = isPlaying,
                    packageName = activePkg,
                    appName = activeAppName ?: if (isPlaying) "Background Audio" else null,
                    title = activeTitle,
                    isStockAdCandidate = isStock || (isPlaying && activePkg == null),
                )
            )

            delay(1500L)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Requests transient exclusive audio focus to forcibly pause and silence
     * any rogue video ad, stock launcher ad, or background audio.
     */
    fun silenceAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    /**
     * Attempts to stop or pause active media sessions.
     */
    fun stopActiveMedia(context: Context) {
        silenceAudio(context)
        val mediaSessionManager = runCatching {
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }.getOrNull() ?: return
        val notificationComponent = ComponentName(context, TvNotificationListenerService::class.java)

        runCatching {
            val controllers = mediaSessionManager.getActiveSessions(notificationComponent)
            controllers.forEach { controller ->
                controller.transportControls?.pause()
                controller.transportControls?.stop()
            }
        }
    }

    /**
     * Terminates background process of the app if possible.
     */
    fun killPackage(context: Context, packageName: String?) {
        silenceAudio(context)
        if (!packageName.isNullOrBlank()) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
        }
    }

    /**
     * Opens App Details / System Settings for the package so user can force stop or disable it.
     */
    fun openAppInfo(context: Context, packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            runCatching {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}
