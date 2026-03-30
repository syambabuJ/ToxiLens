package com.example.yttoxicitychecker.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yttoxicitychecker.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebViewBinding
    private var currentUrl: String = ""
    private var videoTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        val videoId = intent.getStringExtra("video_id") ?: ""
        val videoUrl = intent.getStringExtra("video_url") ?: "https://www.youtube.com/watch?v=$videoId"
        videoTitle = intent.getStringExtra("video_title") ?: "YouTube Video"

        // Setup toolbar
        setupToolbar()

        // Setup WebView
        setupWebView()

        // Load video
        loadVideo(videoId, videoUrl)

        // Setup listeners
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = videoTitle
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            defaultTextEncodingName = "utf-8"
            loadsImagesAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
        }

        binding.webView.webViewClient = CustomWebViewClient()
        binding.webView.webChromeClient = CustomWebChromeClient()
    }

    private fun loadVideo(videoId: String, videoUrl: String) {
        val embedUrl = if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
            // Extract video ID if full URL is provided
            val extractedId = extractVideoId(videoUrl)
            if (extractedId.isNotEmpty()) {
                "https://www.youtube.com/embed/$extractedId?autoplay=1&rel=0&modestbranding=1&showinfo=0"
            } else {
                "https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&showinfo=0"
            }
        } else {
            videoUrl
        }

        currentUrl = embedUrl
        binding.webView.loadUrl(embedUrl)
        showLoading(true)
    }

    private fun extractVideoId(url: String): String {
        val patterns = listOf(
            "v=([a-zA-Z0-9_-]{11})",
            "youtu.be/([a-zA-Z0-9_-]{11})",
            "embed/([a-zA-Z0-9_-]{11})",
            "shorts/([a-zA-Z0-9_-]{11})"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(url)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        return ""
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }

        binding.buttonRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.buttonShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$videoTitle\n$currentUrl")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Video"))
        }

        binding.buttonOpenBrowser.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
        showLoading(false)
    }

    private fun hideError() {
        binding.textError.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        // Clean up webview
        binding.webView.apply {
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            onPause()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    // Custom WebViewClient
    inner class CustomWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            showLoading(true)
            hideError()
            currentUrl = url ?: ""
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            showLoading(false)

            // Update back button state
            binding.buttonBack.isEnabled = true
            binding.buttonBack.text = if (view?.canGoBack() == true) "← Back" else "← Close"
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            url?.let {
                // Handle YouTube redirects and external links
                if (it.contains("youtube.com") || it.contains("youtu.be")) {
                    view?.loadUrl(it)
                    return true
                }
                // Handle other external links - open in browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    startActivity(browserIntent)
                    return true
                } catch (e: Exception) {
                    return false
                }
            }
            return super.shouldOverrideUrlLoading(view, url)
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            showLoading(false)
            showError("Failed to load video.\nError: ${description ?: "Unknown error"}\n\nPlease check your internet connection and try again.")
        }
    }

    // Custom WebChromeClient for handling video playback
    inner class CustomWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (newProgress >= 100) {
                showLoading(false)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            if (!title.isNullOrEmpty() && title != "about:blank" && title != "YouTube") {
                // Update toolbar title with video title if available
                if (videoTitle == "YouTube Video" && title != "YouTube") {
                    supportActionBar?.title = title
                }
            }
        }
    }
}