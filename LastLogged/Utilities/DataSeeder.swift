import Foundation
import SwiftData

struct DataSeeder {

    static func seedDefaultCategoriesIfNeeded(modelContext: ModelContext) {
        let descriptor = FetchDescriptor<TrackerCategory>()
        let existingCount = (try? modelContext.fetchCount(descriptor)) ?? 0
        guard existingCount == 0 else { return }

        let categories: [(name: String, iconName: String, colorHex: String, sortOrder: Int)] = [
            ("Home Maintenance", "house.fill", "#4A90D9"),
            ("Health & Wellness", "heart.fill", "#E74C3C"),
            ("Car Care", "car.fill", "#F39C12"),
            ("Personal Care", "person.fill", "#9B59B6"),
            ("Social & Family", "person.2.fill", "#2ECC71"),
            ("Pet Care", "pawprint.fill", "#1ABC9C"),
        ].enumerated().map { (index, item) in
            (name: item.0, iconName: item.1, colorHex: item.2, sortOrder: index)
        }

        for category in categories {
            let record = TrackerCategory(
                name: category.name,
                iconName: category.iconName,
                colorHex: category.colorHex,
                sortOrder: category.sortOrder,
                isDefault: true
            )
            modelContext.insert(record)
        }

        try? modelContext.save()
    }
}
