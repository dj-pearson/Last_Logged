import SwiftUI
import RevenueCat

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTier: PricingTier = .annual
    @State private var isPurchasing = false
    @State private var errorMessage: String?

    private let revenueCatService = RevenueCatService.shared

    /// When true, hides the close button but shows a "Continue with Free" link
    /// to comply with App Store guidelines (users must always have a way to proceed).
    var isHardPaywall: Bool = false

    enum PricingTier: String, CaseIterable {
        case monthly
        case annual
        case lifetime
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    headerSection
                    featuresSection
                    pricingCards
                    purchaseButton
                    restoreLink

                    if isHardPaywall {
                        Button {
                            AnalyticsService.shared.trackPaywallDismissed()
                            dismiss()
                        } label: {
                            Text("Continue with Free Plan")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        .frame(minHeight: 44)
                        .accessibilityLabel("Continue with free plan")
                        .accessibilityHint("Dismiss paywall and continue using the free tier")
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .multilineTextAlignment(.center)
                    }

                    legalLinks
                }
                .padding()
            }
            .navigationTitle("Upgrade to Premium")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !isHardPaywall {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") {
                            AnalyticsService.shared.trackPaywallDismissed()
                            dismiss()
                        }
                    }
                }
            }
        }
        // Always allow dismissal — App Store requires users can proceed without purchasing
        .onAppear {
            AnalyticsService.shared.trackPaywallPresented()
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 8) {
            Image(systemName: "star.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(.accent)

            Text("Unlock Unlimited Tracking")
                .font(.title2)
                .fontWeight(.bold)

            Text("Track everything that matters with no limits.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 8)
    }

    // MARK: - Features

    private var featuresSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            featureRow(icon: "infinity", text: "Unlimited trackers")
            featureRow(icon: "clock.arrow.circlepath", text: "Full completion history")
            featureRow(icon: "square.grid.2x2.fill", text: "All widget styles")
            featureRow(icon: "bell.badge.fill", text: "Smart reminders")
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func featureRow(icon: String, text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(.accent)
                .frame(width: 24)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
            Spacer()
            Image(systemName: "checkmark")
                .foregroundStyle(.green)
                .font(.caption)
                .fontWeight(.bold)
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(text)
    }

    // MARK: - Pricing Cards

    private var pricingCards: some View {
        VStack(spacing: 12) {
            pricingCard(
                tier: .annual,
                title: "Annual",
                price: annualPrice,
                detail: "7-day free trial",
                badge: savingsBadge
            )

            pricingCard(
                tier: .monthly,
                title: "Monthly",
                price: monthlyPrice,
                detail: "7-day free trial",
                badge: nil
            )

            pricingCard(
                tier: .lifetime,
                title: "Lifetime",
                price: lifetimePrice,
                detail: "One-time purchase",
                badge: nil
            )
        }
    }

    private func pricingCard(
        tier: PricingTier,
        title: String,
        price: String,
        detail: String,
        badge: String?
    ) -> some View {
        let isSelected = selectedTier == tier
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTier = tier
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.headline)
                        if let badge {
                            Text(badge)
                                .font(.caption2)
                                .fontWeight(.bold)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(.green)
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                                .accessibilityLabel(badge.replacingOccurrences(of: "%", with: " percent"))
                        }
                    }
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Text(price)
                    .font(.headline)
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isSelected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: isSelected ? 2 : 1)
            )
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.accentColor.opacity(0.08) : Color.clear)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title), \(price), \(detail)\(badge.map { ", \($0)" } ?? "")")
        .accessibilityHint(isSelected ? "Currently selected" : "Double-tap to select this plan")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    // MARK: - Purchase Button

    private var purchaseButton: some View {
        Button {
            Task {
                await performPurchase()
            }
        } label: {
            Group {
                if isPurchasing {
                    ProgressView()
                        .tint(.white)
                } else {
                    Text(purchaseButtonTitle)
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .disabled(isPurchasing)
        .accessibilityLabel(purchaseButtonTitle)
        .accessibilityHint("Double-tap to proceed with purchase")
    }

    private var purchaseButtonTitle: String {
        switch selectedTier {
        case .monthly, .annual:
            return "Start Free Trial"
        case .lifetime:
            return "Purchase Lifetime Access"
        }
    }

    // MARK: - Restore

    private var restoreLink: some View {
        Button {
            Task {
                await restorePurchases()
            }
        } label: {
            Text("Restore Purchases")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .disabled(isPurchasing)
        .frame(minHeight: 44)
        .accessibilityLabel("Restore purchases")
        .accessibilityHint("Restore previously purchased subscriptions")
    }

    // MARK: - Legal Links

    private var legalLinks: some View {
        HStack(spacing: 16) {
            Link("Privacy Policy", destination: URL(string: "https://lastlogged.com/privacy")!)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Link("Terms of Service", destination: URL(string: "https://lastlogged.com/terms")!)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.bottom, 8)
    }

    // MARK: - Pricing Helpers

    private var monthlyPrice: String {
        packageFor(.monthly)?.localizedPriceString ?? "$3.99/mo"
    }

    private var annualPrice: String {
        packageFor(.annual)?.localizedPriceString ?? "$24.99/yr"
    }

    private var lifetimePrice: String {
        packageFor(.lifetime)?.localizedPriceString ?? "$59.99"
    }

    private var savingsBadge: String {
        // Calculate savings vs monthly: (3.99*12 - 24.99) / (3.99*12) ≈ 48%
        "Save 48%"
    }

    private func packageFor(_ tier: PricingTier) -> Package? {
        let targetId: String
        switch tier {
        case .monthly: targetId = RevenueCatService.premiumMonthlyId
        case .annual: targetId = RevenueCatService.premiumAnnualId
        case .lifetime: targetId = RevenueCatService.lifetimeId
        }
        return revenueCatService.availablePackages.first {
            $0.storeProduct.productIdentifier == targetId
        }
    }

    // MARK: - Actions

    @MainActor
    private func performPurchase() async {
        guard let package = packageFor(selectedTier) else {
            errorMessage = "Product not available. Please try again later."
            return
        }
        isPurchasing = true
        errorMessage = nil
        do {
            let success = try await revenueCatService.purchase(package)
            if success {
                AnalyticsService.shared.trackTrialStarted()
                AnalyticsService.shared.trackPaywallConverted()
                dismiss()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isPurchasing = false
    }

    @MainActor
    private func restorePurchases() async {
        isPurchasing = true
        errorMessage = nil
        do {
            try await revenueCatService.restorePurchases()
            if revenueCatService.isPremium {
                dismiss()
            } else {
                errorMessage = "No active subscription found."
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isPurchasing = false
    }
}

// MARK: - Free Tier Limits

enum FreeTierLimits {
    static let maxTrackers = 5
    static let maxHistoryPerItem = 10
    static let maxWidgetStyles = 1
}

#Preview {
    PaywallView()
}
