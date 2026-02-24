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
import com.murari.careerpolitics.util.IntentHandler
import com.murari.careerpolitics.util.WebViewManager
import android.view.View
import android.webkit.ValueCallback
import android.graphics.Color
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.R
import com.murari.careerpolitics.auth.GoogleAuthManager
import com.murari.careerpolitics.databinding.ActivityMainBinding
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<ActivityMainBinding>(), CustomWebChromeClient.CustomListener {

    private val webViewBridge = AndroidWebViewBridge(this)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val mainActivityScope = MainScope()
    private var webViewManager: WebViewManager? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var cameraImageUri: Uri? = null
    private lateinit var googleAuthManager: GoogleAuthManager
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

            initGoogleAuth()
            initWebView(it.webView)

            if (savedInstanceState != null) {
                it.webView.restoreState(savedInstanceState)
            } else if (!IntentHandler.handleAppLink(intent, it.webView)) {
                it.webView.loadUrl(AppConfig.baseUrl)
            }
            IntentHandler.handleNotification(intent, it.webView)
            initFileChooserLauncher()

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

    private fun initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: if (result.resultCode == RESULT_OK) cameraImageUri else null
            filePathCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            filePathCallback = null
            cameraImageUri = null
        }
    }

    private fun initGoogleAuth() {
        googleAuthManager = GoogleAuthManager(
            activity = this,
            scope = mainActivityScope,
            onAuthComplete = { binding?.webView?.loadUrl(AppConfig.baseUrl) }
        )
        googleAuthManager.init()
    }

    private fun launchNativeGoogleSignIn(@Suppress("UNUSED_PARAMETER") oAuthUrl: String): Boolean {
        return googleAuthManager.launchSignIn()
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

            Logger.d(LOG_TAG, "Push Notifications initialized (awaiting user auth for token registration)")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Error initializing push notifications", e)
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.let { webView ->
            IntentHandler.handleNotification(intent, webView)
            webViewManager?.webViewClient?.observeNetwork()
        }
    }

    override fun onStop() {
        super.onStop()
        webViewManager?.webViewClient?.unobserveNetwork()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewBridge.terminatePodcast()
        mainActivityScope.cancel()
        webViewManager?.destroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.webView?.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        binding?.webView?.let { webView ->
            if (!IntentHandler.handleAppLink(intent, webView)) {
                IntentHandler.handleNotification(intent, webView)
            }
        }
    }


    private fun initWebView(webView: WebView) {
        webViewManager = WebViewManager(
            webView = webView,
            bridge = webViewBridge,
            scope = mainActivityScope,
            chromeClientListener = this,
            onPageFinish = { webView.visibility = View.VISIBLE },
            onGoogleNativeSignInRequested = { authUrl -> launchNativeGoogleSignIn(authUrl) }
        ).also { it.setup() }
    }

    fun handleCustomBackPressed() {
        binding?.webView?.let {
            if (it.canGoBack()) it.goBack() else finish()
        }
    }

    override fun launchGallery(filePathCallback: ValueCallback<Array<Uri>>) {
        this.filePathCallback = filePathCallback

        val galleryIntent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }

        val cameraIntent = try {
            val imageFile = java.io.File.createTempFile(
                "IMG_${System.currentTimeMillis()}", ".jpg", cacheDir
            )
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", imageFile
            )
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri)
            }
        } catch (e: Exception) {
            Logger.w(LOG_TAG, "Camera intent unavailable: ${e.message}")
            null
        }

        val chooser = Intent.createChooser(galleryIntent, "Select Image")
        cameraIntent?.let {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
        }
        fileChooserLauncher.launch(chooser)
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}