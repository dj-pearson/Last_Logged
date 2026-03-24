import SwiftUI
import SwiftData

struct TemplatePickerView: View {
    @Environment(\.dismiss) private var dismiss
    let viewModel: HomeViewModel
    @State private var selectedTemplateIds: Set<UUID> = []

    private var categoriesWithTemplates: [(category: TrackerCategory, templates: [TemplateItem])] {
        viewModel.categories.compactMap { category in
            let templates = CategoryTemplates.templates(for: category.name)
            guard !templates.isEmpty else { return nil }
            return (category: category, templates: templates)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(categoriesWithTemplates, id: \.category.id) { group in
                    Section {
                        ForEach(group.templates) { template in
                            templateRow(template: template, category: group.category)
                        }
                    } header: {
                        Label(group.category.name, systemImage: group.category.iconName)
                            .font(.headline)
                            .foregroundStyle(Color(hex: group.category.colorHex) ?? .primary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Add Templates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selectedTemplateIds.count))") {
                        addSelectedTemplates()
                    }
                    .disabled(selectedTemplateIds.isEmpty)
                }
                ToolbarItem(placement: .bottomBar) {
                    HStack {
                        Button("Select All") { selectAll() }
                        Spacer()
                        Button("Deselect All") { selectedTemplateIds.removeAll() }
                    }
                }
            }
        }
    }

    private func templateRow(template: TemplateItem, category: TrackerCategory) -> some View {
        Button {
            toggleSelection(template)
        } label: {
            HStack {
                Image(systemName: selectedTemplateIds.contains(template.id) ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selectedTemplateIds.contains(template.id) ? .accent : .secondary)
                    .font(.title3)

                Image(systemName: template.iconName)
                    .foregroundStyle(.accent)
                    .frame(width: 28)

                Text(template.name)
                    .foregroundStyle(.primary)

                Spacer()

                Text(formatInterval(template.reminderIntervalDays))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(template.name), every \(formatInterval(template.reminderIntervalDays))")
        .accessibilityHint(selectedTemplateIds.contains(template.id) ? "Double-tap to deselect" : "Double-tap to select")
        .accessibilityAddTraits(selectedTemplateIds.contains(template.id) ? .isSelected : [])
    }

    private func toggleSelection(_ template: TemplateItem) {
        if selectedTemplateIds.contains(template.id) {
            selectedTemplateIds.remove(template.id)
        } else {
            selectedTemplateIds.insert(template.id)
        }
    }

    private func selectAll() {
        for group in categoriesWithTemplates {
            for template in group.templates {
                selectedTemplateIds.insert(template.id)
            }
        }
    }

    private func addSelectedTemplates() {
        for group in categoriesWithTemplates {
            for template in group.templates where selectedTemplateIds.contains(template.id) {
                viewModel.createItem(
                    name: template.name,
                    categoryId: group.category.id,
                    reminderIntervalDays: template.reminderIntervalDays,
                    iconName: template.iconName
                )
            }
        }
        dismiss()
    }

    private func formatInterval(_ days: Int) -> String {
        if days >= 365 && days % 365 == 0 {
            let years = days / 365
            return years == 1 ? "1 year" : "\(years) years"
        } else if days >= 30 && days % 30 == 0 {
            let months = days / 30
            return months == 1 ? "1 month" : "\(months) months"
        } else if days >= 7 && days % 7 == 0 {
            let weeks = days / 7
            return weeks == 1 ? "1 week" : "\(weeks) weeks"
        } else {
            return days == 1 ? "1 day" : "\(days) days"
        }
    }
}

#Preview {
    TemplatePickerView(viewModel: HomeViewModel(modelContext: {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try! ModelContainer(
            for: TrackerItem.self, CompletionLog.self, TrackerCategory.self,
            configurations: config
        )
        return container.mainContext
    }()))
}
