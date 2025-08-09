package com.murari.careerpolitics.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.GravityCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.BuildConfig
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import com.pusher.pushnotifications.PushNotifications
import com.murari.careerpolitics.core.sidebar.SidebarController
import com.murari.careerpolitics.core.gesture.EdgeSwipeHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
 

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: OfflineWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()
    private val sidebarController = SidebarController()
    
    // Drawer
    private val drawerLayout by lazy { binding?.root?.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout) }
    private val navigationView by lazy { binding?.root?.findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view) }

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

        // Draw behind system bars for edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        binding?.let {
            setContentView(it.root)
            enableImmersiveMode()
            setupDrawer()

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
                delay(500) // allow for splash transition
                requestNotificationPermissionIfNeeded()
                isSplashScreenReady = true
            }
        }
    }

    private fun setupDrawer() {
        // Hamburger click should trigger the web app's sidebar
        binding?.root?.findViewById<android.widget.ImageButton>(R.id.menu_button)?.setOnClickListener { v ->
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            binding?.webView?.let { sidebarController.requestOpen(it) }
        }

        // Left-edge swipe target triggers the sidebar without invoking DrawerLayout animation
        binding?.root?.findViewById<View>(R.id.edge_swipe_target)?.let { target ->
            EdgeSwipeHandler(target) { binding?.webView?.let { sidebarController.requestOpen(it) } }
        }

        // Disable drawer gestures (we handle edge swipe ourselves to avoid flicker)
        drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        // We no longer populate a native menu; web app owns the sidebar
    }

    // Sidebar open/close handled by SidebarController via WebView

    private fun enableImmersiveMode() {
        val rootView = binding?.root ?: window.decorView
        val controller = WindowInsetsControllerCompat(window, rootView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        val isDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
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
                userAgentString = BuildConfig.USER_AGENT
            }

            webView.addJavascriptInterface(webViewBridge, "AndroidBridge")
            // Alias for legacy pages expecting AndroidWebViewBridge
            webView.addJavascriptInterface(webViewBridge, "AndroidWebViewBridge")

            webViewClient = OfflineWebViewClient(this, webView, mainActivityScope) {
                webView.visibility = View.VISIBLE
                sidebarController.onPageLoaded(webView)
            }

            webView.webViewClient = webViewClient as WebViewClient
            webView.webChromeClient = CustomWebChromeClient(BuildConfig.BASE_URL, this)
            webViewBridge.webViewClient = webViewClient as CustomWebViewClient
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding?.webView?.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding?.webView?.loadUrl(BuildConfig.BASE_URL)
    }

    fun handleCustomBackPressed() {
        // Close drawer first if open
        drawerLayout?.let { dl ->
            if (dl.isDrawerOpen(GravityCompat.START)) {
                dl.closeDrawer(GravityCompat.START)
                return
            }
        }
        binding?.webView?.let { if (it.canGoBack()) it.goBack() else finish() }
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
