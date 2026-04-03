package com.pearsonmedia.lastlogged.util

data class TrackerTemplate(
    val name: String,
    val iconName: String,
    val reminderIntervalDays: Int
)

object CategoryTemplates {

    val defaultCategories = listOf(
        CategoryTemplate("Home Maintenance", "home", "#4f46e5"),
        CategoryTemplate("Health & Wellness", "favorite", "#ef4444"),
        CategoryTemplate("Car Care", "directions_car", "#f59e0b"),
        CategoryTemplate("Personal Care", "person", "#8b5cf6"),
        CategoryTemplate("Social & Family", "group", "#10b981"),
        CategoryTemplate("Pet Care", "pets", "#ec4899")
    )

    data class CategoryTemplate(
        val name: String,
        val iconName: String,
        val colorHex: String
    )

    fun templatesFor(categoryName: String): List<TrackerTemplate> = when (categoryName) {
        "Home Maintenance" -> listOf(
            TrackerTemplate("HVAC Filter", "air", 90),
            TrackerTemplate("Smoke Detector Batteries", "sensors", 180),
            TrackerTemplate("Gutter Cleaning", "roofing", 180),
            TrackerTemplate("Dryer Vent Cleaning", "dry_cleaning", 365),
            TrackerTemplate("Water Heater Flush", "water_drop", 365)
        )
        "Health & Wellness" -> listOf(
            TrackerTemplate("Dental Checkup", "medical_services", 180),
            TrackerTemplate("Eye Exam", "visibility", 365),
            TrackerTemplate("Physical Exam", "health_and_safety", 365),
            TrackerTemplate("Flu Shot", "vaccines", 365)
        )
        "Car Care" -> listOf(
            TrackerTemplate("Oil Change", "oil_barrel", 90),
            TrackerTemplate("Tire Rotation", "tire_repair", 180),
            TrackerTemplate("Air Filter", "filter_alt", 365),
            TrackerTemplate("Brake Inspection", "speed", 365)
        )
        "Personal Care" -> listOf(
            TrackerTemplate("Haircut", "content_cut", 42),
            TrackerTemplate("Toothbrush Replacement", "brush", 90),
            TrackerTemplate("Contact Lens Replacement", "visibility", 30)
        )
        "Social & Family" -> listOf(
            TrackerTemplate("Call Parents", "phone", 7),
            TrackerTemplate("Date Night", "restaurant", 14),
            TrackerTemplate("Friend Hangout", "group", 30)
        )
        "Pet Care" -> listOf(
            TrackerTemplate("Vet Checkup", "pets", 365),
            TrackerTemplate("Flea/Tick Treatment", "bug_report", 30),
            TrackerTemplate("Nail Trimming", "content_cut", 30),
            TrackerTemplate("Heartworm Prevention", "favorite", 30)
        )
        else -> emptyList()
    }
}
