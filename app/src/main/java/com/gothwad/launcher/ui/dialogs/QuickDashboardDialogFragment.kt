package com.gothwad.launcher.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.TvControlHelper
import com.gothwad.launcher.databinding.DialogQuickDashboardBinding
import com.gothwad.launcher.ui.AppIcons

class QuickDashboardDialogFragment : DialogFragment() {

    private var _binding: DialogQuickDashboardBinding? = null
    private val binding get() = _binding!!

    var netStatus: NetStatus = NetStatus()
    var btStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    var onOpenSettings: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQuickDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (onOpenSettings == null) { // recreated after process death
            dismiss()
            return
        }

        binding.imgDashboardIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DASHBOARD, Color.WHITE))
        binding.btnClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))

        refreshAudioUi()

        binding.btnMute.setOnClickListener {
            TvControlHelper.toggleMute(requireContext())
            refreshAudioUi()
        }

        binding.btnVolDown.setOnClickListener {
            val vol = TvControlHelper.getVolume(requireContext())
            TvControlHelper.setVolume(requireContext(), (vol.first - 1).coerceAtLeast(0))
            refreshAudioUi()
        }

        binding.btnVolUp.setOnClickListener {
            val vol = TvControlHelper.getVolume(requireContext())
            TvControlHelper.setVolume(requireContext(), (vol.first + 1).coerceAtMost(vol.second))
            refreshAudioUi()
        }

        // Net status
        val isNetConnected = netStatus.connected
        val netIconPath = if (netStatus.wifi) AppIcons.PATH_WIFI else if (netStatus.ethernet) AppIcons.PATH_ETHERNET else AppIcons.PATH_WIFI_OFF
        val netColor = if (isNetConnected) 0xFF81C784.toInt() else 0xFFE57373.toInt()
        binding.imgNetIcon.setImageDrawable(AppIcons.createDrawable(netIconPath, netColor))
        binding.tvNetTitle.text = if (netStatus.wifi) "Wi-Fi" else if (netStatus.ethernet) "Ethernet" else "Offline"
        binding.tvNetSub.text = if (netStatus.ssid.isNotEmpty()) netStatus.ssid else if (isNetConnected) "Connected" else "Disconnected"

        binding.tileNetwork.setOnClickListener {
            Actions.openNetworkSettings(requireContext())
            dismiss()
        }

        // Home action
        binding.imgHomeIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF64B5F6.toInt()))
        binding.tvHomeTitle.text = "Home"
        binding.tvHomeSub.text = "Return Home"

        binding.tileHome.setOnClickListener {
            dismiss()
        }

        // Launcher settings
        binding.btnLauncherSettings.setOnClickListener {
            dismiss()
            onOpenSettings?.invoke()
        }

        // Android settings
        binding.btnAndroidSettings.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnMute.requestFocus()
    }

    private fun refreshAudioUi() {
        val vol = TvControlHelper.getVolume(requireContext())
        val isMuted = TvControlHelper.isMuted(requireContext())

        val muteIconPath = if (isMuted) AppIcons.PATH_MUTE else AppIcons.PATH_VOLUME
        val muteColor = if (isMuted) 0xFFFF5252.toInt() else Color.WHITE
        binding.btnMute.setImageDrawable(AppIcons.createDrawable(muteIconPath, muteColor))

        binding.tvVolumeLabel.text = if (isMuted) "Muted" else "Volume ${vol.first} / ${vol.second}"
        binding.progressVolume.max = if (vol.second > 0) vol.second else 100
        binding.progressVolume.progress = if (isMuted) 0 else vol.first
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QuickDashboardDialog"

        fun newInstance(
            net: NetStatus,
            bt: BluetoothDeviceStatus,
            onOpenSettings: () -> Unit
        ): QuickDashboardDialogFragment {
            return QuickDashboardDialogFragment().apply {
                this.netStatus = net
                this.btStatus = bt
                this.onOpenSettings = onOpenSettings
            }
        }
    }
}
