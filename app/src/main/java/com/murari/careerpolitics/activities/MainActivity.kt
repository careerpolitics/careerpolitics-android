package com.murari.careerpolitics.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.BuildConfig
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: OfflineWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private var isSplashScreenReady = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initializePushNotifications()
        }

    override fun layout(): Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen integration
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isSplashScreenReady }

        // Edge-to-edge
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        binding?.let { binding ->
            setContentView(binding.root)
            configureSystemBarsAppearance()

            // Apply system bar insets as padding to avoid overlap
            applySystemBarInsets(binding.root)

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
                delay(100) // allow for splash transition
                requestNotificationPermissionIfNeeded()
                isSplashScreenReady = true
            }
        }
    }

    private fun configureSystemBarsAppearance() {
        val rootView = binding?.root ?: window.decorView
        val controller = WindowInsetsControllerCompat(window, rootView)
        val isDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }

    private fun applySystemBarInsets(target: View) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = sysBars.left, top = sysBars.top, right = sysBars.right, bottom = sysBars.bottom)
            insets
        }
    }

    private fun initGalleryLauncher() {
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            filePathCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            filePathCallback = null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ->
                    initializePushNotifications()

                shouldShowRequestPermissionRationale(permission) ->
                    requestNotificationPermissionLauncher.launch(permission)

                else -> requestNotificationPermissionLauncher.launch(permission)
            }
        } else {
            initializePushNotifications()
        }
    }

    private fun initializePushNotifications() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all")
            PushNotifications.start(applicationContext, "923a6e14-cca6-47dd-b98e-8145f7724dd7")
            PushNotifications.addDeviceInterest("broadcast")
            Log.d(LOG_TAG, "Push Notifications initialized")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error initializing push notifications: ${e.message}", e)
            // Fallback: try to initialize without Firebase if it fails
            try {
                PushNotifications.start(applicationContext, "923a6e14-cca6-47dd-b98e-8145f7724dd7")
                PushNotifications.addDeviceInterest("broadcast")
                Log.d(LOG_TAG, "Push Notifications initialized (fallback)")
            } catch (fallbackException: Exception) {
                Log.e(LOG_TAG, "Fallback push notification initialization failed: ${fallbackException.message}", fallbackException)
            }
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
        
        // Clear WebView to prevent memory leaks
        binding?.webView?.let { webView ->
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
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
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = BuildConfig.USER_AGENT
            }

            webView.addJavascriptInterface(webViewBridge, "AndroidBridge")
            webView.addJavascriptInterface(webViewBridge, "AndroidWebViewBridge")

            webViewClient = OfflineWebViewClient(this, webView, mainActivityScope) {
                webView.visibility = View.VISIBLE
            }

            webView.webViewClient = webViewClient as WebViewClient
            webView.webChromeClient = CustomWebChromeClient(BuildConfig.BASE_URL, this)
            webViewBridge.webViewClient = webViewClient as CustomWebViewClient

            // Left-edge swipe gesture to open web sidebar, without affecting vertical pull-to-refresh
            setupLeftEdgeSwipeForSidebar(webView)
        }
    }

    private fun setupLeftEdgeSwipeForSidebar(webView: WebView) {
        val edgeWidthPx = (24 * webView.resources.displayMetrics.density).toInt()
        val triggerDxPx = (30 * webView.resources.displayMetrics.density).toInt()
        var downX = 0f
        var downY = 0f
        var eligible = false
        var triggered = false

        webView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    eligible = downX <= edgeWidthPx
                    triggered = false
                    false // do not consume; allow WebView to handle
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (eligible && !triggered) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx > triggerDxPx && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) {
                            triggered = true
                            openWebSidebar(webView)
                            // Do not consume to keep vertical gestures intact
                            false
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    eligible = false
                    triggered = false
                    false
                }
                else -> false
            }
        }
    }

    private fun openWebSidebar(webView: WebView) {
        val js = """
            (function(){
                function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*="menu" i],button[aria-label*="navigation" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid="menu-button"],[data-action="open-sidebar"]');}
                function simulate(el){try{el.focus();}catch(e){} var o={bubbles:true,cancelable:true}; try{el.dispatchEvent(new PointerEvent('pointerdown',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mousedown',o));}catch(e){} try{el.dispatchEvent(new TouchEvent('touchstart',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('click',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mouseup',o));}catch(e){} try{el.dispatchEvent(new PointerEvent('pointerup',o));}catch(e){} }
                var el=q(); if(el){simulate(el); return true;} return false;
            })()
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding?.webView?.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding?.webView?.loadUrl(BuildConfig.BASE_URL)
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

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}
