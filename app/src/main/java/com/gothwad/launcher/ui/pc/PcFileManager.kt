package com.gothwad.launcher.ui.pc

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * PC Level File Manager Core - Windows Explorer style operations
 * Handles navigation, file operations, storage detection
 */
object PcFileManager {

    data class StorageInfo(
        val name: String,
        val path: File,
        val isPrimary: Boolean = false,
        val isRemovable: Boolean = false
    )

    /**
     * Get all available storages (internal + SD cards + USB)
     */
    fun getStorages(context: Context): List<StorageInfo> {
        val storages = mutableListOf<StorageInfo>()

        // Primary internal storage
        val internal = Environment.getExternalStorageDirectory()
        if (internal.exists()) {
            storages.add(StorageInfo("Internal Storage", internal, isPrimary = true))
        }

        // Additional storages via getExternalFilesDirs
        try {
            val externalFilesDirs = context.getExternalFilesDirs(null)
            externalFilesDirs.forEach { file ->
                if (file != null) {
                    // Get root of this storage
                    // Path like /storage/XXXX-XXXX/Android/data/pkg/files -> /storage/XXXX-XXXX
                    val rootPath = file.absolutePath.substringBefore("/Android/")
                    val rootFile = File(rootPath)
                    if (rootFile.exists() && rootFile.canRead() && storages.none { it.path.absolutePath == rootFile.absolutePath }) {
                        val isRemovable = Environment.isExternalStorageRemovable(file)
                        val name = if (isRemovable) {
                            // Try to get volume name or use SD Card / USB
                            if (rootPath.contains("emulated")) "Internal Storage"
                            else {
                                // Check if USB
                                val lower = rootPath.lowercase()
                                if (lower.contains("usb")) "USB Drive" else "SD Card"
                            }
                        } else {
                            "Internal Storage"
                        }
                        if (rootFile.absolutePath != internal.absolutePath) {
                            storages.add(StorageInfo(name, rootFile, isRemovable = isRemovable))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback common paths
        val commonPaths = listOf(
            "/storage" to "Storage",
            "/mnt/media_rw" to "Media",
            "/sdcard" to "SD Card"
        )
        
        // Deduplicate and filter existing
        return storages.distinctBy { it.path.absolutePath }.filter { it.path.exists() }.ifEmpty {
            // Fallback to internal at least
            listOf(StorageInfo("Internal Storage", Environment.getExternalStorageDirectory(), true))
        }
    }

    fun getQuickAccess(context: Context): List<StorageInfo> {
        val quick = mutableListOf<StorageInfo>()
        val internal = Environment.getExternalStorageDirectory()
        
        // Standard Android folders
        val folders = mapOf(
            "Downloads" to File(internal, "Download"),
            "Pictures" to File(internal, "Pictures"),
            "Movies" to File(internal, "Movies"),
            "Music" to File(internal, "Music"),
            "Documents" to File(internal, "Documents"),
            "DCIM" to File(internal, "DCIM")
        )
        
        folders.forEach { (name, file) ->
            if (file.exists()) {
                quick.add(StorageInfo(name, file))
            }
        }
        
        // Add storages
        quick.addAll(0, getStorages(context))
        
        return quick
    }

    fun listFiles(dir: File, showHidden: Boolean = false, sortBy: PcFileSortBy = PcFileSortBy.NAME, ascending: Boolean = true): List<PcFileItem> {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
        
        val files = dir.listFiles() ?: return emptyList()
        
        var items = files.map { PcFileItem(it) }
        
        if (!showHidden) {
            items = items.filter { !it.isHidden && !it.name.startsWith(".") }
        }
        
        items = when (sortBy) {
            PcFileSortBy.NAME -> items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            PcFileSortBy.SIZE -> items.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            PcFileSortBy.DATE -> items.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            PcFileSortBy.TYPE -> items.sortedWith(compareBy({ !it.isDirectory }, { it.extension }, { it.name.lowercase() }))
        }
        
        if (!ascending) items = items.reversed()
        
        return items
    }

    fun createFolder(parent: File, name: String): Boolean {
        return try {
            val newFolder = File(parent, name)
            if (newFolder.exists()) false else newFolder.mkdirs()
        } catch (_: Exception) { false }
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteFile(it) }
            }
            file.delete()
        } catch (_: Exception) { false }
    }

    fun renameFile(file: File, newName: String): Boolean {
        return try {
            val newFile = File(file.parentFile, newName)
            if (newFile.exists()) false else file.renameTo(newFile)
        } catch (_: Exception) { false }
    }

    fun copyFile(src: File, destDir: File): Boolean {
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, src.name)
            if (src.isDirectory) {
                dest.mkdirs()
                src.listFiles()?.forEach { copyFile(it, dest) }
                true
            } else {
                src.copyTo(dest, overwrite = true)
                true
            }
        } catch (_: Exception) { false }
    }

    fun getParent(file: File): File? {
        return file.parentFile
    }

    fun getFreeSpace(file: File): Long {
        return try { file.freeSpace } catch (_: Exception) { 0L }
    }

    fun getTotalSpace(file: File): Long {
        return try { file.totalSpace } catch (_: Exception) { 0L }
    }

    fun isRootDir(file: File, context: Context): Boolean {
        val storages = getStorages(context)
        return storages.any { it.path.absolutePath == file.absolutePath } || file.parentFile == null
    }
}
