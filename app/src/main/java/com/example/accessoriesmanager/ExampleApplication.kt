package com.example.accessoriesmanager

import android.app.Application
import com.cloudinary.android.MediaManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExampleApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        MediaManager.init(this, mapOf("cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME))
    }
}