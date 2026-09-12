package com.gothwad.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

val trustAllHttpClient: OkHttpClient by lazy {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    OkHttpClient.Builder()
        .sslSocketFactory(ctx.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * Live video wallpaper behind the launcher, muted, aspect-filled, via ExoPlayer.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoWallpaper(
    uri: String,
    speed: Float = 1f,
    loop: Boolean = true,
    coverBrush: Brush,
    onEnded: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alpha = remember { Animatable(0f) }
    var firstFrameGen by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(lifecycleOwner) {
        fun build(): ExoPlayer {
            val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            val http = OkHttpDataSource.Factory(trustAllHttpClient)
            val dataSource = DefaultDataSource.Factory(context, http)
            val sourceFactory = DefaultMediaSourceFactory(dataSource)
            return ExoPlayer.Builder(context)
                .setRenderersFactory(renderers)
                .setMediaSourceFactory(sourceFactory)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 15_000, 2_500, 5_000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()
                )
                .build().apply {
                    repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    volume = 0f
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    playWhenReady = true
                    setPlaybackSpeed(speed)
                }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (player == null) player = build()
                Lifecycle.Event.ON_STOP -> {
                    player?.let { runCatching { it.release() } }
                    player = null
                }
                else -> {}
            }
        }
        if (player == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player = build()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.let { runCatching { it.release() } }
            player = null
        }
    }

    LaunchedEffect(player == null) { if (player == null) alpha.snapTo(0f) }
    LaunchedEffect(player, speed) { player?.setPlaybackSpeed(speed) }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrameGen++ }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.w("WallpaperView", "wallpaper play error ${error.errorCodeName}: $uri")
                onError()
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    LaunchedEffect(player, uri) {
        val p = player ?: return@LaunchedEffect
        if (alpha.value > 0f) alpha.animateTo(0f, tween(450))
        val genBefore = firstFrameGen
        runCatching {
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
        }
        delay(2_500L)
        val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (firstFrameGen == genBefore && resumed) {
            Log.w("WallpaperView", "wallpaper stalled (no frame in 2.5s), skipping: $uri")
            onError()
        }
    }

    LaunchedEffect(firstFrameGen) {
        if (firstFrameGen > 0) alpha.animateTo(1f, tween(1100))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> android.view.SurfaceView(ctx) },
            update = { view -> player?.setVideoSurfaceView(view) },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = 1f - alpha.value }
                .background(coverBrush)
        )
    }
}

/** Decodes a bitmap with an inSampleSize so its width stays near [maxWidth]. */
fun decodeDownsampled(file: File, maxWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}
