package com.pearsonmedia.lastlogged.service

import android.app.Application
import android.util.Log
import com.pearsonmedia.lastlogged.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SubscriptionTier { FREE, PREMIUM, LIFETIME }

object FreeTierLimits {
    const val MAX_TRACKERS = 3
    const val MAX_HISTORY_PER_ITEM = 100
    const val MAX_WIDGET_ITEMS = 1
}

@Singleton
class RevenueCatService @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "RevenueCatService"
        const val PRODUCT_MONTHLY = "premium_monthly"
        const val PRODUCT_ANNUAL = "premium_annual"
        const val PRODUCT_LIFETIME = "lifetime"
        const val ENTITLEMENT_ID = "premium"
    }

    private val _tier = MutableStateFlow(SubscriptionTier.FREE)
    val tier: StateFlow<SubscriptionTier> = _tier.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _availablePackages = MutableStateFlow<List<PackageInfo>>(emptyList())
    val availablePackages: StateFlow<List<PackageInfo>> = _availablePackages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    data class PackageInfo(
        val identifier: String,
        val productId: String,
        val title: String,
        val price: String,
        val description: String
    )

    fun configure() {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        if (apiKey == "placeholder-key") {
            Log.w(TAG, "RevenueCat API key is placeholder — skipping configuration")
            loadCachedState()
            return
        }

        // TODO: Initialize RevenueCat SDK
        // Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        // Purchases.sharedInstance.updatedCustomerInfoListener = ...

        loadCachedState()
        refreshSubscriptionStatus()
    }

    private fun loadCachedState() {
        val cached = secureStorageService.getIsPremium()
        _isPremium.value = cached
        _tier.value = when (secureStorageService.getSubscriptionTier()) {
            "premium" -> SubscriptionTier.PREMIUM
            "lifetime" -> SubscriptionTier.LIFETIME
            else -> SubscriptionTier.FREE
        }

        // Provide default package info for UI
        _availablePackages.value = listOf(
            PackageInfo(PRODUCT_MONTHLY, PRODUCT_MONTHLY, "Monthly", "$3.99/mo", "Billed monthly"),
            PackageInfo(PRODUCT_ANNUAL, PRODUCT_ANNUAL, "Annual", "$24.99/yr", "Save 48%"),
            PackageInfo(PRODUCT_LIFETIME, PRODUCT_LIFETIME, "Lifetime", "$59.99", "One-time purchase")
        )
    }

    fun refreshSubscriptionStatus() {
        // TODO: Query RevenueCat for current entitlements
        // Purchases.sharedInstance.getCustomerInfo { info, error -> ... }
    }

    fun purchase(productId: String) {
        _isLoading.value = true
        _error.value = null
        // TODO: Initiate purchase via RevenueCat
        // Purchases.sharedInstance.purchase(PurchaseParams.Builder(...).build()) { ... }
        _isLoading.value = false
    }

    fun restorePurchases() {
        _isLoading.value = true
        _error.value = null
        // TODO: Restore via RevenueCat
        // Purchases.sharedInstance.restorePurchases { info, error -> ... }
        _isLoading.value = false
    }

    fun updateTier(newTier: SubscriptionTier) {
        _tier.value = newTier
        _isPremium.value = newTier != SubscriptionTier.FREE
        secureStorageService.setIsPremium(newTier != SubscriptionTier.FREE)
        secureStorageService.setSubscriptionTier(
            when (newTier) {
                SubscriptionTier.FREE -> "free"
                SubscriptionTier.PREMIUM -> "premium"
                SubscriptionTier.LIFETIME -> "lifetime"
            }
        )
    }

    fun canCreateTracker(currentCount: Int): Boolean {
        return _isPremium.value || currentCount < FreeTierLimits.MAX_TRACKERS
    }

    fun getHistoryLimit(): Int {
        return if (_isPremium.value) Int.MAX_VALUE else FreeTierLimits.MAX_HISTORY_PER_ITEM
    }

    fun dismissError() {
        _error.value = null
    }
}
