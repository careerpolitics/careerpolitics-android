import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    id("kotlin-kapt")
}

// ============================================================================
// Load secrets from secrets.properties (never committed to Git)
// ============================================================================
val secretsPropertiesFile = rootProject.file("app/secrets.properties")
val secretsProperties = Properties()
if (secretsPropertiesFile.exists()) {
    secretsProperties.load(secretsPropertiesFile.inputStream())
} else {
    logger.warn("⚠️  secrets.properties not found. Using placeholder values.")
    logger.warn("📝 Copy secrets.properties.template to secrets.properties and fill in values.")
}

// Helper function to get secret or fallback to environment variable or default
fun getSecret(key: String, envVar: String? = null, default: String = ""): String {
    return secretsProperties.getProperty(key)
        ?: (envVar?.let { System.getenv(it) })
        ?: default
}

android {
    namespace = "com.murari.careerpolitics"
        compileSdk = 36

    defaultConfig {
        applicationId = "com.murari.careerpolitics"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        // ========================================================================
        // BuildConfig Fields - Injected at compile time
        // ========================================================================
        // These values will be available as BuildConfig.FIELD_NAME in Kotlin code

        buildConfigField("String", "BASE_URL", "\"https://careerpolitics.com/\"")
        buildConfigField("String", "BASE_DOMAIN", "\"careerpolitics.com\"")
        buildConfigField("int", "NETWORK_TIMEOUT_MS", "1500")
        buildConfigField("String", "FIREBASE_TOPIC", "\"all\"")
        buildConfigField("String", "PUSHER_INTEREST", "\"broadcast\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${getSecret("google.web.client.id", "GOOGLE_WEB_CLIENT_ID", "89605387556-4i6ndjolfd6p6458elkggt712p4a3hic.apps.googleusercontent.com")}\"")
        buildConfigField("String", "NATIVE_GOOGLE_LOGIN_CALLBACK_PATH", "\"${getSecret("native.google.login.path", "NATIVE_GOOGLE_LOGIN_PATH", "/users/auth/google_oauth2/callback")}\"")
    }

    // Build performance optimizations
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // ========================================================================
    // Signing Configurations
    // ========================================================================
    signingConfigs {
        create("release") {
            // Load from secrets.properties or environment variables (CI/CD)
            storeFile = file(getSecret("keystore.path", "KEYSTORE_PATH", "release.keystore"))
            storePassword = getSecret("keystore.password", "KEYSTORE_PASSWORD", "")
            keyAlias = getSecret("key.alias", "KEY_ALIAS", "")
            keyPassword = getSecret("key.password", "KEY_PASSWORD", "")

            // Enable V2 signing for better security and faster verification
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        // ====================================================================
        // DEBUG Build Type
        // ====================================================================
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false

            // Debug-specific BuildConfig fields
            buildConfigField("String", "USER_AGENT", "\"DEV-Native-android-debug\"")
            buildConfigField("String", "PUSHER_INSTANCE_ID", "\"${getSecret("pusher.instance.id", "PUSHER_INSTANCE_ID", "debug-instance-id")}\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_WEBVIEW_DEBUG", "true")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "false")

        }

        // ====================================================================
        // STAGING Build Type (new)
        // ====================================================================
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            isDebuggable = true
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Reuse existing library module variants (debug/release) for staging resolution
            matchingFallbacks += listOf("debug", "release")

            // Staging uses production-like settings but different config
            buildConfigField("String", "USER_AGENT", "\"STAGING-Native-android\"")
            buildConfigField("String", "PUSHER_INSTANCE_ID", "\"${getSecret("pusher.instance.id", "PUSHER_INSTANCE_ID", "staging-instance-id")}\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_WEBVIEW_DEBUG", "true")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")

            // Use debug signing for staging (easier testing)
            signingConfig = signingConfigs.getByName("debug")

        }

        // ====================================================================
        // RELEASE Build Type
        // ====================================================================
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Production BuildConfig fields
            buildConfigField("String", "USER_AGENT", "\"PROD-Native-android\"")
            buildConfigField("String", "PUSHER_INSTANCE_ID", "\"${getSecret("pusher.instance.id", "PUSHER_INSTANCE_ID", "")}\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            buildConfigField("boolean", "ENABLE_WEBVIEW_DEBUG", "false")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")

            // CRITICAL: Use release signing config for production
            signingConfig = signingConfigs.getByName("release")

            // Strip out native debug symbols
            ndk {
                debugSymbolLevel = "FULL" // Upload to Play Console for crash symbolication
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true // Enable BuildConfig generation
    }

    dataBinding {
        enable = true
    }

    // ========================================================================
    // Lint Configuration
    // ========================================================================
    lint {
        // Fail build on critical security issues
        abortOnError = true
        lintConfig = file("../config/lint/lint.xml")
        checkReleaseBuilds = true

        // Check for security vulnerabilities
        enable += listOf(
            "HardcodedDebugMode",
            "HardcodedText",
            "SetJavaScriptEnabled",
            "ExportedReceiver",
            "ExportedService"
        )

        // Don't fail on warnings in debug builds
        warningsAsErrors = false
    }

    // ========================================================================
    // Packaging Options
    // ========================================================================
    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("eventBusIndex", "com.murari.careerpolitics.MyEventBusIndex")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.browser)
    implementation(libs.constraintlayout)
    implementation(libs.multidex)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // ExoPlayer
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.hls)
    implementation(libs.exoplayer.ui)
    implementation(libs.extension.mediasession)

    // Firebase Messaging
    implementation(libs.firebase.messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.push.notifications.android)
    implementation(libs.play.services.auth)

    // Gson
    implementation(libs.gson)

    // EventBus
    implementation(libs.eventbus)
    kapt(libs.eventbus.annotation.processor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    //splash
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)

    // DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Modular foundation
    implementation(project(":core:common"))
    implementation(project(":core:webview"))
    implementation(project(":feature:shell"))
    implementation(project(":feature:deeplink"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:media"))
    implementation(project(":data:auth"))
    implementation(project(":data:notifications"))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
