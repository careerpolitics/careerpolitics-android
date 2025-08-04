package com.murari.careerpolitics.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: CustomWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(LOG_TAG, "POST_NOTIFICATIONS permission granted.")
                initializePushNotifications()
            } else {
                Log.w(LOG_TAG, "POST_NOTIFICATIONS permission denied.")
            }
        }

    override fun layout(): Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup WebView
        setWebViewSettings()

        // Restore WebView state or navigate to home
        savedInstanceState?.let { restoreState(it) } ?: navigateToHome()

        // Setup gallery file chooser
        initGalleryLauncher()

        // Check notification permission
        checkAndRequestNotificationPermission()
    }

    private fun initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let {
                    filePathCallback?.onReceiveValue(arrayOf(it))
                } ?: filePathCallback?.onReceiveValue(null)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }
    }

    override fun onResume() {
        super.onResume()

        intent.extras?.getString("url")?.let { targetUrl ->
            try {
                if (targetUrl.toUri().host?.contains("careerpolitics.com") == true) {
                    binding.webView.loadUrl(targetUrl)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error loading intent URL: ${e.message}")
            }
        }

        webViewClient.observeNetwork()
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
        binding.webView.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            binding.webView.loadUrl(uri.toString())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setWebViewSettings() {
        WebView.setWebContentsDebuggingEnabled(true)
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "DEV-Native-android"
        }

        binding.webView.addJavascriptInterface(webViewBridge, "AndroidBridge")

        webViewClient = CustomWebViewClient(
            this,
            binding.webView,
            mainActivityScope
        ) {
            binding.webView.visibility = View.VISIBLE
        }

        binding.webView.webViewClient = webViewClient
        binding.webView.webChromeClient = CustomWebChromeClient("https://careerpolitics.com/", this)
        webViewBridge.webViewClient = webViewClient
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding.webView.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding.webView.loadUrl("https://careerpolitics.com/")
    }

    fun handleCustomBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }

    override fun launchGallery(filePathCallback: ValueCallback<Array<Uri>>) {
        this.filePathCallback = filePathCallback

        val galleryIntent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }

        galleryLauncher.launch(Intent.createChooser(galleryIntent, "Select Picture"))
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    initializePushNotifications()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            initializePushNotifications()
        }
    }

    private fun initializePushNotifications() {
        FirebaseMessaging.getInstance().subscribeToTopic("all").addOnCompleteListener {
            Log.i("FCM", if (it.isSuccessful) "Subscribed" else "Failed to subscribe")
        }
        PushNotifications.start(applicationContext, "cdaf9857-fad0-4bfb-b360-64c1b2693ef3")
        PushNotifications.addDeviceInterest("broadcast")
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}
