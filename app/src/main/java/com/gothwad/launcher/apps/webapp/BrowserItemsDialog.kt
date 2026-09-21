package com.gothwad.launcher.apps.webapp

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.DialogBrowserItemsBinding
import com.gothwad.launcher.databinding.ItemBrowserEntryBinding
import com.gothwad.launcher.ui.AppIcons

class BrowserItemsDialog(
    private val itemType: String, // "BOOKMARK", "HISTORY", "DOWNLOAD"
    private val onSelectUrl: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogBrowserItemsBinding? = null
    private val binding get() = _binding!!
    private lateinit var dataStore: BrowserDataStore

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBrowserItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost((480 * resources.displayMetrics.density).toInt()),
            (resources.displayMetrics.heightPixels * 0.65f).toInt().coerceAtMost((420 * resources.displayMetrics.density).toInt())
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dataStore = BrowserDataStore(requireContext())

        val titleText = when (itemType) {
            "BOOKMARK" -> "Bookmarks"
            "HISTORY" -> "Browsing History"
            "DOWNLOAD" -> "Downloads"
            else -> "Saved Items"
        }
        val iconPath = when (itemType) {
            "BOOKMARK" -> AppIcons.PATH_BOOKMARK
            "HISTORY" -> AppIcons.PATH_HISTORY
            "DOWNLOAD" -> AppIcons.PATH_DOWNLOAD
            else -> AppIcons.PATH_GLOBE
        }

        binding.tvDialogTitle.text = titleText
        binding.imgDialogTypeIcon.setImageDrawable(AppIcons.createDrawable(iconPath, 0xFF4FA7FA.toInt()))
        binding.btnCloseItemsDialog.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))

        binding.btnCloseItemsDialog.setOnClickListener { dismiss() }

        binding.recyclerBrowserItems.layoutManager = LinearLayoutManager(requireContext())
        refreshList()

        binding.btnClearAllItems.setOnClickListener {
            dataStore.clearType(itemType)
            refreshList()
        }
    }

    private fun refreshList() {
        val items = dataStore.getAll().filter { it.type == itemType }
        if (items.isEmpty()) {
            binding.tvEmptyItems.visibility = View.VISIBLE
            binding.recyclerBrowserItems.visibility = View.GONE
        } else {
            binding.tvEmptyItems.visibility = View.GONE
            binding.recyclerBrowserItems.visibility = View.VISIBLE
            binding.recyclerBrowserItems.adapter = BrowserItemsAdapter(items)
        }
    }

    inner class BrowserItemsAdapter(
        private val list: List<BrowserEntry>
    ) : RecyclerView.Adapter<BrowserItemsAdapter.Holder>() {

        inner class Holder(val b: ItemBrowserEntryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemBrowserEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.b.tvEntryTitle.text = item.title.ifEmpty { item.url }
            holder.b.tvEntrySubtitle.text = item.url
            holder.b.imgEntryIcon.setImageDrawable(
                AppIcons.createDrawable(
                    if (itemType == "BOOKMARK") AppIcons.PATH_BOOKMARK else if (itemType == "DOWNLOAD") AppIcons.PATH_DOWNLOAD else AppIcons.PATH_HISTORY,
                    0xFF888888.toInt()
                )
            )
            holder.b.btnEntryDelete.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFE05252.toInt())
            )

            holder.b.root.setOnClickListener {
                onSelectUrl(item.url)
                dismiss()
            }

            holder.b.btnEntryDelete.setOnClickListener {
                dataStore.deleteEntry(item.id)
                refreshList()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
