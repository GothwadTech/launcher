package com.gothwad.launcher.ui.pc

import java.io.File

/**
 * File item for PC File Manager - Windows Explorer style
 */
data class PcFileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val isHidden: Boolean = file.isHidden,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val canRead: Boolean = file.canRead(),
    val canWrite: Boolean = file.canWrite(),
    val extension: String = if (file.isFile) file.extension.lowercase() else "",
    val childCount: Int = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0
) {
    val isImage: Boolean get() = extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isVideo: Boolean get() = extension in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
    val isAudio: Boolean get() = extension in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac")
    val isApk: Boolean get() = extension == "apk"
    val isZip: Boolean get() = extension in setOf("zip", "rar", "7z", "tar", "gz")
    val isDoc: Boolean get() = extension in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")

    fun getFormattedSize(): String {
        if (isDirectory) return "$childCount items"
        return formatFileSize(size)
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}

enum class PcFileSortBy {
    NAME, SIZE, DATE, TYPE
}

enum class PcFileViewMode {
    LIST, GRID
}
