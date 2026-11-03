package com.dynamicisland.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_ENABLED, false)
                    notifyPage(if (enabled) "Островок включён" else "Островок выключен", enabled)
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(NativeControls(), "NativeControls")
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && webView.url != null) {
            val enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
            notifyPage(if (enabled) "Островок включён" else "Островок выключен", enabled)
        }
    }

    private fun enableIsland() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            } catch (_: Exception) {
                notifyPage("Открой настройки Android и разреши показ поверх других приложений", false)
            }
            return
        }
        startIslandService()
    }

    private fun startIslandService() {
        val intent = Intent(this, DynamicIslandService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        notifyPage("Островок включён поверх приложений", true)
    }

    private fun disableIsland() {
        stopService(Intent(this, DynamicIslandService::class.java))
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        notifyPage("Островок выключен", false)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startIslandService()
            } else {
                notifyPage("Разрешение не выдано — островок не включён", false)
            }
        }
    }

    private fun notifyPage(message: String, enabled: Boolean) {
        if (!::webView.isInitialized) return
        val safeMessage = JSONObject.quote(message)
        webView.post {
            webView.evaluateJavascript(
                "window.onNativeOverlayState && window.onNativeOverlayState($safeMessage, $enabled);",
                null
            )
        }
    }

    private inner class NativeControls {
        @JavascriptInterface
        fun enableIsland() {
            runOnUiThread { this@MainActivity.enableIsland() }
        }

        @JavascriptInterface
        fun disableIsland() {
            runOnUiThread { this@MainActivity.disableIsland() }
        }
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val PREFS = "dynamic_island"
        private const val KEY_ENABLED = "enabled"
    }
}
