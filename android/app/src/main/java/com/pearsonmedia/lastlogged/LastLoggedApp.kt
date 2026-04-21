package com.pearsonmedia.lastlogged

import android.app.Application
import com.pearsonmedia.lastlogged.service.PushTokenService
import com.pearsonmedia.lastlogged.service.RevenueCatService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LastLoggedApp : Application() {

    @Inject lateinit var revenueCatService: RevenueCatService
    @Inject lateinit var pushTokenService: PushTokenService

    override fun onCreate() {
        super.onCreate()
        revenueCatService.configure()
        pushTokenService.initialize()
    }
}
