/**
 * Centralized dependency and version management.
 * 
 * This file prevents magic strings in build.gradle files and provides a single source
 * of truth for all dependency versions and build configuration.
 * 
 * Architecture Decision:
 * - Using buildSrc for compile-time type safety
 * - Alternative: Version catalogs (libs.versions.toml) - already in use, keep both
 * - buildSrc is ideal for build configuration constants
 */

object AppConfig {
    const val compileSdk = 36
    const val minSdk = 29
    const val targetSdk = 36
    
    // Version info managed here for easy bumping
    const val versionMajor = 3
    const val versionMinor = 0
    const val versionPatch = 0
    const val versionBuild = 0
    
    // Computed version code: MAJOR * 10000 + MINOR * 100 + PATCH
    const val versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
    const val versionName = "$versionMajor.$versionMinor.$versionPatch"
}

object BuildTypes {
    const val DEBUG = "debug"
    const val RELEASE = "release"
    const val STAGING = "staging" // New staging build type
}

object ProductFlavors {
    const val DIMENSION = "environment"
    
    // Future: If you need different app variants (free/paid, different backends)
    // const val FREE = "free"
    // const val PAID = "paid"
}

object AppConstants {
    // Network Configuration
    const val NETWORK_TIMEOUT_MS = 1500
    const val NETWORK_RETRY_COUNT = 3
    
    // WebView Configuration
    const val WEBVIEW_CACHE_SIZE_MB = 50
    
    // Build Configuration
    const val ENABLE_PROGUARD_RELEASE = true
    const val ENABLE_RESOURCE_SHRINKING = true
}
