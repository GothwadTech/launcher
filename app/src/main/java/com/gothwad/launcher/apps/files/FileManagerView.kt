package com.gothwad.launcher.apps.files

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.R
import com.gothwad.launcher.databinding.ItemFileBreadcrumbBinding
import com.gothwad.launcher.databinding.ItemFileEntryBinding
import com.gothwad.launcher.databinding.ItemFileEntryGridBinding
import com.gothwad.launcher.databinding.ViewFileManagerBinding
import com.gothwad.launcher.ui.AppIcons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File Item Model.
 */
data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeString: String,
    val dateString: String,
    val length: Long,
    val lastModified: Long
)

enum class SortMode {
    NAME_ASC, NAME_DESC, DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, TYPE_FIRST
}

/**
 * Native Android View File Manager (Desktop & Linux Dolphin/Nautilus style).
 * Features:
 * - Real drag-and-drop system for files and folders
 * - Interactive breadcrumb navigation bar
 * - In-folder live search / filter
 * - Grid View and Detailed List View toggle
 * - Multiple sorting options
 * - Linux Places Sidebar (Home, Downloads, Documents, Pictures, Music, Videos, DCIM, App Data, Root)
 * - Sidebar storage usage gauge
 * - Native zero Jetpack Compose overhead
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

    private val backStack = mutableListOf<File>()
    private val forwardStack = mutableListOf<File>()
    private val allItems = mutableListOf<FileItem>()

    private var isGridView = false
    private var currentSort = SortMode.TYPE_FIRST
    private var searchQuery = ""

    private val fileAdapter: FileExplorerAdapter

    init {
        setupIcons()
        setupSidebar()

        fileAdapter = FileExplorerAdapter(
            onItemClick = { item ->
                if (item.isDirectory) {
                    navigateTo(item.file)
                } else {
                    openFile(item.file)
                }
            },
            onDeleteClick = { item ->
                confirmDelete(item)
            },
            onItemLongClick = { view, item ->
                startFileDrag(view, item)
            },
            onItemDropped = { sourceFile, destFolder ->
                moveFileOrFolder(sourceFile, destFolder)
            }
        )

        updateLayoutManager()
        binding.recyclerFiles.adapter = fileAdapter

        setupToolbar()
        setupSearch()
        loadDirectory(currentDir)
    }

    private fun setupIcons() {
        val iconColor = 0xFFCCCCCC.toInt()
        binding.btnNavBack.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, iconColor))
        binding.btnNavForward.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, iconColor).apply {
            // Rotated or back icon
        })
        binding.btnNavForward.rotation = 180f
        binding.btnNavUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, iconColor))
        binding.btnNewFolder.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, iconColor))
        binding.btnRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, iconColor))
        binding.btnViewMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DASHBOARD, iconColor))
        binding.btnSort.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MOVE, iconColor))

        binding.imgPathIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))
        binding.imgSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xFF9AA0A6.toInt()))
        binding.btnClearSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xFF9AA0A6.toInt()))

        val placeColor = 0xFF9AA0A6.toInt()
        binding.imgPlaceInternal.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF4FA7FA.toInt()))
        binding.imgPlaceDownloads.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, 0xFF60A5FA.toInt()))
        binding.imgPlaceDocuments.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, placeColor))
        binding.imgPlacePictures.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, placeColor))
        binding.imgPlaceMusic.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MUSIC, placeColor))
        binding.imgPlaceMovies.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, placeColor))
        binding.imgPlaceDcim.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, placeColor))
        binding.imgPlaceAppStorage.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, placeColor))
        binding.imgPlaceRoot.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_STORAGE, placeColor))
    }

    private fun setupToolbar() {
        binding.btnNavBack.setOnClickListener {
            if (backStack.isNotEmpty()) {
                forwardStack.add(currentDir)
                val prev = backStack.removeAt(backStack.lastIndex)
                loadDirectory(prev, addToHistory = false)
            }
        }

        binding.btnNavForward.setOnClickListener {
            if (forwardStack.isNotEmpty()) {
                backStack.add(currentDir)
                val next = forwardStack.removeAt(forwardStack.lastIndex)
                loadDirectory(next, addToHistory = false)
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

        binding.btnViewMode.setOnClickListener {
            isGridView = !isGridView
            updateLayoutManager()
            binding.btnViewMode.setImageDrawable(
                AppIcons.createDrawable(
                    if (isGridView) AppIcons.PATH_CLEAR_ALL else AppIcons.PATH_DASHBOARD,
                    0xFFCCCCCC.toInt()
                )
            )
            binding.tvViewModeLabel.text = if (isGridView) "Grid View" else "Detailed List"
            fileAdapter.notifyDataSetChanged()
        }

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
    }

    private fun updateLayoutManager() {
        if (isGridView) {
            val density = context.resources.displayMetrics.density
            val widthDp = context.resources.displayMetrics.widthPixels / density
            val spanCount = if (widthDp > 700) 5 else if (widthDp > 450) 4 else 3
            binding.recyclerFiles.layoutManager = GridLayoutManager(context, spanCount)
        } else {
            binding.recyclerFiles.layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSearch() {
        binding.etSearchFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchFilter.text?.clear()
        }
    }

    private fun setupSidebar() {
        val homeDir = Environment.getExternalStorageDirectory() ?: context.filesDir
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val picsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val appStorageDir = context.filesDir
        val rootDir = File("/")

        binding.placeInternal.setOnClickListener { navigateTo(homeDir) }
        binding.placeDownloads.setOnClickListener { navigateTo(downloadsDir) }
        binding.placeDocuments.setOnClickListener { navigateTo(docsDir) }
        binding.placePictures.setOnClickListener { navigateTo(picsDir) }
        binding.placeMusic.setOnClickListener { navigateTo(musicDir) }
        binding.placeMovies.setOnClickListener { navigateTo(moviesDir) }
        binding.placeDcim.setOnClickListener { navigateTo(dcimDir) }
        binding.placeAppStorage.setOnClickListener { navigateTo(appStorageDir) }
        binding.placeRoot.setOnClickListener { navigateTo(rootDir) }

        // Setup drop targets for all sidebar places
        setupPlaceDropTarget(binding.placeInternal, homeDir)
        setupPlaceDropTarget(binding.placeDownloads, downloadsDir)
        setupPlaceDropTarget(binding.placeDocuments, docsDir)
        setupPlaceDropTarget(binding.placePictures, picsDir)
        setupPlaceDropTarget(binding.placeMusic, musicDir)
        setupPlaceDropTarget(binding.placeMovies, moviesDir)
        setupPlaceDropTarget(binding.placeDcim, dcimDir)
        setupPlaceDropTarget(binding.placeAppStorage, appStorageDir)
    }

    private fun setupPlaceDropTarget(placeView: View, targetDir: File) {
        placeView.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription?.hasMimeType("application/x-gothwad-file") == true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    v.setBackgroundResource(R.drawable.bg_file_drop_target)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    v.setBackgroundResource(R.drawable.bg_win_btn)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    v.setBackgroundResource(R.drawable.bg_win_btn)
                    val sourcePath = event.clipData?.getItemAt(0)?.text?.toString()
                    if (sourcePath != null) {
                        moveFileOrFolder(File(sourcePath), targetDir)
                    }
                    true
                }
                else -> true
            }
        }
    }

    fun getView(): View = binding.root

    fun navigateTo(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(context, "Cannot open folder", Toast.LENGTH_SHORT).show()
            return
        }
        forwardStack.clear()
        loadDirectory(dir, addToHistory = true)
    }

    fun refresh() {
        loadDirectory(currentDir, addToHistory = false)
    }

    private fun loadDirectory(dir: File, addToHistory: Boolean = false) {
        if (addToHistory && dir != currentDir) {
            backStack.add(currentDir)
        }
        currentDir = dir
        binding.btnNavBack.alpha = if (backStack.isNotEmpty()) 1.0f else 0.4f
        binding.btnNavForward.alpha = if (forwardStack.isNotEmpty()) 1.0f else 0.4f

        updateBreadcrumbs(dir)
        updateStorageMeter(dir)

        val files = runCatching { dir.listFiles() }.getOrNull()
        allItems.clear()

        if (files != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            for (file in files) {
                val isDir = file.isDirectory
                val sizeStr = if (isDir) {
                    val count = runCatching { file.list()?.size ?: 0 }.getOrDefault(0)
                    "$count items"
                } else {
                    formatFileSize(file.length())
                }
                allItems.add(
                    FileItem(
                        file = file,
                        name = file.name,
                        isDirectory = isDir,
                        sizeString = sizeStr,
                        dateString = dateFormat.format(Date(file.lastModified())),
                        length = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }

        applyFilterAndSort()
    }

    private fun updateBreadcrumbs(dir: File) {
        binding.layoutBreadcrumbsList.removeAllViews()
        val segments = mutableListOf<File>()
        var curr: File? = dir
        while (curr != null) {
            segments.add(0, curr)
            curr = curr.parentFile
        }

        for (i in segments.indices) {
            val segmentFile = segments[i]
            val isLast = (i == segments.lastIndex)
            val chipBinding = ItemFileBreadcrumbBinding.inflate(
                LayoutInflater.from(context),
                binding.layoutBreadcrumbsList,
                false
            )

            val name = if (i == 0) "Root" else segmentFile.name.ifEmpty { "Root" }
            chipBinding.tvBreadcrumbName.text = name
            if (isLast) {
                chipBinding.tvBreadcrumbName.setTextColor(0xFF4FA7FA.toInt())
                chipBinding.tvBreadcrumbSeparator.visibility = View.GONE
            } else {
                chipBinding.tvBreadcrumbName.setTextColor(0xFFE8EAED.toInt())
                chipBinding.tvBreadcrumbSeparator.visibility = View.VISIBLE
            }

            chipBinding.tvBreadcrumbName.setOnClickListener {
                if (!isLast) {
                    navigateTo(segmentFile)
                }
            }

            // Breadcrumb segments also accept drop!
            chipBinding.root.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("application/x-gothwad-file") == true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        chipBinding.tvBreadcrumbName.setBackgroundResource(R.drawable.bg_file_drop_target)
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        chipBinding.tvBreadcrumbName.setBackgroundResource(R.drawable.bg_win_btn)
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        chipBinding.tvBreadcrumbName.setBackgroundResource(R.drawable.bg_win_btn)
                        val sourcePath = event.clipData?.getItemAt(0)?.text?.toString()
                        if (sourcePath != null) {
                            moveFileOrFolder(File(sourcePath), segmentFile)
                        }
                        true
                    }
                    else -> true
                }
            }

            binding.layoutBreadcrumbsList.addView(chipBinding.root)
        }

        // Scroll to end of breadcrumbs
        binding.scrollBreadcrumbs.post {
            binding.scrollBreadcrumbs.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun applyFilterAndSort() {
        var filtered = if (searchQuery.isEmpty()) {
            allItems.toList()
        } else {
            allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        filtered = when (currentSort) {
            SortMode.TYPE_FIRST -> filtered.sortedWith(
                compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.lastModified }
            SortMode.DATE_ASC -> filtered.sortedBy { it.lastModified }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.length }
            SortMode.SIZE_ASC -> filtered.sortedBy { it.length }
        }

        fileAdapter.setItems(filtered)

        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmptyState.text = if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\"" else "This folder is empty"
        binding.tvStatusInfo.text = "${filtered.size} items • ${getStorageInfo(currentDir)}"
    }

    private fun updateStorageMeter(dir: File) {
        runCatching {
            val totalBytes = dir.totalSpace
            val freeBytes = dir.freeSpace
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0)
            if (totalBytes > 0) {
                val pct = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                binding.progressStorageMeter.progress = pct
                val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                binding.tvStorageCapacity.text = String.format(Locale.US, "Free: %.1f GB / %.1f GB", freeGb, totalGb)
            }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "Folders First (Default)",
            "Name (A to Z)",
            "Name (Z to A)",
            "Date Modified (Newest)",
            "Date Modified (Oldest)",
            "Size (Largest first)",
            "Size (Smallest first)"
        )
        val modes = arrayOf(
            SortMode.TYPE_FIRST,
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.DATE_DESC,
            SortMode.DATE_ASC,
            SortMode.SIZE_DESC,
            SortMode.SIZE_ASC
        )
        val selectedIdx = modes.indexOf(currentSort).coerceAtLeast(0)

        AlertDialog.Builder(context)
            .setTitle("Sort Files By")
            .setSingleChoiceItems(options, selectedIdx) { dialog, which ->
                currentSort = modes[which]
                applyFilterAndSort()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startFileDrag(view: View, item: FileItem) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val fileUri = Uri.fromFile(item.file)
        val clipItem = ClipData.Item(fileUri)
        val ext = item.file.extension.lowercase()
        val mimeType = runCatching {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }.getOrNull() ?: "*/*"

        val mimeTypes = arrayOf(
            "application/x-gothwad-file",
            ClipDescription.MIMETYPE_TEXT_PLAIN,
            ClipDescription.MIMETYPE_TEXT_URILIST,
            mimeType
        )
        val clipData = ClipData("FILE_DRAG", mimeTypes, clipItem).apply {
            addItem(ClipData.Item(item.file.absolutePath))
        }
        val shadow = View.DragShadowBuilder(view)
        binding.tvDragDropHint.visibility = View.VISIBLE
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clipData, shadow, item, flags)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(clipData, shadow, item, flags)
        }
    }

    fun moveFileOrFolder(source: File, targetDir: File) {
        binding.tvDragDropHint.visibility = View.GONE
        if (!source.exists() || !targetDir.exists() || !targetDir.isDirectory) return

        if (source.parentFile?.canonicalPath == targetDir.canonicalPath) {
            Toast.makeText(context, "'${source.name}' is already in '${targetDir.name}'", Toast.LENGTH_SHORT).show()
            return
        }

        if (source.isDirectory && targetDir.canonicalPath.startsWith(source.canonicalPath)) {
            Toast.makeText(context, "Cannot move a folder into its own subfolder", Toast.LENGTH_SHORT).show()
            return
        }

        var destFile = File(targetDir, source.name)
        if (destFile.exists()) {
            destFile = File(targetDir, "Copy_${System.currentTimeMillis() % 1000}_${source.name}")
        }

        val success = if (source.renameTo(destFile)) {
            true
        } else {
            runCatching {
                if (source.isDirectory) {
                    source.copyRecursively(destFile, overwrite = true)
                    source.deleteRecursively()
                } else {
                    source.copyTo(destFile, overwrite = true)
                    source.delete()
                }
            }.isSuccess
        }

        if (success) {
            Toast.makeText(context, "Moved '${source.name}' to '${targetDir.name}'", Toast.LENGTH_SHORT).show()
            refresh()
        } else {
            Toast.makeText(context, "Could not move '${source.name}'", Toast.LENGTH_SHORT).show()
        }
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
     * Adapter handling both List and Grid views with Drag and Drop.
     */
    inner class FileExplorerAdapter(
        private val onItemClick: (FileItem) -> Unit,
        private val onDeleteClick: (FileItem) -> Unit,
        private val onItemLongClick: (View, FileItem) -> Unit,
        private val onItemDropped: (File, File) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<FileItem>()

        fun setItems(list: List<FileItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (isGridView) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                val b = ItemFileEntryGridBinding.inflate(inflater, parent, false)
                GridViewHolder(b)
            } else {
                val b = ItemFileEntryBinding.inflate(inflater, parent, false)
                ListViewHolder(b)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is ListViewHolder) {
                holder.bind(item)
            } else if (holder is GridViewHolder) {
                holder.bind(item)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ListViewHolder(private val b: ItemFileEntryBinding) :
            RecyclerView.ViewHolder(b.root) {

            @SuppressLint("ClickableViewAccessibility")
            fun bind(item: FileItem) {
                b.tvFileName.text = item.name
                b.tvFileDetails.text = "${if (item.isDirectory) "Folder" else item.sizeString} • ${item.dateString}"

                val iconPath = getFileIconPath(item)
                val iconColor = if (item.isDirectory) 0xFF4FA7FA.toInt() else Color.WHITE
                b.imgFileIcon.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor))
                b.btnFileDelete.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFE05252.toInt()))

                b.root.setOnClickListener { onItemClick(item) }
                b.btnFileDelete.setOnClickListener { onDeleteClick(item) }

                // Drag source
                b.root.setOnLongClickListener {
                    onItemLongClick(b.root, item)
                    true
                }

                // Drop target if directory
                if (item.isDirectory) {
                    b.root.setOnDragListener { v, event ->
                        when (event.action) {
                            DragEvent.ACTION_DRAG_STARTED -> {
                                event.clipDescription?.hasMimeType("application/x-gothwad-file") == true
                            }
                            DragEvent.ACTION_DRAG_ENTERED -> {
                                v.setBackgroundResource(R.drawable.bg_file_drop_target)
                                true
                            }
                            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                                v.setBackgroundResource(R.drawable.bg_win_btn)
                                true
                            }
                            DragEvent.ACTION_DROP -> {
                                v.setBackgroundResource(R.drawable.bg_win_btn)
                                val sourcePath = event.clipData?.getItemAt(0)?.text?.toString()
                                if (sourcePath != null) {
                                    onItemDropped(File(sourcePath), item.file)
                                }
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    b.root.setOnDragListener(null)
                }
            }
        }

        inner class GridViewHolder(private val b: ItemFileEntryGridBinding) :
            RecyclerView.ViewHolder(b.root) {

            @SuppressLint("ClickableViewAccessibility")
            fun bind(item: FileItem) {
                b.tvFileGridName.text = item.name
                b.tvFileGridInfo.text = if (item.isDirectory) item.sizeString else item.sizeString

                val iconPath = getFileIconPath(item)
                val iconColor = if (item.isDirectory) 0xFF4FA7FA.toInt() else Color.WHITE
                b.imgFileGridIcon.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor, 28f, 28f))

                b.root.setOnClickListener { onItemClick(item) }

                // Drag source
                b.root.setOnLongClickListener {
                    onItemLongClick(b.root, item)
                    true
                }

                // Drop target if directory
                if (item.isDirectory) {
                    b.root.setOnDragListener { v, event ->
                        when (event.action) {
                            DragEvent.ACTION_DRAG_STARTED -> {
                                event.clipDescription?.hasMimeType("application/x-gothwad-file") == true
                            }
                            DragEvent.ACTION_DRAG_ENTERED -> {
                                v.setBackgroundResource(R.drawable.bg_file_drop_target)
                                true
                            }
                            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                                v.setBackgroundResource(R.drawable.bg_win_btn)
                                true
                            }
                            DragEvent.ACTION_DROP -> {
                                v.setBackgroundResource(R.drawable.bg_win_btn)
                                val sourcePath = event.clipData?.getItemAt(0)?.text?.toString()
                                if (sourcePath != null) {
                                    onItemDropped(File(sourcePath), item.file)
                                }
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    b.root.setOnDragListener(null)
                }
            }
        }

        private fun getFileIconPath(item: FileItem): String {
            if (item.isDirectory) return AppIcons.PATH_FOLDER
            val name = item.name.lowercase()
            return when {
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".webp") || name.endsWith(".gif") -> AppIcons.PATH_IMAGE
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                    name.endsWith(".avi") -> AppIcons.PATH_PLAY
                name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") ||
                    name.endsWith(".flac") -> AppIcons.PATH_MUSIC
                name.endsWith(".apk") -> AppIcons.PATH_APPS
                name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".doc") ||
                    name.endsWith(".docx") -> AppIcons.PATH_PENCIL
                else -> AppIcons.PATH_STORAGE
            }
        }
    }
}
