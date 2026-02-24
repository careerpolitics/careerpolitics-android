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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.feature.deeplink.domain.ResolvedDeepLink
import com.murari.careerpolitics.feature.deeplink.presentation.DeepLinkViewModel
import com.murari.careerpolitics.feature.auth.presentation.AuthEffect
import com.murari.careerpolitics.feature.auth.presentation.AuthViewModel
import com.murari.careerpolitics.feature.notifications.domain.NotificationRegistrationError
import com.murari.careerpolitics.feature.notifications.presentation.NotificationsEffect
import com.murari.careerpolitics.feature.notifications.presentation.NotificationsViewModel
import com.murari.careerpolitics.core.webview.bridge.WebViewBridgeRegistry
import com.murari.careerpolitics.core.webview.settings.WebViewSettingsConfig
import com.murari.careerpolitics.core.webview.settings.WebViewSettingsPolicy
import com.murari.careerpolitics.feature.shell.presentation.ShellNavigationCommand
import com.murari.careerpolitics.feature.shell.presentation.ShellRouteSource
import com.murari.careerpolitics.feature.shell.presentation.ShellViewModel
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private val shellViewModel: ShellViewModel by viewModels()
    private val deepLinkViewModel: DeepLinkViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val notificationsViewModel: NotificationsViewModel by viewModels()
    private lateinit var webViewClient: OfflineWebViewClient
    private val webViewBridge = AndroidWebViewBridge(this)
    private val webViewBridgeRegistry = WebViewBridgeRegistry()
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private var isSplashScreenReady = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                notificationsViewModel.onPermissionGranted()
            }
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

            initGoogleSignInLauncher()
            setWebViewSettings()

            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            }

            observeShellState()
            observeAuthState()
            observeNotificationsState()
            shellViewModel.resolveStartupRoute(
                savedInstanceStateExists = savedInstanceState != null,
                deepLinkUrl = resolveDeepLink(intent),
                notificationUrl = extractNotificationUrl(intent),
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

    private fun initGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            authViewModel.onGoogleSignInResult(
                data = result.data,
                baseUrl = AppConfig.baseUrl,
                callbackPath = AppConfig.nativeGoogleLoginCallbackPath
            )
        }
    }

    private fun launchNativeGoogleSignIn(oAuthUrl: String): Boolean {
        return authViewModel.onGoogleOAuthRequested(oAuthUrl)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                notificationsViewModel.onPermissionGranted()
            } else {
                notificationsViewModel.onNotificationPermissionRequired()
            }
        } else {
            notificationsViewModel.onPermissionGranted()
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.let { _ ->
            shellViewModel.resolveResumeIntent(extractNotificationUrl(intent))
            webViewClient.observeNetwork()
        }
    }

    override fun onStop() {
        super.onStop()
        webViewClient.unobserveNetwork()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewBridge.release()
        mainActivityScope.cancel()
        
        // Clear WebView to prevent memory leaks
        binding?.webView?.let { webView ->
            webViewBridgeRegistry.detach(webView)
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
        shellViewModel.resolveIncomingIntent(
            deepLinkUrl = resolveDeepLink(intent),
            notificationUrl = extractNotificationUrl(intent)
        )
    }

    private fun resolveDeepLink(intent: Intent?): String? {
        return when (val resolved = deepLinkViewModel.resolve(intent)) {
            is ResolvedDeepLink.Valid -> resolved.url
            is ResolvedDeepLink.Invalid -> {
                Logger.d(LOG_TAG, "Ignoring non-app deep link: ${resolved.candidate}")
                null
            }
            ResolvedDeepLink.None -> null
        }
    }

    private fun extractNotificationUrl(intent: Intent?): String? = intent?.getStringExtra("url")

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

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    val effect = state.pendingEffect ?: return@collect
                    renderAuthEffect(effect)
                    authViewModel.consumeEffect()
                }
            }
        }
    }

    private fun renderAuthEffect(effect: AuthEffect) {
        when (effect) {
            is AuthEffect.LaunchGoogleSignIn -> googleSignInLauncher.launch(effect.intent)
            is AuthEffect.CompleteGoogleLogin -> {
                Logger.d(LOG_TAG, "Completing native Google login via callback URL")
                binding?.webView?.loadUrl(effect.callbackUrl)
            }
            is AuthEffect.Error -> Logger.w(LOG_TAG, effect.message)
        }
    }

    private fun observeNotificationsState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationsViewModel.uiState.collect { state ->
                    state.effect?.let {
                        renderNotificationEffect(it)
                        notificationsViewModel.consumeEffect()
                    }
                    state.error?.let {
                        renderNotificationError(it)
                        notificationsViewModel.consumeError()
                    }
                }
            }
        }
    }

    private fun renderNotificationEffect(effect: NotificationsEffect) {
        when (effect) {
            NotificationsEffect.RequestPermission -> {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            NotificationsEffect.InitializePush -> {
                notificationsViewModel.initializePushRegistration(
                    topic = AppConfig.firebaseBroadcastTopic,
                    instanceId = AppConfig.pusherInstanceId,
                    interest = AppConfig.pusherDeviceInterest
                )
            }
        }
    }

    private fun renderNotificationError(error: NotificationRegistrationError) {
        when (error) {
            is NotificationRegistrationError.Recoverable -> Logger.e(
                LOG_TAG,
                "Push registration recoverable failure: reason=${error.reason}, attempt=${error.attempt}",
                error.throwable
            )

            is NotificationRegistrationError.Fatal -> Logger.e(
                LOG_TAG,
                "Push registration fatal failure: reason=${error.reason}",
                error.throwable
            )
        }
    }

    private fun setWebViewSettings() {
        binding?.webView?.let { webView ->
            val settingsPolicy = WebViewSettingsPolicy(
                WebViewSettingsConfig(
                    enableDebugging = AppConfig.enableWebViewDebugging,
                    enableJavaScript = AppConfig.enableJavaScript,
                    enableDomStorage = AppConfig.enableDomStorage,
                    userAgent = AppConfig.userAgent
                )
            )
            settingsPolicy.apply(webView)

            webViewBridgeRegistry.register("Android", webViewBridge)
            webViewBridgeRegistry.attach(webView)

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
