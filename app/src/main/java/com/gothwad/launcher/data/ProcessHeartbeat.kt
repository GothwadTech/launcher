package com.gothwad.launcher.data

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Cheap, dependency-free liveness heartbeat shared between the main process and the
 * isolated `:watchdog` process.
 *
 * Why this exists: the previous watchdog implementation treated
 * `ActivityManager.getRunningAppProcesses()` returning `null` as "the launcher process
 * is dead" and re-launched the HOME intent every 10 seconds. On restricted OEM
 * firmware (Jio/Airtel STBs and several Google TV builds) that API legitimately
 * returns `null`/an incomplete list for third-party apps, which made the launcher
 * steal the screen away from every other app every 10 seconds.
 *
 * Instead we publish two monotonically increasing timestamps to a tiny file in
 * device-protected storage (visible to both processes, same UID, and readable before the
 * user unlocks the device - see [sharedDir]):
 *
 *  - `process` beat: written by the main process application object, every few seconds,
 *    regardless of UI state. If this goes stale, the process is genuinely dead/hung.
 *  - `foreground` beat: written by MainActivity while it is STARTED. Used only for
 *    diagnostics - a stale foreground beat while an app (Netflix, a game, ...) is in
 *    front is completely normal for a HOME app and must NOT trigger a relaunch.
 *
 * Both values use [SystemClock.elapsedRealtime], so they are immune to wall-clock and
 * timezone changes.
 */
object ProcessHeartbeat {

    private const val TAG = "ProcessHeartbeat"
    private const val FILE_NAME = "gothwad_heartbeat"
    private const val POLICY_FILE_NAME = "gothwad_relaunch_policy"

    /** How often the main process publishes a beat. */
    const val BEAT_INTERVAL_MS = 5_000L

    /** A main-process beat older than this means the process is dead or hung. */
    const val STALE_PROCESS_MS = 45_000L

    /**
     * Directory for state shared between the main process and `:watchdog`.
     *
     * It has to be *device-protected* storage: the watchdog is `directBootAware` and can be
     * started from `LOCKED_BOOT_COMPLETED`, when credential-encrypted `filesDir` is not mounted
     * yet. Every read there throws, the throw is swallowed by `runCatching`, and the watchdog
     * concludes "no heartbeat = the main process is dead" - so it yanked the TV back to HOME
     * before the device was even unlocked.
     */
    internal fun sharedDir(context: Context): File = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext().filesDir
        } else {
            context.filesDir
        }
    }.getOrDefault(context.filesDir)

    /**
     * Writes [file] atomically: write to a sibling temp file, then `rename` over it.
     *
     * A plain `writeText` truncates the file first, so a reader that arrives between the
     * truncate and the write (the `:watchdog` polls every 15 s) sees an empty/partial file,
     * parses `0`, and `isMainProcessStale()` then says "the launcher is dead" - which yanks
     * the TV back to HOME mid-movie. `rename(2)` on the same filesystem is atomic, so
     * readers only ever see the old or the new content, never a torn one.
     */
    internal fun writeAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            // Rename failed (should not happen on same-dir internal storage): fall back to
            // a direct write rather than losing the update entirely.
            file.writeText(content)
            runCatching { tmp.delete() }
        }
    }

    /**
     * Single daemon thread that owns every shared-file write.
     *
     * [touch] is called from the main looper (the beat runnable and the status-bar clock
     * loop), and project rule #3 is "no blocking work on the main thread" - not even a
     * "few bytes" of file I/O, which is still a syscall pair that can jank on a loaded
     * eMMC. The single thread also serialises read-modify-write cycles against themselves.
     */
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gothwad-heartbeat").apply { isDaemon = true }
    }

    private var handler: Handler? = null

    private val beatRunnable = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            touch(ctx, foreground = false)
            handler?.postDelayed(this, BEAT_INTERVAL_MS)
        }
    }

    @Volatile
    private var appContext: Context? = null

    /** Starts the periodic main-process beat. Call from the main process only. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (handler != null) return
        val h = Handler(Looper.getMainLooper())
        handler = h
        touch(app, foreground = false)
        h.postDelayed(beatRunnable, BEAT_INTERVAL_MS)
        Log.i(TAG, "Heartbeat started (interval ${BEAT_INTERVAL_MS}ms)")
    }

    fun stop() {
        handler?.removeCallbacks(beatRunnable)
        handler = null
        Log.i(TAG, "Heartbeat stopped")
    }

    /**
     * Writes the current timestamp. Never throws and never runs on the caller's thread:
     * the write is queued on [ioExecutor] (a few bytes on internal storage).
     */
    fun touch(context: Context, foreground: Boolean) {
        runCatching {
            // Use the application context when available so a queued task never pins an
            // Activity. (Never called from attachBaseContext, so this is safe here.)
            val app = context.applicationContext ?: context
            ioExecutor.execute {
                runCatching {
                    val now = SystemClock.elapsedRealtime()
                    val file = File(sharedDir(app), FILE_NAME)
                    val previous = readPair(file)
                    // A foreground beat must never wipe the process beat (keep the last known one,
                    // and publish `now` if we never had one).
                    val processTs = if (foreground) previous.first.takeIf { it > 0L } ?: now else now
                    val fgTs = if (foreground) now else previous.second
                    writeAtomic(file, "$processTs\n$fgTs")
                }
            }
        }
    }

    /** Last main-process beat (elapsedRealtime), or 0 when unknown. */
    fun lastProcessBeat(context: Context): Long = readPair(File(sharedDir(context), FILE_NAME)).first

    /** Last foreground (MainActivity STARTED) beat, or 0 when unknown. */
    fun lastForegroundBeat(context: Context): Long = readPair(File(sharedDir(context), FILE_NAME)).second

    /**
     * Publishes whether the watchdog may pull the launcher to the foreground.
     *
     * Mirrors `LauncherConfig.launchOnBoot` (plus "are we the default home app?") into a file,
     * because the `:watchdog` process must not touch the Preferences DataStore: DataStore is
     * single-process by design and two processes on one file can corrupt it.
     */
    fun setRelaunchAllowed(context: Context, allowed: Boolean) {
        runCatching {
            val app = context.applicationContext ?: context
            val content = if (allowed) "1" else "0"
            ioExecutor.execute {
                runCatching { writeAtomic(File(sharedDir(app), POLICY_FILE_NAME), content) }
            }
        }
    }

    /**
     * Last published policy. Defaults to "allowed" when nothing has been written yet, so crash
     * recovery still works on a fresh install before the first config emission.
     */
    fun isRelaunchAllowed(context: Context): Boolean = runCatching {
        File(sharedDir(context), POLICY_FILE_NAME).readText().trim() != "0"
    }.getOrDefault(true)

    /** True when the main process looks dead or hung. Safe against missing/corrupt state. */
    fun isMainProcessStale(context: Context): Boolean {
        val last = lastProcessBeat(context)
        // No file at all (e.g. fresh install, or the process died before its first beat):
        // treat as stale so the watchdog can recover, but only after it has itself been
        // alive long enough to be sure - see LauncherWatchdogService.
        if (last <= 0L) return true
        return SystemClock.elapsedRealtime() - last > STALE_PROCESS_MS
    }

    private fun readPair(file: File): Pair<Long, Long> {
        return runCatching {
            if (!file.exists()) return 0L to 0L
            val lines = file.readLines()
            val p = lines.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
            val f = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
            p to f
        }.getOrDefault(0L to 0L)
    }
}

/**
 * Relaunch budget shared by the crash self-healer (main process) and the watchdog
 * (`:watchdog` process). Prevents the classic "launcher crashes on startup, gets
 * relaunched forever, HOME is unusable" loop: after [MAX_RELAUNCHES] attempts inside
 * [WINDOW_MS] the self-healing stops until the launcher stays alive long enough to be
 * declared healthy again.
 */
object SelfHealGuard {

    private const val TAG = "SelfHealGuard"
    private const val FILE_NAME = "gothwad_selfheal"

    /** Max automatic relaunches allowed inside one window before we back off. */
    private const val MAX_RELAUNCHES = 3
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** How long the launcher must stay alive before it counts as healthy again. */
    private const val HEALTHY_AFTER_MS = 20_000L

    // File-backed (not SharedPreferences) on purpose: the crash handler lives in the
    // main process and the watchdog in `:watchdog`, and SharedPreferences instances are
    // cached per process, so cross-process counters would silently drift apart.
    private fun file(context: Context) = File(ProcessHeartbeat.sharedDir(context), FILE_NAME)

    private fun read(context: Context): Triple<Int, Long, Boolean> {
        return runCatching {
            val lines = file(context).readLines()
            Triple(
                lines.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
                lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L,
                lines.getOrNull(2)?.trim()?.toBoolean() ?: false,
            )
        }.getOrDefault(Triple(0, 0L, false))
    }

    private fun write(context: Context, count: Int, windowStart: Long, safeMode: Boolean) {
        runCatching {
            // Atomic for the same reason as the heartbeat: the other process polls this
            // file and must never parse a torn half-write as "safe mode" or "budget spent".
            ProcessHeartbeat.writeAtomic(file(context), "$count\n$windowStart\n$safeMode")
        }
    }

    /**
     * @return true when a relaunch is allowed. Records the attempt when it is.
     */
    @Synchronized
    fun shouldRelaunch(context: Context): Boolean {
        val (storedCount, windowStart, _) = read(context)
        val now = System.currentTimeMillis()

        val (count, start) = if (now - windowStart > WINDOW_MS) {
            0 to now
        } else {
            storedCount to windowStart
        }

        if (count >= MAX_RELAUNCHES) {
            write(context, count, start, safeMode = true)
            Log.w(
                TAG,
                "Safe mode: $count relaunches within ${WINDOW_MS / 60000} min - pausing self-healing"
            )
            return false
        }

        write(context, count + 1, start, safeMode = false)
        Log.i(TAG, "Relaunch allowed (attempt ${count + 1}/$MAX_RELAUNCHES in this window)")
        return true
    }

    /**
     * Called by MainActivity once the launcher has stayed up long enough to be trusted.
     * Clears the relaunch budget so future (unrelated) crashes can still self-heal.
     */
    @Synchronized
    fun markHealthy(context: Context) {
        val (count, _, safeMode) = read(context)
        if (count == 0 && !safeMode) return
        write(context, 0, 0L, safeMode = false)
        Log.i(TAG, "Launcher stable - self-heal budget reset")
    }

    /** True when self-healing is currently paused because of a crash loop. */
    fun isInSafeMode(context: Context): Boolean = read(context).third

    /** How long the launcher has to stay up before [markHealthy] is called. */
    fun healthyDelayMs(): Long = HEALTHY_AFTER_MS
}
