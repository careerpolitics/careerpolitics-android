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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {
    private val webViewBridge: AndroidWebViewBridge = AndroidWebViewBridge(this)
    private lateinit var webViewClient: CustomWebViewClient

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val mainActivityScope = MainScope()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(LOG_TAG, "POST_NOTIFICATIONS permission granted.")
                initializePushNotifications()
            } else {
                Log.w(LOG_TAG, "POST_NOTIFICATIONS permission denied.")
            }
        }

    override fun layout(): Int {
        return R.layout.activity_main
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setWebViewSettings()
        savedInstanceState?.let { restoreState(it) } ?: navigateToHome()
        //handleIntent(intent)
        checkAndRequestNotificationPermission()
        /*binding.showPopupImageView.setOnClickListener {
            showForemAppAlert()
        }

        binding.openForemImageView.setOnClickListener {
            val url = binding.webView.url
            ForemAppDialog.openForemApp(this, url)
        }*/
    }

    override fun onResume() {
        if (intent.extras != null && intent.extras!!["url"] != null) {
            val targetUrl = intent.extras!!["url"].toString()
            try {
                val targetHost = Uri.parse(targetUrl).host ?: ""
                if (targetHost.contains("careerpolitics.com")) {
                    binding.webView.loadUrl(targetUrl)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "${e.message}")
            }
        }

        /*if (ForemAppDialog.isForemAppAlreadyInstalled(this)) {
            binding.openForemImageView.visibility = View.VISIBLE
        } else {
            binding.openForemImageView.visibility = View.GONE
        }*/

        super.onResume()
        webViewClient.observeNetwork()
    }

    override fun onStop() {
        super.onStop()
        webViewClient.unobserveNetwork()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Make sure we're not leaving any audio playing behind
        webViewBridge.terminatePodcast()

        // Coroutine cleanup
        mainActivityScope.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val appLinkData: Uri? = intent.data
        appLinkData?.host?.let {
            binding.webView.loadUrl(appLinkData.toString())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setWebViewSettings() {
        WebView.setWebContentsDebuggingEnabled(true)
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.settings.userAgentString = "DEV-Native-android"

        binding.webView.addJavascriptInterface(webViewBridge, "AndroidBridge")
        webViewClient = CustomWebViewClient(
            this@MainActivity,
            binding.webView,
            mainActivityScope
        ) {
            //binding.splash.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            //binding.bottomLayout.visibility = View.VISIBLE
            // showForemAppAlert()
        }
        binding.webView.webViewClient = webViewClient
        webViewBridge.webViewClient = webViewClient
        binding.webView.webChromeClient = CustomWebChromeClient("https://careerpolitics.com/", this)
    }

    private fun showForemAppAlert() {
        val url: String = binding.webView.url ?: ""
        ForemAppDialog.newInstance(url).show(
            supportFragmentManager,
            "ForemAppDialogFragment"
        )
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding.webView.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding.webView.loadUrl("https://careerpolitics.com/")
    }

    // open home page on back press if webview can go back

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun launchGallery(filePathCallback: ValueCallback<Array<Uri>>?) {
        this.filePathCallback = filePathCallback

        val galleryIntent = Intent().apply {
            // Show only images, no videos or anything else
            type = "image/*"
            action = Intent.ACTION_PICK
        }

        // Always show the chooser (if there are multiple options available)
        startActivityForResult(
            Intent.createChooser(galleryIntent, "Select Picture"),
            PIC_CHOOSER_REQUEST,
            null    // No additional data
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PIC_CHOOSER_REQUEST) {
            return super.onActivityResult(requestCode, resultCode, data)
        }

        when (resultCode) {
            RESULT_OK -> data?.data?.let {
                filePathCallback?.onReceiveValue(arrayOf(it))
                filePathCallback = null
            }

            RESULT_CANCELED -> {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        }
    }

    companion object {
        private const val PIC_CHOOSER_REQUEST = 100
        private const val LOG_TAG = "MainActivity"
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU is Android 13
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(LOG_TAG, "POST_NOTIFICATIONS permission already granted.")
                    // You can use the API that requires the permission.
                    initializePushNotifications()
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // In an educational UI, explain to the user why your app requires this
                    // permission for a specific feature to behave as expected, and what
                    // features are disabled if it's declined. In this UI, include a
                    // "cancel" or "no thanks" button that lets the user continue
                    // using your app without granting the permission.
                    Log.i(LOG_TAG, "Showing rationale for POST_NOTIFICATIONS permission.")
                    // Example: show a dialog here explaining why you need the permission
                    // and then call requestPermissionLauncher.launch(...) if the user agrees.
                    // For simplicity, directly requesting here, but a rationale is recommended.
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    // Directly ask for the permission.
                    // The result of this request will be handled by the
                    // an 'onRequestPermissionsResult' callback registered via
                    // 'registerForActivityResult(...)'
                    Log.i(LOG_TAG, "Requesting POST_NOTIFICATIONS permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Notification permission is not required for versions below Android 13.
            Log.i(LOG_TAG, "Notification permission not required for this Android version.")
            initializePushNotifications()
        }
    }

    private fun initializePushNotifications() {
        // Moved Pusher initialization here to ensure it's called after permission check
        // or if permission is not required.
        FirebaseMessaging.getInstance().subscribeToTopic("all").addOnCompleteListener {
            if(it.isSuccessful){
                Log.i("fcm","subscribed")
            }else{
                Log.i("fcm","not subscribed")
            }
        }
        Log.d(LOG_TAG, "Initializing PushNotifications.")
        PushNotifications.start(applicationContext, "cdaf9857-fad0-4bfb-b360-64c1b2693ef3")
        PushNotifications.addDeviceInterest("broadcast")
    }
}