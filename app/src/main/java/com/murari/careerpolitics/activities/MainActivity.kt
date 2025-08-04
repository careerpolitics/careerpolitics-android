package com.murari.careerpolitics.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: CustomWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initializePushNotifications()
        }

    override fun layout(): Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        binding?.let {
            setContentView(it.root)

            onBackPressedDispatcher.addCallback(this) {
                handleCustomBackPressed()
            }

            setWebViewSettings()

            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            } else {
                navigateToHome()
            }

            initGalleryLauncher()

            mainActivityScope.launch {
                delay(500)
                checkAndRequestNotificationPermission()
                isReady = true
            }
        }
    }

    private fun initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            filePathCallback?.onReceiveValue(uri?.let { arrayOf(it) } ?: null)
            filePathCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.let { webView ->
            intent.extras?.getString("url")?.let { targetUrl ->
                try {
                    if (targetUrl.toUri().host?.contains("careerpolitics.com") == true) {
                        webView.loadUrl(targetUrl)
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error loading intent URL: ${e.message}")
                }
            }
            webViewClient.observeNetwork()
        }
    }

    override fun onStop() {
        super.onStop()
        webViewClient.unobserveNetwork()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewBridge.terminatePodcast()
        mainActivityScope.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.webView?.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        binding?.webView?.loadUrl(intent.data?.toString().orEmpty())
    }

    private fun setWebViewSettings() {
        binding?.webView?.let { webView ->
            WebView.setWebContentsDebuggingEnabled(true)
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "DEV-Native-android"
            }

            webView.addJavascriptInterface(webViewBridge, "AndroidBridge")

            webViewClient = CustomWebViewClient(
                this,
                webView,
                mainActivityScope
            ) {
                webView.visibility = View.VISIBLE
            }

            webView.webViewClient = webViewClient
            webView.webChromeClient = CustomWebChromeClient("https://careerpolitics.com/", this)
            webViewBridge.webViewClient = webViewClient
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding?.webView?.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding?.webView?.loadUrl("https://careerpolitics.com/")
    }

    fun handleCustomBackPressed() {
        binding?.webView?.let {
            if (it.canGoBack()) it.goBack() else finish()
        }
    }

    override fun launchGallery(filePathCallback: ValueCallback<Array<Uri>>) {
        this.filePathCallback = filePathCallback
        galleryLauncher.launch(
            Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        )
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> initializePushNotifications()

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                else -> requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else initializePushNotifications()
    }

    private fun initializePushNotifications() {
        FirebaseMessaging.getInstance().subscribeToTopic("all")
        PushNotifications.start(applicationContext, "cdaf9857-fad0-4bfb-b360-64c1b2693ef3")
        PushNotifications.addDeviceInterest("broadcast")
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}