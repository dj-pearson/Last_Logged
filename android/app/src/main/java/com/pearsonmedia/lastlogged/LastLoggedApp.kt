package com.pearsonmedia.lastlogged

import android.app.Application
import com.pearsonmedia.lastlogged.service.AnalyticsService
import com.pearsonmedia.lastlogged.service.CrashReportingService
import com.pearsonmedia.lastlogged.service.PushTokenService
import com.pearsonmedia.lastlogged.service.RevenueCatService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LastLoggedApp : Application() {

    @Inject lateinit var revenueCatService: RevenueCatService
    @Inject lateinit var pushTokenService: PushTokenService
    @Inject lateinit var analyticsService: AnalyticsService
    @Inject lateinit var crashReportingService: CrashReportingService

    override fun onCreate() {
        super.onCreate()
        // First, so a crash during the rest of startup is still reported.
        crashReportingService.configure()
        analyticsService.configure()
        revenueCatService.configure()
        pushTokenService.initialize()
    }
}
