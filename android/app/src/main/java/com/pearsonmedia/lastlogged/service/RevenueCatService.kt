package com.pearsonmedia.lastlogged.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.pearsonmedia.lastlogged.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
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
    @ApplicationContext private val context: Context,
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

    private var configured = false
    private var currentOffering: Offering? = null

    data class PackageInfo(
        val identifier: String,
        val productId: String,
        val title: String,
        val price: String,
        val description: String
    )

    fun configure() {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        if (apiKey.isBlank() || apiKey == "placeholder-key") {
            Log.w(TAG, "RevenueCat API key is placeholder — skipping SDK configuration")
            loadCachedState()
            seedDefaultPackages()
            return
        }

        if (!configured) {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
            configured = true
        }

        loadCachedState()
        refreshSubscriptionStatus()
        fetchOfferings()
    }

    fun identifyUser(userId: String) {
        if (!configured) return
        Purchases.sharedInstance.logIn(userId, object : com.revenuecat.purchases.interfaces.LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                applyCustomerInfo(customerInfo)
            }
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "RevenueCat logIn failed: ${error.message}")
            }
        })
    }

    fun onSignOut() {
        if (!configured) return
        Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = applyCustomerInfo(customerInfo)
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "RevenueCat logOut failed: ${error.message}")
            }
        })
    }

    private fun loadCachedState() {
        val cached = secureStorageService.getIsPremium()
        _isPremium.value = cached
        _tier.value = when (secureStorageService.getSubscriptionTier()) {
            "premium" -> SubscriptionTier.PREMIUM
            "lifetime" -> SubscriptionTier.LIFETIME
            else -> SubscriptionTier.FREE
        }
    }

    private fun seedDefaultPackages() {
        _availablePackages.value = listOf(
            PackageInfo(PRODUCT_MONTHLY, PRODUCT_MONTHLY, "Monthly", "$3.99/mo", "Billed monthly"),
            PackageInfo(PRODUCT_ANNUAL, PRODUCT_ANNUAL, "Annual", "$24.99/yr", "Save 48%"),
            PackageInfo(PRODUCT_LIFETIME, PRODUCT_LIFETIME, "Lifetime", "$59.99", "One-time purchase")
        )
    }

    fun refreshSubscriptionStatus() {
        if (!configured) return
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = applyCustomerInfo(customerInfo)
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "getCustomerInfo failed: ${error.message}")
            }
        })
    }

    private fun fetchOfferings() {
        if (!configured) return
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val offering = offerings.current ?: run {
                    seedDefaultPackages()
                    return
                }
                currentOffering = offering
                _availablePackages.value = offering.availablePackages.map { pkg ->
                    val product = pkg.product
                    PackageInfo(
                        identifier = pkg.identifier,
                        productId = product.id,
                        title = product.title,
                        price = product.price.formatted,
                        description = product.description
                    )
                }
            }
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "getOfferings failed: ${error.message}")
                seedDefaultPackages()
            }
        })
    }

    fun purchase(activity: Activity, packageIdentifier: String) {
        if (!configured) {
            _error.value = "Purchases are not available right now."
            return
        }
        val pkg = currentOffering?.availablePackages?.firstOrNull { it.identifier == packageIdentifier }
        if (pkg == null) {
            _error.value = "That plan is no longer available. Please try again."
            return
        }
        _isLoading.value = true
        _error.value = null
        Purchases.sharedInstance.purchase(
            com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    applyCustomerInfo(customerInfo)
                }
                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _isLoading.value = false
                    if (!userCancelled) _error.value = error.message
                }
            }
        )
    }

    fun restorePurchases() {
        if (!configured) {
            _error.value = "Purchases are not available right now."
            return
        }
        _isLoading.value = true
        _error.value = null
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                _isLoading.value = false
                applyCustomerInfo(customerInfo)
            }
            override fun onError(error: PurchasesError) {
                _isLoading.value = false
                _error.value = error.message
            }
        })
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val premiumEntitlement = info.entitlements.active[ENTITLEMENT_ID]
        val isPremium = premiumEntitlement != null
        val productId = premiumEntitlement?.productIdentifier
        val newTier = when {
            !isPremium -> SubscriptionTier.FREE
            productId == PRODUCT_LIFETIME -> SubscriptionTier.LIFETIME
            else -> SubscriptionTier.PREMIUM
        }
        updateTier(newTier)
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
