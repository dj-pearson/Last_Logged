import Foundation
import RevenueCat

enum SubscriptionTier: String {
    case free
    case premium
    case lifetime
}

@Observable
final class RevenueCatService: NSObject {
    static let shared = RevenueCatService()

    // MARK: - Product Identifiers

    static let premiumMonthlyId = "premium_monthly"   // $3.99/mo
    static let premiumAnnualId = "premium_annual"      // $24.99/yr
    static let lifetimeId = "lifetime"                 // $59.99 one-time

    static let allProductIds: Set<String> = [
        premiumMonthlyId,
        premiumAnnualId,
        lifetimeId,
    ]

    // MARK: - State

    private(set) var subscriptionTier: SubscriptionTier = .free
    private(set) var availablePackages: [Package] = []

    var isPremium: Bool {
        subscriptionTier == .premium || subscriptionTier == .lifetime
    }

    // MARK: - Initialization

    private override init() {
        super.init()
    }

    func configure() {
        Purchases.logLevel = .debug
        Purchases.configure(
            with: Configuration.Builder(withAPIKey: AppSecrets.revenueCatAPIKey)
                .with(appUserID: nil)
                .build()
        )
        Purchases.shared.delegate = self

        Task {
            await refreshSubscriptionStatus()
            await loadOfferings()
        }
    }

    // MARK: - Purchase

    func purchase(_ package: Package) async throws -> Bool {
        let result = try await Purchases.shared.purchase(package: package)
        if !result.userCancelled {
            await refreshSubscriptionStatus()
            return true
        }
        return false
    }

    // MARK: - Restore Purchases

    func restorePurchases() async throws {
        _ = try await Purchases.shared.restorePurchases()
        await refreshSubscriptionStatus()
    }

    // MARK: - Subscription Status

    func refreshSubscriptionStatus() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateTier(from: customerInfo)
        } catch {
            // Keep current tier on error
        }
    }

    private func updateTier(from customerInfo: CustomerInfo) {
        if customerInfo.entitlements["premium"]?.isActive == true {
            // Check if lifetime by looking at product identifier
            if let activeEntitlement = customerInfo.entitlements["premium"],
               activeEntitlement.productIdentifier == Self.lifetimeId {
                subscriptionTier = .lifetime
            } else {
                subscriptionTier = .premium
            }
        } else {
            subscriptionTier = .free
        }
    }

    // MARK: - Offerings

    private func loadOfferings() async {
        do {
            let offerings = try await Purchases.shared.offerings()
            if let current = offerings.current {
                availablePackages = current.availablePackages
            }
        } catch {
            // Packages unavailable — paywall can show placeholder
        }
    }
}

// MARK: - PurchasesDelegate

extension RevenueCatService: PurchasesDelegate {
    func purchases(_ purchases: Purchases, receivedUpdated customerInfo: CustomerInfo) {
        updateTier(from: customerInfo)
    }
}
