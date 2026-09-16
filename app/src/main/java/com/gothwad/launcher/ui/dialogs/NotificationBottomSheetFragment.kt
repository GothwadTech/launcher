package com.gothwad.launcher.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.R
import com.gothwad.launcher.databinding.SheetNotificationsBinding
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationBottomSheetFragment : DialogFragment() {

    private var _binding: SheetNotificationsBinding? = null
    private val binding get() = _binding!!

    private var adapter: NotificationAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_LiteTV_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.START)
            window.setWindowAnimations(R.style.Animation_LeftSidebar)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgSheetBell.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL_ACTIVE, 0xFF4C8DFF.toInt()))
        binding.btnCloseSheet.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgPermIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHIELD, 0xFFFFB300.toInt()))
        binding.imgEmptyBell.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, 0x4DFFFFFF.toInt()))

        binding.viewBackdrop.setOnClickListener {
            dismiss()
        }

        adapter = NotificationAdapter(
            onClick = { item ->
                dismiss()
                NotificationManagerBridge.launchNotification(requireContext(), item)
            },
            onDismiss = { item ->
                NotificationManagerBridge.dismissNotification(item.key)
            }
        )

        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotifications.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            NotificationManagerBridge.clearAll()
        }

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnEnablePerm.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            dismiss()
        }

        observeData()
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    private fun checkPermission() {
        val granted = NotificationManagerBridge.isNotificationAccessGranted(requireContext())
        binding.cardPermissionWarning.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationManagerBridge.notifications.collectLatest { list ->
                    val hasPerm = NotificationManagerBridge.isNotificationAccessGranted(requireContext())
                    binding.cardPermissionWarning.visibility = if (hasPerm) View.GONE else View.VISIBLE

                    binding.tvNotifHeading.text = if (list.isNotEmpty()) "Notifications (${list.size})" else "Notifications"
                    binding.btnClearAll.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE

                    if (list.isEmpty()) {
                        binding.recyclerNotifications.visibility = View.GONE
                        binding.layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.recyclerNotifications.visibility = View.VISIBLE
                        binding.layoutEmptyState.visibility = View.GONE
                        adapter?.submitList(list)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NotificationBottomSheet"

        fun newInstance(): NotificationBottomSheetFragment {
            return NotificationBottomSheetFragment()
        }
    }
}
