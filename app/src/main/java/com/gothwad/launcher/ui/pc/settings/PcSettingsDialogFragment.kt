package com.gothwad.launcher.ui.pc.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.DialogPcSettingsBinding
import com.gothwad.launcher.ui.dialogs.PinSetupDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PcSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogPcSettingsBinding? = null
    private val binding get() = _binding!!

    private var initialTab: Int = PcSettingsConstants.TAB_SYSTEM
    private var onWallpaperChanged: (() -> Unit)? = null

    private lateinit var navAdapter: PcSettingsNavAdapter
    private var currentConfig: LauncherConfig = LauncherConfig()

    private var personalisationPage: PcSettingsPersonalisationPage? = null
    private var systemPage: PcSettingsSystemPage? = null
    private var otherTabs: PcSettingsOtherTabs? = null

    private var currentTabId: Int = PcSettingsConstants.TAB_SYSTEM
    private var isMaximized: Boolean = false

    private val pickCustomImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleCustomWallpaperUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialTab = arguments?.getInt(ARG_INITIAL_TAB, PcSettingsConstants.TAB_SYSTEM)
            ?: PcSettingsConstants.TAB_SYSTEM
        currentTabId = initialTab
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPcSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowControls()
        setupSearch()

        viewLifecycleOwner.lifecycleScope.launch {
            val store = ConfigStore(requireContext())
            currentConfig = store.flow.first()

            initPages()
            setupNavigation()
            showTab(currentTabId)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { win ->
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.90f).toInt().coerceAtLeast(600).coerceAtMost(1020)
            val height = (dm.heightPixels * 0.88f).toInt().coerceAtLeast(420).coerceAtMost(700)
            win.setLayout(width, height)
        }
    }

    private fun setupWindowControls() {
        binding.btnWinClose.setOnClickListener {
            dismiss()
        }
        binding.btnWinMinimize.setOnClickListener {
            dismiss()
        }
        binding.btnWinMaximize.setOnClickListener {
            dialog?.window?.let { win ->
                val dm = resources.displayMetrics
                if (!isMaximized) {
                    win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    isMaximized = true
                } else {
                    val width = (dm.widthPixels * 0.90f).toInt().coerceAtLeast(600).coerceAtMost(1020)
                    val height = (dm.heightPixels * 0.88f).toInt().coerceAtLeast(420).coerceAtMost(700)
                    win.setLayout(width, height)
                    isMaximized = false
                }
            }
        }
    }

    private fun initPages() {
        personalisationPage = PcSettingsPersonalisationPage(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            config = currentConfig,
            onWallpaperChanged = { onWallpaperChanged?.invoke() },
            onPickCustomPhoto = { pickCustomImageLauncher.launch("image/*") },
            onOpenLockSetup = { openLockSetupDialog() }
        )

        systemPage = PcSettingsSystemPage(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            config = currentConfig,
            onSwitchToTvMode = { dismiss() }
        )

        otherTabs = PcSettingsOtherTabs(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            config = currentConfig,
            onOpenLockSetup = { openLockSetupDialog() }
        )
    }

    private fun setupNavigation() {
        navAdapter = PcSettingsNavAdapter(
            items = PcSettingsConstants.NAV_ITEMS,
            selectedId = currentTabId
        ) { item ->
            showTab(item.id)
        }
        binding.recyclerSettingsNav.adapter = navAdapter
    }

    private fun setupSearch() {
        binding.etSearchSettings.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) {
                    PcSettingsConstants.NAV_ITEMS
                } else {
                    PcSettingsConstants.NAV_ITEMS.filter {
                        it.title.contains(query, ignoreCase = true)
                    }
                }
                navAdapter.updateItems(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showTab(tabId: Int) {
        currentTabId = tabId
        navAdapter.setSelectedId(tabId)
        binding.containerSettingsPage.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val pageView: View = when (tabId) {
            PcSettingsConstants.TAB_PERSONALISATION -> personalisationPage?.createView(inflater)
            PcSettingsConstants.TAB_SYSTEM -> systemPage?.createView(inflater)
            else -> otherTabs?.createPageView(inflater, tabId)
        } ?: return

        binding.containerSettingsPage.addView(pageView)
    }

    private fun handleCustomWallpaperUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val target = File(context.filesDir, "wallpaper_pc.jpg")
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }.getOrDefault(false)
            }

            if (copied) {
                ConfigStore(context).update {
                    it.copy(pcUseCustomWallpaper = true)
                }
                currentConfig = currentConfig.copy(pcUseCustomWallpaper = true)
                personalisationPage?.updateConfig(currentConfig)
                onWallpaperChanged?.invoke()
                Toast.makeText(context, "Desktop wallpaper applied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLockSetupDialog() {
        PinSetupDialogFragment.newInstance(
            onSaved = { newCred ->
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update { it.copy(deviceLock = newCred) }
                    currentConfig = currentConfig.copy(deviceLock = newCred)
                    Toast.makeText(requireContext(), "Lock PIN updated", Toast.LENGTH_SHORT).show()
                }
            }
        ).show(parentFragmentManager, PinSetupDialogFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PcSettingsDialogFragment"
        private const val ARG_INITIAL_TAB = "arg_initial_tab"

        fun newInstance(
            initialTab: Int = PcSettingsConstants.TAB_SYSTEM,
            onWallpaperChanged: (() -> Unit)? = null
        ): PcSettingsDialogFragment {
            return PcSettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INITIAL_TAB, initialTab)
                }
                this.onWallpaperChanged = onWallpaperChanged
            }
        }
    }
}
