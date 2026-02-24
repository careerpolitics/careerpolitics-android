package com.murari.careerpolitics.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.feature.shell.presentation.ShellNavigationCommand
import com.murari.careerpolitics.feature.shell.presentation.ShellRouteSource
import com.murari.careerpolitics.feature.shell.presentation.ShellViewModel
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private val shellViewModel: ShellViewModel by viewModels()
    private lateinit var webViewClient: OfflineWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isGoogleSignInInProgress = false
    private var pendingGoogleOAuthState: String? = null
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

        // Enable edge-to-edge content and transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        binding?.let {
            setContentView(it.root)
            applyEdgeToEdgeInsets()
            configureSystemBarsAppearance()

            onBackPressedDispatcher.addCallback(this) {
                handleCustomBackPressed()
            }

            initGoogleSignIn()
            initGoogleSignInLauncher()
            setWebViewSettings()

            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            }

            observeShellState()
            shellViewModel.resolveStartupRoute(
                savedInstanceStateExists = savedInstanceState != null,
                intent = intent,
                homeUrl = AppConfig.baseUrl
            )

            initGalleryLauncher()

            mainActivityScope.launch {
                delay(AppConfig.SPLASH_SCREEN_DELAY_MS)
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

    private fun applyEdgeToEdgeInsets() {
        val rootView = binding?.root ?: return
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
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

    private fun initGoogleSignIn() {
        val clientId = AppConfig.googleWebClientId
        if (clientId.isBlank()) {
            Logger.w(LOG_TAG, "Google web client ID not configured. Native Google login disabled.")
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(clientId)
            .requestServerAuthCode(clientId, true)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun initGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isGoogleSignInInProgress = false
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            try {
                val account = task.getResult(ApiException::class.java)
                val authCode = account.serverAuthCode
                val idToken = account.idToken

                if (authCode.isNullOrBlank() && idToken.isNullOrBlank()) {
                    Logger.w(LOG_TAG, "Google sign-in succeeded without auth code or ID token")
                    return@registerForActivityResult
                }

                completeNativeGoogleLogin(authCode, idToken)
            } catch (exception: ApiException) {
                Logger.e(LOG_TAG, "Native Google sign-in failed", exception)
            }
        }
    }

    private fun launchNativeGoogleSignIn(oAuthUrl: String): Boolean {
        pendingGoogleOAuthState = oAuthUrl.toUri().getQueryParameter("state") ?: "navbar_basic"

        if (!::googleSignInClient.isInitialized || !::googleSignInLauncher.isInitialized) {
            Logger.w(LOG_TAG, "Google native sign-in is not initialized")
            return false
        }

        if (isGoogleSignInInProgress) {
            Logger.d(LOG_TAG, "Google sign-in already in progress")
            return true
        }

        isGoogleSignInInProgress = true
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        return true
    }

    private fun completeNativeGoogleLogin(authCode: String?, idToken: String?) {
        val callbackPath = AppConfig.nativeGoogleLoginCallbackPath
            .trim()
            .let { if (it.startsWith("/")) it else "/$it" }

        val callbackUri = AppConfig.baseUrl.toUri().buildUpon()
            .encodedPath(callbackPath)
            .apply {
                if (!authCode.isNullOrBlank()) {
                    appendQueryParameter("code", authCode)
                }
                if (!idToken.isNullOrBlank()) {
                    appendQueryParameter("id_token", idToken)
                }
                pendingGoogleOAuthState?.let { appendQueryParameter("state", it) }
                appendQueryParameter("platform", "android")
            }
            .build()
            .toString()

        Logger.d(LOG_TAG, "Completing native Google login via callback URL")
        binding?.webView?.loadUrl(callbackUri)
        pendingGoogleOAuthState = null
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
            // Subscribe to Firebase topic using config
            FirebaseMessaging.getInstance().subscribeToTopic(AppConfig.firebaseBroadcastTopic)

            // Initialize Pusher with config-driven instance ID
            PushNotifications.start(applicationContext, AppConfig.pusherInstanceId)
            PushNotifications.addDeviceInterest(AppConfig.pusherDeviceInterest)

            Logger.d(LOG_TAG, "Push Notifications initialized(awaiting user auth for token registration)")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Error initializing push notifications", e)

            // Fallback: try to initialize without Firebase if it fails
            try {
                PushNotifications.start(applicationContext, AppConfig.pusherInstanceId)
                PushNotifications.addDeviceInterest(AppConfig.pusherDeviceInterest)

                Logger.d(LOG_TAG, "Push Notifications initialized (fallback)")
            } catch (fallbackException: Exception) {
                Logger.e(LOG_TAG, "Fallback push notification initialization failed", fallbackException)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.let { _ ->
            shellViewModel.resolveResumeIntent(intent)
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
        setIntent(intent)
        shellViewModel.resolveIncomingIntent(intent)
    }

    private fun observeShellState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shellViewModel.uiState.collect { state ->
                    val command = state.pendingNavigation ?: return@collect
                    renderShellNavigation(command)
                    shellViewModel.consumeNavigation()
                }
            }
        }
    }

    private fun renderShellNavigation(command: ShellNavigationCommand) {
        when (command) {
            is ShellNavigationCommand.LoadUrl -> {
                binding?.webView?.loadUrl(command.url)
                logNavigation(command)
                if (command.source in setOf(
                        ShellRouteSource.STARTUP_NOTIFICATION,
                        ShellRouteSource.INCOMING_NOTIFICATION,
                        ShellRouteSource.RESUME_NOTIFICATION
                    )) {
                    consumeNotificationExtras(intent)
                }
            }
        }
    }

    private fun consumeNotificationExtras(intent: Intent?) {
        intent?.removeExtra("url")
        intent?.removeExtra("notification_type")
    }

    private fun logNavigation(command: ShellNavigationCommand.LoadUrl) {
        when (command.source) {
            ShellRouteSource.STARTUP_DEEP_LINK,
            ShellRouteSource.INCOMING_DEEP_LINK -> Logger.d(
                LOG_TAG,
                "Opening app link in WebView: ${command.url}"
            )

            ShellRouteSource.STARTUP_NOTIFICATION,
            ShellRouteSource.INCOMING_NOTIFICATION,
            ShellRouteSource.RESUME_NOTIFICATION -> Logger.d(
                LOG_TAG,
                "Notification routed to WebView: ${command.url}"
            )

            ShellRouteSource.STARTUP_HOME -> Logger.d(
                LOG_TAG,
                "Routing startup to home"
            )
        }
    }

    private fun setWebViewSettings() {
        binding?.webView?.let { webView ->
            // Enable remote debugging only in debug/staging builds
            WebView.setWebContentsDebuggingEnabled(AppConfig.enableWebViewDebugging)

            with(webView.settings) {
                javaScriptEnabled = AppConfig.enableJavaScript
                domStorageEnabled = AppConfig.enableDomStorage
                userAgentString = AppConfig.userAgent

                // Additional security settings
                allowFileAccess = false // Prevent file:// URL access
                allowContentAccess = true // Allow content:// URLs for image picking
                databaseEnabled = true // Required for DOM storage

                // Performance settings
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            }

            webView.addJavascriptInterface(webViewBridge, "Android")

            webViewClient = OfflineWebViewClient(
                this,
                webView,
                mainActivityScope,
                onPageFinish = { webView.visibility = View.VISIBLE },
                onGoogleNativeSignInRequested = { authUrl -> launchNativeGoogleSignIn(authUrl) }
            )

            webView.webViewClient = webViewClient as WebViewClient
            webView.webChromeClient = CustomWebChromeClient(AppConfig.baseUrl, this)
            webViewBridge.webViewClient = webViewClient as CustomWebViewClient

            // Left-edge swipe gesture to open web sidebar, without affecting vertical pull-to-refresh
            setupLeftEdgeSwipeForSidebar(webView)
        }
    }

    private fun setupLeftEdgeSwipeForSidebar(webView: WebView) {
        val edgeWidthPx = (AppConfig.EDGE_SWIPE_WIDTH_DP * webView.resources.displayMetrics.density).toInt()
        val triggerDxPx = (AppConfig.EDGE_SWIPE_TRIGGER_DP * webView.resources.displayMetrics.density).toInt()
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
