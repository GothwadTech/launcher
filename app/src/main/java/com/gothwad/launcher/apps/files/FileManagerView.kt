package com.gothwad.launcher.apps.files

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemFileEntryBinding
import com.gothwad.launcher.databinding.ViewFileManagerBinding
import com.gothwad.launcher.ui.AppIcons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File Entry Model.
 */
data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeString: String,
    val dateString: String
)

/**
 * Native Android View File Manager.
 * Features:
 * - Places sidebar (Internal, Downloads, Documents, Pictures, Camera, App Data)
 * - Breadcrumb navigation bar (Back, Up, current path)
 * - Folder creation & file/folder deletion
 * - Open files via system intents / FileProvider
 * - Zero Jetpack Compose overhead, pure Native Views.
 */
class FileManagerView(
    private val context: Context,
    initialDirectory: File? = null
) {
    val binding: ViewFileManagerBinding = ViewFileManagerBinding.inflate(
        LayoutInflater.from(context)
    )

    private var currentDir: File = initialDirectory?.takeIf { it.exists() && it.isDirectory }
        ?: Environment.getExternalStorageDirectory()
        ?: context.filesDir

    private val historyStack = mutableListOf<File>()
    private val fileAdapter: FileListAdapter

    init {
        // Setup icons using AppIcons
        binding.btnNavBack.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE))
        binding.btnNavUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, Color.WHITE))
        binding.btnNewFolder.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))
        binding.btnRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE))
        binding.imgPathIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))

        val placeIconColor = 0xFF9AA0A6.toInt()
        binding.imgPlaceInternal.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_STORAGE, placeIconColor))
        binding.imgPlaceDownloads.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, placeIconColor))
        binding.imgPlaceDocuments.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, placeIconColor))
        binding.imgPlacePictures.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, placeIconColor))
        binding.imgPlaceDcim.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, placeIconColor))
        binding.imgPlaceAppStorage.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, placeIconColor))

        // Recycler setup
        fileAdapter = FileListAdapter(
            onItemClick = { item ->
                if (item.isDirectory) {
                    navigateTo(item.file)
                } else {
                    openFile(item.file)
                }
            },
            onDeleteClick = { item ->
                confirmDelete(item)
            }
        )
        binding.recyclerFiles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = fileAdapter
            setHasFixedSize(true)
        }

        // Toolbar actions
        binding.btnNavBack.setOnClickListener {
            if (historyStack.isNotEmpty()) {
                val prev = historyStack.removeAt(historyStack.lastIndex)
                if (prev.exists() && prev.isDirectory) {
                    loadDirectory(prev)
                }
            }
        }

        binding.btnNavUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                navigateTo(parent)
            } else {
                Toast.makeText(context, "Cannot go higher", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNewFolder.setOnClickListener {
            showNewFolderDialog()
        }

        binding.btnRefresh.setOnClickListener {
            refresh()
        }

        // Places Sidebar clicks
        binding.placeInternal.setOnClickListener {
            navigateTo(Environment.getExternalStorageDirectory())
        }
        binding.placeDownloads.setOnClickListener {
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        }
        binding.placeDocuments.setOnClickListener {
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        }
        binding.placePictures.setOnClickListener {
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        }
        binding.placeDcim.setOnClickListener {
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        }
        binding.placeAppStorage.setOnClickListener {
            navigateTo(context.filesDir)
        }

        // Initial Load
        loadDirectory(currentDir)
    }

    fun getView(): View = binding.root

    fun navigateTo(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(context, "Cannot open folder", Toast.LENGTH_SHORT).show()
            return
        }
        if (dir != currentDir) {
            historyStack.add(currentDir)
        }
        loadDirectory(dir)
    }

    fun refresh() {
        loadDirectory(currentDir)
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        binding.tvCurrentPath.text = dir.absolutePath

        val files = runCatching { dir.listFiles() }.getOrNull()
        if (files == null || files.isEmpty()) {
            fileAdapter.setItems(emptyList())
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvStatusInfo.text = "0 items • ${getStorageInfo(dir)}"
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val items = files.map { file ->
            val isDir = file.isDirectory
            val sizeStr = if (isDir) {
                val childCount = runCatching { file.list()?.size ?: 0 }.getOrDefault(0)
                "$childCount items"
            } else {
                formatFileSize(file.length())
            }
            val dateStr = dateFormat.format(Date(file.lastModified()))

            FileItem(
                file = file,
                name = file.name,
                isDirectory = isDir,
                sizeString = sizeStr,
                dateString = dateStr
            )
        }.sortedWith(
            compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )

        fileAdapter.setItems(items)
        binding.tvStatusInfo.text = "${items.size} items • ${getStorageInfo(dir)}"
    }

    private fun showNewFolderDialog() {
        val input = EditText(context).apply {
            hint = "Folder Name"
            setTextColor(Color.WHITE)
            setHintTextColor(0x80FFFFFF.toInt())
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(context)
            .setTitle("Create New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newDir = File(currentDir, name)
                    if (newDir.exists()) {
                        Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
                    } else if (newDir.mkdirs()) {
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                        refresh()
                    } else {
                        Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: FileItem) {
        val type = if (item.isDirectory) "folder" else "file"
        AlertDialog.Builder(context)
            .setTitle("Delete $type?")
            .setMessage("Are you sure you want to delete '${item.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                val deleted = if (item.isDirectory) item.file.deleteRecursively() else item.file.delete()
                if (deleted) {
                    Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                    refresh()
                } else {
                    Toast.makeText(context, "Could not delete ${item.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFile(file: File) {
        try {
            val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrElse {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun getStorageInfo(dir: File): String {
        return runCatching {
            val freeBytes = dir.freeSpace
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "Free: %.1f GB", freeGb)
        }.getOrDefault("")
    }

    /**
     * File items list adapter
     */
    inner class FileListAdapter(
        private val onItemClick: (FileItem) -> Unit,
        private val onDeleteClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

        private val items = mutableListOf<FileItem>()

        fun setItems(list: List<FileItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val itemBinding = ItemFileEntryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return FileViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class FileViewHolder(private val itemBinding: ItemFileEntryBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: FileItem) {
                itemBinding.tvFileName.text = item.name
                itemBinding.tvFileDetails.text = "${if (item.isDirectory) "Folder" else item.sizeString} • ${item.dateString}"

                // Icon selection
                val iconPath = if (item.isDirectory) {
                    AppIcons.PATH_FOLDER
                } else {
                    val name = item.name.lowercase()
                    when {
                        name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp") -> AppIcons.PATH_IMAGE
                        name.endsWith(".mp4") || name.endsWith(".mkv") -> AppIcons.PATH_PLAY
                        name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") -> AppIcons.PATH_MUSIC
                        name.endsWith(".apk") -> AppIcons.PATH_APPS
                        else -> AppIcons.PATH_STORAGE
                    }
                }
                val iconColor = if (item.isDirectory) 0xFF4FA7FA.toInt() else Color.WHITE
                itemBinding.imgFileIcon.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor))
                itemBinding.btnFileDelete.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFE05252.toInt()))

                itemBinding.root.setOnClickListener {
                    onItemClick(item)
                }

                itemBinding.btnFileDelete.setOnClickListener {
                    onDeleteClick(item)
                }
            }
        }
    }
}
