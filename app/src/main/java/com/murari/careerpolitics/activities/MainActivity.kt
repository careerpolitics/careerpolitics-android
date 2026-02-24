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
import android.graphics.Color
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.activities.main.MainIntentRouter
import com.murari.careerpolitics.activities.main.MainWebViewConfigurator
import com.murari.careerpolitics.activities.main.PushNotificationInitializer
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private lateinit var webViewClient: OfflineWebViewClient
    private lateinit var intentRouter: MainIntentRouter
    private lateinit var pushNotificationInitializer: PushNotificationInitializer
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
            if (isGranted) pushNotificationInitializer.initialize()
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
        intentRouter = MainIntentRouter { binding?.webView }
        pushNotificationInitializer = PushNotificationInitializer(applicationContext)
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
            } else if (!intentRouter.handleAppLinkIntent(intent)) {
                navigateToHome()
            }
            intentRouter.handleNotificationIntent(intent)
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
        val clientId = AppConfig.authConfig.googleWebClientId
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
        val callbackPath = AppConfig.authConfig.nativeGoogleLoginCallbackPath
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
                    pushNotificationInitializer.initialize()

                shouldShowRequestPermissionRationale(permission) ->
                    requestNotificationPermissionLauncher.launch(permission)

                else -> requestNotificationPermissionLauncher.launch(permission)
            }
        } else {
            pushNotificationInitializer.initialize()
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.let { webView ->
            intentRouter.handleNotificationIntent(intent)
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

        if (!intentRouter.handleAppLinkIntent(intent)) {
            intentRouter.handleNotificationIntent(intent)
        }
    }

    private fun setWebViewSettings() {
        binding?.webView?.let { webView ->
            val configurator = MainWebViewConfigurator(
                activity = this,
                scope = mainActivityScope,
                bridge = webViewBridge,
                onNativeGoogleSignInRequested = { authUrl -> launchNativeGoogleSignIn(authUrl) },
                onPageFinished = { webView.visibility = View.VISIBLE }
            )
            webViewClient = configurator.configure(webView)
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding?.webView?.restoreState(savedInstanceState)
    }

    private fun navigateToHome() {
        binding?.webView?.loadUrl(AppConfig.baseUrl)
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
