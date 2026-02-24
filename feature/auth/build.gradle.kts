plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    id("kotlin-kapt")
}

android {
    namespace = "com.murari.careerpolitics.feature.auth"
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
    implementation(project(":core:common"))
    implementation(project(":data:auth"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
