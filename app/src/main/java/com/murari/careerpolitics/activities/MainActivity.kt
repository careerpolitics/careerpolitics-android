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
import android.webkit.WebViewClient
import android.graphics.Color
import android.content.res.Configuration
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
import kotlinx.coroutines.flow.collectLatest
import com.murari.careerpolitics.util.DrawerMenuPublisher
import com.murari.careerpolitics.util.DrawerMenuItem

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: OfflineWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()
    private var isPageLoaded: Boolean = false
    private var pendingOpenSidebar: Boolean = false
    private var lastSidebarTriggerMs: Long = 0L
    
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
            requestOpenWebSidebar()
        }

        // Left-edge swipe target triggers the sidebar without invoking DrawerLayout animation
        binding?.root?.findViewById<View>(R.id.edge_swipe_target)?.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private val touchSlop = 16 * resources.displayMetrics.density
            private var triggered = false
            override fun onTouch(v: View?, event: android.view.MotionEvent?): Boolean {
                event ?: return false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        triggered = false
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = kotlin.math.abs(event.rawY - startY)
                        if (!triggered && dx > touchSlop && dy < 3 * touchSlop) {
                            v?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            triggered = true
                        }
                        return triggered
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (triggered) {
                            requestOpenWebSidebar()
                            triggered = false
                            return true
                        }
                    }
                }
                return false
            }
        })

        // Disable drawer gestures (we handle edge swipe ourselves to avoid flicker)
        drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        // We no longer populate a native menu; web app owns the sidebar
    }

    private fun openWebSidebar() {
        // Re-inject helper quickly then open; also dispatch a custom event
        val js = "(function(){" +
                "try{if(!(window.__nativeSidebar&&__nativeSidebar.open)){throw new Error('reinjection');}}catch(_){" +
                    "(function(){if(window.__nativeSidebar&&window.__nativeSidebar.__v==1)return true;window.__nativeSidebar=(function(){function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*\\u003d\"menu\" i],button[aria-label*\\u003d\"navigation\" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid\\u003d\"menu-button\"],[data-action\\u003d\"open-sidebar\"]');}function isOpen(){return document.body.classList.contains('sidebar-open')||!!document.querySelector('.sidebar.open,.drawer.open,.nav--open,.is-open,.menu-open,[aria-expanded\\u003d\"true\"]');}function open(){if(isOpen())return true;var el=q();if(el){el.click();return true;}var t=document.elementFromPoint(24,24);if(t){t.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));return true;}return false;}return {open:open,isOpen:isOpen,__v:1};})();})();" +
                "}" +
                "try{window.__nativeSidebar&&__nativeSidebar.open&&__nativeSidebar.open();}catch(e){} try{window.dispatchEvent(new Event('native-open-sidebar'));}catch(e){} true;})()"
        binding?.webView?.evaluateJavascript(js, null)
    }

    private fun requestOpenWebSidebar() {
        val now = System.currentTimeMillis()
        if (now - lastSidebarTriggerMs < 250) return // debounce
        lastSidebarTriggerMs = now
        if (!isPageLoaded) {
            pendingOpenSidebar = true
            return
        }
        openWebSidebar()
    }

    private fun closeWebSidebarOr(onNotOpen: () -> Unit) {
        val js = "(function(){try{var isOpen=!!(window.__nativeSidebar&&__nativeSidebar.isOpen&&__nativeSidebar.isOpen()); if(isOpen){var el=document.querySelector('button.js-hamburger-trigger'); if(el){el.click(); return true;} } return false;}catch(e){return false;}})()"
        binding?.webView?.evaluateJavascript(js) { result ->
            val isClosed = result?.contains("true") == true
            if (!isClosed) onNotOpen()
        }
    }

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
                userAgentString = "DEV-Native-android"
            }

            webView.addJavascriptInterface(webViewBridge, "AndroidBridge")

            webViewClient = OfflineWebViewClient(this, webView, mainActivityScope) {
                webView.visibility = View.VISIBLE
                isPageLoaded = true
                if (pendingOpenSidebar) {
                    pendingOpenSidebar = false
                    openWebSidebar()
                }
            }

            webView.webViewClient = webViewClient as WebViewClient
            webView.webChromeClient = CustomWebChromeClient("https://careerpolitics.com/", this)
            webViewBridge.webViewClient = webViewClient as CustomWebViewClient
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding?.webView?.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding?.webView?.loadUrl("https://careerpolitics.com/")
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
