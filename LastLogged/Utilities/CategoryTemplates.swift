import Foundation

struct TemplateItem: Identifiable {
    let id = UUID()
    let name: String
    let iconName: String
    let reminderIntervalDays: Int
}

struct CategoryTemplates {

    static let templates: [String: [TemplateItem]] = [
        "Home Maintenance": [
            TemplateItem(name: "HVAC Filter", iconName: "fan.fill", reminderIntervalDays: 90),
            TemplateItem(name: "Smoke Detector Batteries", iconName: "flame.fill", reminderIntervalDays: 180),
            TemplateItem(name: "Gutter Cleaning", iconName: "drop.fill", reminderIntervalDays: 180),
            TemplateItem(name: "Water Heater Flush", iconName: "drop.fill", reminderIntervalDays: 365),
            TemplateItem(name: "Dryer Vent Cleaning", iconName: "wind", reminderIntervalDays: 365),
        ],
        "Health & Wellness": [
            TemplateItem(name: "Dental Cleaning", iconName: "heart.text.square.fill", reminderIntervalDays: 180),
            TemplateItem(name: "Annual Physical", iconName: "stethoscope", reminderIntervalDays: 365),
            TemplateItem(name: "Eye Exam", iconName: "eye.fill", reminderIntervalDays: 365),
            TemplateItem(name: "Flu Shot", iconName: "cross.case.fill", reminderIntervalDays: 365),
        ],
        "Car Care": [
            TemplateItem(name: "Oil Change", iconName: "oilcan.fill", reminderIntervalDays: 90),
            TemplateItem(name: "Tire Rotation", iconName: "tire.fill", reminderIntervalDays: 180),
            TemplateItem(name: "Air Filter", iconName: "wind", reminderIntervalDays: 365),
            TemplateItem(name: "Brake Inspection", iconName: "car.fill", reminderIntervalDays: 365),
        ],
        "Personal Care": [
            TemplateItem(name: "Haircut", iconName: "scissors", reminderIntervalDays: 42),
            TemplateItem(name: "Deep Clean House", iconName: "sparkles", reminderIntervalDays: 30),
            TemplateItem(name: "Wash Bedding", iconName: "bed.double.fill", reminderIntervalDays: 14),
        ],
        "Social & Family": [
            TemplateItem(name: "Call Parents", iconName: "phone.fill", reminderIntervalDays: 14),
            TemplateItem(name: "Call Close Friend", iconName: "phone.fill", reminderIntervalDays: 30),
            TemplateItem(name: "Date Night", iconName: "heart.fill", reminderIntervalDays: 14),
        ],
        "Pet Care": [
            TemplateItem(name: "Flea/Tick Treatment", iconName: "pawprint.fill", reminderIntervalDays: 30),
            TemplateItem(name: "Heartworm Medication", iconName: "pills.fill", reminderIntervalDays: 30),
            TemplateItem(name: "Vet Checkup", iconName: "stethoscope", reminderIntervalDays: 365),
            TemplateItem(name: "Grooming", iconName: "scissors", reminderIntervalDays: 60),
        ],
    ]

    static func templates(for categoryName: String) -> [TemplateItem] {
        templates[categoryName] ?? []
    }
}
