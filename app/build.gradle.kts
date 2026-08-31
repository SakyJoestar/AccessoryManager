

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.accessoriesmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.accessoriesmanager"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "CLOUDINARY_CLOUD_NAME",
            "\"${localProperties.getProperty("CLOUDINARY_CLOUD_NAME", "")}\""
        )
        buildConfigField(
            "String",
            "CLOUDINARY_UPLOAD_PRESET",
            "\"${localProperties.getProperty("CLOUDINARY_UPLOAD_PRESET", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.cloudinary.android)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.androidx.gridlayout)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("org.dhatim:fastexcel:0.20.2")
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation("com.google.code.gson:gson:2.11.0")
}

kapt {
    correctErrorTypes = true
}