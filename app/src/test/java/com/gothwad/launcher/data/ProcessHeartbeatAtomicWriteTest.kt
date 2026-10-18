package com.gothwad.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Locks in the atomic-replace write used by every cross-process shared file
 * (`gothwad_heartbeat`, `gothwad_relaunch_policy`, `gothwad_selfheal`).
 *
 * The old `file.writeText(...)` truncated first, so the `:watchdog` polling mid-write could
 * read an empty file and conclude "the launcher is dead" - yanking the TV back to HOME.
 * A reader must only ever see the old or the new content, never a torn/empty one, and the
 * writer must not litter temp files.
 */
class ProcessHeartbeatAtomicWriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeAtomic_replacesExistingContent() {
        val file = File(tmp.root, "gothwad_heartbeat")
        ProcessHeartbeat.writeAtomic(file, "100\n200")
        assertEquals("100\n200", file.readText())

        ProcessHeartbeat.writeAtomic(file, "300\n400")
        assertEquals("300\n400", file.readText())
    }

    @Test
    fun writeAtomic_createsMissingFile() {
        val file = File(tmp.root, "gothwad_selfheal")
        ProcessHeartbeat.writeAtomic(file, "0\n0\nfalse")
        assertEquals("0\n0\nfalse", file.readText())
    }

    @Test
    fun writeAtomic_leavesNoTempFileBehind() {
        val file = File(tmp.root, "gothwad_relaunch_policy")
        ProcessHeartbeat.writeAtomic(file, "1")
        assertEquals("1", file.readText())
        assertFalse(File(tmp.root, "gothwad_relaunch_policy.tmp").exists())
    }
}
