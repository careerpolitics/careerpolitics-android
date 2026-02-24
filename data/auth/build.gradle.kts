plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    id("kotlin-kapt")
}

android {
    namespace = "com.murari.careerpolitics.data.auth"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kapt { correctErrorTypes = true }

dependencies {
    implementation(libs.play.services.auth)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
