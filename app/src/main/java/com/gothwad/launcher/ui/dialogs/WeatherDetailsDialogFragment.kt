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
import com.gothwad.launcher.data.WeatherData
import com.gothwad.launcher.databinding.DialogWeatherDetailsBinding
import com.gothwad.launcher.ui.AppIcons

class WeatherDetailsDialogFragment : DialogFragment() {

    private var _binding: DialogWeatherDetailsBinding? = null
    private val binding get() = _binding!!

    var weatherData: WeatherData = WeatherData()
    var onRefresh: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWeatherDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgHeaderIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SUN, 0xFFFFB300.toInt()))
        binding.btnClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))

        binding.tvCity.text = weatherData.city
        binding.tvTemperature.text = weatherData.temp
        binding.tvCondition.text = weatherData.condition

        val iconPath = when {
            weatherData.weatherCode in 51..82 -> AppIcons.PATH_RAIN
            weatherData.weatherCode in 1..48 -> AppIcons.PATH_CLOUD
            else -> AppIcons.PATH_SUN
        }
        val iconColor = if (weatherData.weatherCode in 51..82) 0xFF64B5F6.toInt() else 0xFFFFD54F.toInt()
        binding.imgWeatherCondition.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor))

        if (weatherData.hasLocationPermission) {
            binding.tvLocationStatus.text = "GPS Active"
            binding.tvLocationStatus.setTextColor(0xFF81C784.toInt())
            binding.btnLocationSetup.visibility = View.GONE
        } else {
            binding.tvLocationStatus.text = "Default (New Delhi)"
            binding.tvLocationStatus.setTextColor(0xFFE0E0E0.toInt())
            binding.btnLocationSetup.visibility = View.VISIBLE
        }

        binding.tvTimeOfDay.text = if (weatherData.isDay) "Daytime" else "Night"

        binding.btnRefresh.setOnClickListener {
            onRefresh?.invoke()
            Actions.toast(requireContext(), "Refreshing weather…")
            dismiss()
        }

        binding.btnLocationSetup.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.onFailure {
                Actions.openSystemSettings(requireContext())
            }
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnRefresh.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WeatherDetailsDialog"

        fun newInstance(
            weather: WeatherData,
            onRefresh: () -> Unit
        ): WeatherDetailsDialogFragment {
            return WeatherDetailsDialogFragment().apply {
                this.weatherData = weather
                this.onRefresh = onRefresh
            }
        }
    }
}
