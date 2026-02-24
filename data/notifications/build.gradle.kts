plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    id("kotlin-kapt")
}

android {
    namespace = "com.murari.careerpolitics.data.notifications"
    compileSdk = 36

    defaultConfig { minSdk = 29 }

    buildTypes {
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kapt { correctErrorTypes = true }

dependencies {
    implementation(libs.firebase.messaging)
    implementation(libs.push.notifications.android)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
