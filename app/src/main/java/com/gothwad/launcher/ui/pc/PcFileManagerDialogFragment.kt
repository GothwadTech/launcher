package com.gothwad.launcher.ui.pc

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.databinding.LayoutPcFileManagerBinding
import com.gothwad.launcher.databinding.LayoutPcAppContextMenuBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PC Level File Manager - Windows Explorer Style
 * Opens as dialog/window with full file operations
 */
class PcFileManagerDialogFragment : DialogFragment() {

    private var _binding: LayoutPcFileManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var fileAdapter: PcFileManagerAdapter
    private lateinit var sidebarAdapter: PcSidebarAdapter

    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()
    private var currentSort = PcFileSortBy.NAME
    private var ascending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutPcFileManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadStorages()
        navigateToInitialDir()
    }

    private fun setupUI() {
        // Titlebar icons
        binding.imgFmIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))
        binding.imgFmClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.btnViewMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
        binding.btnNewFolder.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))
        binding.btnNavBack.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE))
        binding.btnNavUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, Color.WHITE))
        binding.btnNavHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, Color.WHITE))
        binding.btnRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE))
        binding.imgEmptyIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, Color.WHITE))

        // Adapters
        fileAdapter = PcFileManagerAdapter(
            onFileClick = { item -> handleFileClick(item) },
            onFileLongClick = { item, v -> showFileContextMenu(item, v) }
        )

        sidebarAdapter = PcSidebarAdapter { storage ->
            navigateTo(storage.path)
        }

        binding.recyclerFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }

        binding.recyclerSidebar.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sidebarAdapter
        }

        // Click listeners
        binding.btnFmClose.setOnClickListener { dismiss() }

        binding.btnNavBack.setOnClickListener {
            if (backStack.isNotEmpty()) {
                val prev = backStack.removeAt(backStack.lastIndex)
                navigateTo(prev, addToBackStack = false)
            } else {
                currentDir?.parentFile?.let { navigateTo(it) }
            }
        }

        binding.btnNavUp.setOnClickListener {
            currentDir?.parentFile?.let { navigateTo(it) }
        }

        binding.btnNavHome.setOnClickListener {
            navigateToInitialDir()
        }

        binding.btnRefresh.setOnClickListener {
            currentDir?.let { loadFiles(it) }
        }

        binding.btnNewFolder.setOnClickListener {
            showNewFolderDialog()
        }

        binding.btnViewMode.setOnClickListener {
            // Toggle sort
            currentSort = when (currentSort) {
                PcFileSortBy.NAME -> PcFileSortBy.DATE
                PcFileSortBy.DATE -> PcFileSortBy.SIZE
                PcFileSortBy.SIZE -> PcFileSortBy.TYPE
                PcFileSortBy.TYPE -> PcFileSortBy.NAME
            }
            currentDir?.let { loadFiles(it) }
            Toast.makeText(requireContext(), "Sort: $currentSort", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStorages() {
        val storages = PcFileManager.getQuickAccess(requireContext())
        sidebarAdapter.submitList(storages)
    }

    private fun navigateToInitialDir() {
        val internal = android.os.Environment.getExternalStorageDirectory()
        if (internal.exists()) {
            navigateTo(internal)
        } else {
            val storages = PcFileManager.getStorages(requireContext())
            if (storages.isNotEmpty()) {
                navigateTo(storages[0].path)
            }
        }
    }

    private fun navigateTo(dir: File, addToBackStack: Boolean = true) {
        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(requireContext(), "Cannot access: ${dir.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }

        if (addToBackStack) {
            currentDir?.let { backStack.add(it) }
        }

        currentDir = dir
        binding.tvCurrentPath.text = dir.absolutePath
        sidebarAdapter.setSelectedPath(dir.absolutePath)

        // Update storage info
        val free = PcFileManager.getFreeSpace(dir)
        val total = PcFileManager.getTotalSpace(dir)
        if (total > 0) {
            val freeStr = PcFileItem.formatFileSize(free)
            val totalStr = PcFileItem.formatFileSize(total)
            binding.tvStorageInfo.text = "Free: $freeStr / $totalStr"
            binding.tvStatusFree.text = "Free: $freeStr"
        }

        loadFiles(dir)
    }

    private fun loadFiles(dir: File) {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                PcFileManager.listFiles(dir, showHidden = false, sortBy = currentSort, ascending = ascending)
            }

            fileAdapter.submitList(files)

            binding.layoutEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE

            binding.tvStatusCount.text = "${files.size} items"
            binding.tvStatusSelected.text = dir.absolutePath
        }
    }

    private fun handleFileClick(item: PcFileItem) {
        if (item.isDirectory) {
            navigateTo(item.file)
        } else {
            openFile(item.file)
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val mime = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Try to open, if fails show chooser
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val chooser = Intent.createChooser(intent, "Open with")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(chooser)
            }
        } catch (e: Exception) {
            // Fallback: try direct file:// (may fail on Android 7+)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(file), getMimeType(file))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Cannot open: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "html", "htm" -> "text/html"
            else -> "*/*"
        }
    }

    private fun showFileContextMenu(item: PcFileItem, anchor: View) {
        val context = requireContext()
        val bindingMenu = LayoutPcAppContextMenuBinding.inflate(LayoutInflater.from(context))

        bindingMenu.tvAppHeaderLabel.text = item.name
        bindingMenu.imgAppHeaderIcon.setImageDrawable(
            AppIcons.createDrawable(
                if (item.isDirectory) AppIcons.PATH_FOLDER else AppIcons.PATH_SAVE,
                if (item.isDirectory) 0xFF4FA7FA.toInt() else 0xFFCCCCCC.toInt()
            )
        )

        bindingMenu.imgActionOpen.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, 0xFF4FA7FA.toInt()))
        bindingMenu.tvActionPin.text = "Open"
        bindingMenu.imgActionPin.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, Color.WHITE))

        bindingMenu.imgActionRename.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, Color.WHITE))
        bindingMenu.imgActionMoveUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))
        bindingMenu.tvActionPin.text = if (item.isDirectory) "Open" else "Open"
        bindingMenu.imgActionMoveUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))

        // Customize menu items for file manager
        bindingMenu.tvActionPin.text = "Open"
        bindingMenu.itemAppPinTaskbar.findViewById<android.widget.TextView>(com.gothwad.launcher.R.id.tv_action_pin)?.text = "Open"

        // We'll reuse menu but with file operations
        val popup = PopupWindow(
            bindingMenu.root,
            (220 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        bindingMenu.itemAppOpen.setOnClickListener {
            popup.dismiss()
            handleFileClick(item)
        }

        bindingMenu.itemAppPinTaskbar.setOnClickListener {
            popup.dismiss()
            // Share file
            shareFile(item.file)
        }
        bindingMenu.tvActionPin.text = "Share"
        bindingMenu.imgActionPin.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))

        bindingMenu.itemAppRename.setOnClickListener {
            popup.dismiss()
            showRenameDialog(item)
        }

        bindingMenu.itemAppMoveUp.setOnClickListener {
            popup.dismiss()
            showDeleteConfirm(item)
        }
        bindingMenu.imgActionMoveUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFFF6B6B.toInt()))
        bindingMenu.itemAppMoveUp.findViewById<android.widget.TextView>(com.gothwad.launcher.R.id.tv_action_move_up)?.let {
            it.text = "Delete"
            it.setTextColor(0xFFFF6B6B.toInt())
        }

        bindingMenu.itemAppMoveDown.visibility = View.GONE
        bindingMenu.itemAppHide.visibility = View.GONE
        bindingMenu.itemAppInfo.setOnClickListener {
            popup.dismiss()
            showProperties(item)
        }
        bindingMenu.itemAppUninstall.visibility = View.GONE

        popup.showAsDropDown(anchor, 0, -anchor.height - 20)
    }

    private fun showNewFolderDialog() {
        val context = requireContext()
        val input = android.widget.EditText(context).apply {
            hint = "Folder name"
            setPadding(24, 16, 24, 16)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    currentDir?.let { dir ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val success = PcFileManager.createFolder(dir, name)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                                    loadFiles(dir)
                                } else {
                                    Toast.makeText(context, "Failed to create", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(item: PcFileItem) {
        val context = requireContext()
        val input = android.widget.EditText(context).apply {
            setText(item.name)
            setPadding(24, 16, 24, 16)
            selectAll()
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val success = PcFileManager.renameFile(item.file, newName)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                                currentDir?.let { loadFiles(it) }
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(item: PcFileItem) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage("Delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = PcFileManager.deleteFile(item.file)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                            currentDir?.let { loadFiles(it) }
                        } else {
                            Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProperties(item: PcFileItem) {
        val sizeStr = item.getFormattedSize()
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH).format(Date(item.lastModified))
        val msg = """
            Name: ${item.name}
            Path: ${item.file.absolutePath}
            Size: $sizeStr
            Modified: $dateStr
            Readable: ${item.canRead}
            Writable: ${item.canWrite}
            Hidden: ${item.isHidden}
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Properties")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PcFileManagerDialog"
        fun newInstance() = PcFileManagerDialogFragment()
    }
}
