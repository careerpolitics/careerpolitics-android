plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("kotlin-kapt")
}

android {
    namespace = "com.murari.careerpolitics"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.murari.careerpolitics"
        minSdk = 27
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    // Build performance optimizations
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Change to release config for production
        }
        debug {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    dataBinding {
        enable = true
    }
}

kapt {
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
    implementation(libs.androidx.activity.ktx)
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

    // Pusher Push Notifications
    implementation(libs.push.notifications.android)

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

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
