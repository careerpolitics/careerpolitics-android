package com.murari.careerpolitics.config

import com.murari.careerpolitics.BuildConfig

/**
 * Centralized application configuration.
 * 
 * This object provides compile-time constants injected via BuildConfig and runtime configuration.
 * All configuration values should be accessed through this single source of truth.
 * 
 * Architecture Decision:
 * - Using object (singleton) for simplicity since config is read-only after initialization
 * - For DI-heavy apps, convert to interface + implementation with Hilt/Koin injection
 * - All values are compile-time constants where possible for dead code elimination
 */
object AppConfig {
    
    // ============================================================================
    // Environment Configuration (injected at compile time)
    // ============================================================================
    
    /** Current build environment (DEBUG, STAGING, RELEASE) */
    val environment: Environment = when {
        BuildConfig.DEBUG -> Environment.DEBUG
        BuildConfig.BUILD_TYPE == "staging" -> Environment.STAGING
        else -> Environment.RELEASE
    }
    
    val isDebugBuild: Boolean = BuildConfig.DEBUG
    val isReleaseBuild: Boolean = !BuildConfig.DEBUG
    
    // ============================================================================
    // Network Configuration
    // ============================================================================
    
    /** Base URL for the application */
    val baseUrl: String = BuildConfig.BASE_URL
    
    /** Base domain (for validation and network checks) */
    val baseDomain: String = BuildConfig.BASE_DOMAIN
    
    /** Network connectivity check timeout in milliseconds */
    val networkCheckTimeout: Int = BuildConfig.NETWORK_TIMEOUT_MS
    
    /** WebView user agent string */
    val userAgent: String = BuildConfig.USER_AGENT

    /** Google OAuth web client ID for native Android sign-in */
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    /** Relative callback path that exchanges native Google ID token for web session */
    val nativeGoogleLoginCallbackPath: String = BuildConfig.NATIVE_GOOGLE_LOGIN_CALLBACK_PATH
    
    // ============================================================================
    // Push Notification Configuration
    // ============================================================================
    
    /** Pusher Beams instance ID */
    val pusherInstanceId: String = BuildConfig.PUSHER_INSTANCE_ID
    
    /** Firebase topic for broadcast notifications */
    val firebaseBroadcastTopic: String = BuildConfig.FIREBASE_TOPIC
    
    /** Pusher device interest for broadcast */
    val pusherDeviceInterest: String = BuildConfig.PUSHER_INTEREST
    
    // ============================================================================
    // WebView Configuration
    // ============================================================================
    
    /** Enable WebView remote debugging (debug builds only) */
    val enableWebViewDebugging: Boolean = BuildConfig.ENABLE_WEBVIEW_DEBUG
    
    /** Enable JavaScript in WebView */
    val enableJavaScript: Boolean = true // Always true for this app
    
    /** Enable DOM storage in WebView */
    val enableDomStorage: Boolean = true
    
    /** Left edge swipe trigger width in DP */
    const val EDGE_SWIPE_WIDTH_DP = 24
    
    /** Left edge swipe trigger distance in DP */
    const val EDGE_SWIPE_TRIGGER_DP = 30
    
    // ============================================================================
    // Media & Player Configuration
    // ============================================================================
    
    /** Podcast playback tick interval in milliseconds */
    const val PODCAST_TICK_INTERVAL_MS = 1000L
    
    /** Video player default start position in seconds */
    const val VIDEO_DEFAULT_START_POSITION = 0
    
    // ============================================================================
    // Logging Configuration
    // ============================================================================
    
    /** Enable verbose logging */
    val enableLogging: Boolean = BuildConfig.ENABLE_LOGGING
    
    /** Log tag prefix for the application */
    const val LOG_TAG_PREFIX = "CareerPolitics"
    
    // ============================================================================
    // Feature Flags
    // ============================================================================
    
    /** Enable splash screen delay for initialization */
    const val SPLASH_SCREEN_DELAY_MS = 100L
    
    /** Enable crash reporting (Firebase Crashlytics) */
    val enableCrashReporting: Boolean = BuildConfig.ENABLE_CRASHLYTICS
    
    // ============================================================================
    // Security Configuration
    // ============================================================================
    
    /** Clear WebView cache on specific routes */
    val clearCacheRoutes: List<String> = listOf(
        "$baseUrl/enter" // Clear cache when user hits login/enter route
    )
    
    /** Allowed URL schemes for deep linking */
    val allowedUrlSchemes: List<String> = listOf("https")
    
    /** Validate that URLs match base domain */
    fun isValidAppUrl(url: String): Boolean {
        return url.contains(baseDomain, ignoreCase = true)
    }
    
    // ============================================================================
    // Build Information
    // ============================================================================
    
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
    val applicationId: String = BuildConfig.APPLICATION_ID
    val buildType: String = BuildConfig.BUILD_TYPE

    const val LOG_TAG = "CareerPolitics"
}

/**
 * Build environment enumeration
 */
enum class Environment {
    DEBUG,
    STAGING,
    RELEASE
}
