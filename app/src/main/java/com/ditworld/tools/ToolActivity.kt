package com.ditworld.tools

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class ToolActivity : Activity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        private const val HOST = "ditworld.com"

        // Nasconde header/footer/breadcrumb del sito: nell'app c'e' la barra nativa.
        private const val HIDE_CHROME_JS = """
            (function(){
              if (document.getElementById('app-hide')) return;
              var s = document.createElement('style');
              s.id = 'app-hide';
              s.textContent = '.hdr,.ft,.crumbs{display:none!important}';
              (document.head || document.documentElement).appendChild(s);
            })();
        """
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var startUrl: String
    private var failed = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "DIT Tools"
        startUrl = intent.getStringExtra(EXTRA_URL) ?: "https://$HOST/tools/"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
        }

        // --- Barra superiore nativa ---
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.panel))
            setPadding(dp(4), 0, dp(16), 0)
        }
        bar.addView(TextView(this).apply {
            text = "←"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(getColor(R.color.orange))
            gravity = Gravity.CENTER
            minWidth = dp(48); minHeight = dp(52)
            contentDescription = "Indietro"
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(getColor(R.color.text))
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange))
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        // --- WebView + schermata errore ---
        val content = FrameLayout(this)
        webView = WebView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(false)
            webViewClient = client
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                    if (newProgress > 20) view.evaluateJavascript(HIDE_CHROME_JS, null)
                }
            }
        }
        content.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        errorView = buildErrorView().also {
            it.visibility = View.GONE
            content.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else load()
    }

    private fun buildErrorView(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(R.color.bg))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
        }
        v.addView(TextView(this).apply {
            text = "Nessuna connessione"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(getColor(R.color.text))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        v.addView(TextView(this).apply {
            text = "Questo strumento ha bisogno di internet per caricarsi. Controlla la rete e riprova."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getColor(R.color.dim))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        v.addView(Button(this).apply {
            text = "Riprova"
            setOnClickListener { load() }
        })
        return v
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun load() {
        failed = false
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(startUrl)
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            // Pagine del sito ditworld.com restano nell'app, il resto va al browser.
            if (uri.scheme == "https" && (uri.host == HOST || uri.host?.endsWith(".$HOST") == true)) return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) { /* nessuna app disponibile */ }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            view.evaluateJavascript(HIDE_CHROME_JS, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(HIDE_CHROME_JS, null)
            if (failed) {
                webView.visibility = View.GONE
                errorView.visibility = View.VISIBLE
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) failed = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack() && !failed) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
