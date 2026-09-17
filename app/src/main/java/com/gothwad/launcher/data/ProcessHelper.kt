package com.gothwad.launcher.data

import android.app.Application
import android.os.Build

object ProcessHelper {
    fun currentProcessName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                val pid = android.os.Process.myPid()
                val cmdline = java.io.File("/proc/$pid/cmdline")
                if (cmdline.exists()) {
                    cmdline.readText().trim().replace("\u0000", "")
                } else ""
            }.getOrDefault("")
        }
    }
}
