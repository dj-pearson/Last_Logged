import Foundation
import SwiftData
import SwiftUI

@Observable
final class OnboardingViewModel {
    private var modelContext: ModelContext

    var currentPage: Int = 0
    var selectedCategoryIds: Set<UUID> = []

    @ObservationIgnored
    @AppStorage("onboardingCompleted") var onboardingCompleted = false

    var categories: [TrackerCategory] = []

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        fetchCategories()
        // Pre-select all categories by default
        selectedCategoryIds = Set(categories.map(\.id))
    }

    // MARK: - Fetch

    func fetchCategories() {
        let descriptor = FetchDescriptor<TrackerCategory>(
            sortBy: [SortDescriptor(\.sortOrder)]
        )
        do {
            categories = try modelContext.fetch(descriptor)
        } catch {
            categories = []
        }
    }

    // MARK: - Category Selection

    func toggleCategory(_ category: TrackerCategory) {
        if selectedCategoryIds.contains(category.id) {
            selectedCategoryIds.remove(category.id)
        } else {
            selectedCategoryIds.insert(category.id)
        }
    }

    func isCategorySelected(_ category: TrackerCategory) -> Bool {
        selectedCategoryIds.contains(category.id)
    }

    // MARK: - Complete Onboarding

    func completeOnboarding() {
        // Create template tracker items for each selected category
        let selectedCategories = categories.filter { selectedCategoryIds.contains($0.id) }

        for category in selectedCategories {
            let templates = CategoryTemplates.templates(for: category.name)
            for (index, template) in templates.enumerated() {
                let item = TrackerItem(
                    name: template.name,
                    categoryId: category.id,
                    reminderIntervalDays: template.reminderIntervalDays,
                    sortOrder: index,
                    iconName: template.iconName
                )
                modelContext.insert(item)
            }
        }

        do {
            try modelContext.save()
        } catch {
            // Save failed silently
        }

        // Schedule notifications for the newly created items
        let context = modelContext
        Task {
            await NotificationService.shared.rescheduleAllNotifications(modelContext: context)
        }

        // Trigger sync
        SupabaseService.shared.scheduleSyncAfterWrite(modelContext: modelContext)

        onboardingCompleted = true
    }
}
