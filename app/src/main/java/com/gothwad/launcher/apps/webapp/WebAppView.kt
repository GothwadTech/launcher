package com.gothwad.launcher.apps.webapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.gothwad.launcher.databinding.ViewWebAppBinding
import com.gothwad.launcher.ui.AppIcons

/**
 * Native Android View WebApp / PWA Container.
 * Features:
 * - Full desktop web engine with JavaScript, DOM storage, and hardware acceleration
 * - Dynamic URL bar, SSL status, Back/Forward/Refresh controls
 * - Page Title & Favicon auto-detection
 * - Save / Pin as Desktop Shortcut
 * - Zero Jetpack Compose overhead, 100% Native Views & ViewBinding.
 */
class WebAppView(
    private val context: Context,
    private val initialUrl: String = "https://www.google.com",
    private val onPinShortcut: ((title: String, url: String, icon: Bitmap?) -> Unit)? = null
) {
    val binding: ViewWebAppBinding = ViewWebAppBinding.inflate(
        LayoutInflater.from(context)
    )

    private var currentTitle: String = "Web App"
    private var currentFavicon: Bitmap? = null

    init {
        // Setup toolbar icons
        binding.btnWebBack.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE))
        binding.btnWebForward.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, Color.WHITE))
        binding.btnWebRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE))
        binding.btnWebGo.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, Color.WHITE))
        binding.btnPinToDesktop.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PIN, 0xFF4FA7FA.toInt()))
        binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4CAF50.toInt()))
        binding.imgWebError.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI_OFF, 0xFFE05252.toInt()))

        setupWebView()
        setupListeners()

        loadUrl(initialUrl)
    }

    fun getView(): View = binding.root

    fun getTitle(): String = currentTitle

    fun getFavicon(): Bitmap? = currentFavicon

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.webProgressBar.visibility = View.VISIBLE
                binding.layoutWebError.visibility = View.GONE
                url?.let {
                    binding.etWebUrl.setText(it)
                    updateSslIcon(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.webProgressBar.visibility = View.GONE
                url?.let { binding.etWebUrl.setText(it) }
                view?.title?.let { currentTitle = it }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.webProgressBar.visibility = View.GONE
                    binding.layoutWebError.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.webProgressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.webProgressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { currentTitle = it }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                currentFavicon = icon
            }
        }
    }

    private fun setupListeners() {
        binding.btnWebBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }

        binding.btnWebForward.setOnClickListener {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }

        binding.btnWebRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnWebGo.setOnClickListener {
            navigateFromInput()
        }

        binding.etWebUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateFromInput()
                true
            } else {
                false
            }
        }

        binding.btnPinToDesktop.setOnClickListener {
            val url = binding.webView.url ?: binding.etWebUrl.text.toString()
            onPinShortcut?.invoke(currentTitle, url, currentFavicon)
            Toast.makeText(context, "Pinned '$currentTitle' to Desktop", Toast.LENGTH_SHORT).show()
        }

        binding.btnWebRetry.setOnClickListener {
            binding.layoutWebError.visibility = View.GONE
            binding.webView.reload()
        }
    }

    private fun navigateFromInput() {
        var input = binding.etWebUrl.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) {
                "https://$input"
            } else {
                "https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8")
            }
        }
        loadUrl(input)
    }

    private fun updateSslIcon(url: String) {
        if (url.startsWith("https://")) {
            binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4CAF50.toInt()))
        } else {
            binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFE05252.toInt()))
        }
    }

    fun loadUrl(url: String) {
        binding.etWebUrl.setText(url)
        updateSslIcon(url)
        binding.webView.loadUrl(url)
    }

    fun destroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
    }
}
