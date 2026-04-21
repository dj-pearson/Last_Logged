package com.pearsonmedia.lastlogged.ui.components

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.pearsonmedia.lastlogged.service.AnalyticsService
import com.pearsonmedia.lastlogged.service.RevenueCatService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val revenueCatService: RevenueCatService,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    val packages: StateFlow<List<RevenueCatService.PackageInfo>> = revenueCatService.availablePackages
    val isLoading: StateFlow<Boolean> = revenueCatService.isLoading
    val error: StateFlow<String?> = revenueCatService.error

    fun purchase(activity: Activity, packageIdentifier: String) {
        revenueCatService.purchase(activity, packageIdentifier)
    }

    fun restorePurchases() {
        revenueCatService.restorePurchases()
    }

    fun trackDismissed() {
        analyticsService.trackPaywallDismissed()
    }
}
