package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.DialogSearchBinding
import com.gothwad.launcher.ui.AppIcons

class SearchDialogFragment : DialogFragment() {

    private var _binding: DialogSearchBinding? = null
    private val binding get() = _binding!!

    private var allApps: List<AppEntry> = emptyList()
    private var config: LauncherConfig = LauncherConfig()
    private var adapter: SearchAppAdapter? = null
    var onLaunchApp: ((AppEntry) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xB3FFFFFF.toInt()))
        binding.btnClearSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.btnCloseSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgVaultLock.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFFFB300.toInt()))

        adapter = SearchAppAdapter { app ->
            onLaunchApp?.invoke(app)
            dismiss()
        }

        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSearchResults.adapter = adapter

        updateAppList("")

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }

        binding.btnCloseSearch.setOnClickListener {
            dismiss()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                updateAppList(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.requestFocus()
    }

    private fun updateAppList(query: String) {
        val trimmed = query.trim()
        val isSecretMatch = if (trimmed.isEmpty()) false else {
            val codeMatches = config.hideAppsCode.isNotEmpty() && trimmed.equals(config.hideAppsCode, ignoreCase = true)
            val pinMatches = config.hideAppsPin.isNotEmpty() && trimmed == config.hideAppsPin
            codeMatches || pinMatches
        }

        if (isSecretMatch) {
            binding.bannerSecretVault.visibility = View.VISIBLE
            binding.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFFFB300.toInt()))
            val hiddenApps = allApps.filter { it.pkg in config.hidden }
            adapter?.submitList(hiddenApps)
            binding.tvEmptyState.visibility = if (hiddenApps.isEmpty()) View.VISIBLE else View.GONE
            if (hiddenApps.isEmpty()) {
                binding.tvEmptyState.text = "No apps are currently hidden"
            }
        } else {
            binding.bannerSecretVault.visibility = View.GONE
            binding.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xB3FFFFFF.toInt()))
            val visibleApps = if (trimmed.isEmpty()) {
                allApps.filter { it.pkg !in config.hidden }
            } else {
                allApps.filter {
                    it.pkg !in config.hidden && (it.label.contains(trimmed, ignoreCase = true) || it.pkg.contains(trimmed, ignoreCase = true))
                }
            }
            adapter?.submitList(visibleApps)
            binding.tvEmptyState.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
            if (visibleApps.isEmpty()) {
                binding.tvEmptyState.text = "No apps found matching '$trimmed'"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SearchDialog"

        fun newInstance(
            apps: List<AppEntry>,
            config: LauncherConfig,
            onLaunch: (AppEntry) -> Unit
        ): SearchDialogFragment {
            return SearchDialogFragment().apply {
                this.allApps = apps
                this.config = config
                this.onLaunchApp = onLaunch
            }
        }
    }
}
