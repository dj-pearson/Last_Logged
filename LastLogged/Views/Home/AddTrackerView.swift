import SwiftUI
import SwiftData

struct AddTrackerView: View {
    @Environment(\.dismiss) private var dismiss
    let viewModel: HomeViewModel

    @State private var name = ""
    @State private var selectedCategoryId: UUID?
    @State private var hasReminder = false
    @State private var reminderValue = 30
    @State private var reminderUnit: ReminderUnit = .days
    @State private var selectedIcon = "checkmark.circle"
    @State private var showingIconPicker = false

    enum ReminderUnit: String, CaseIterable {
        case days = "Days"
        case weeks = "Weeks"
        case months = "Months"

        func toDays(_ value: Int) -> Int {
            switch self {
            case .days: return value
            case .weeks: return value * 7
            case .months: return value * 30
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("e.g. Oil Change, Haircut", text: $name)
                }

                Section("Category") {
                    Picker("Category", selection: $selectedCategoryId) {
                        ForEach(viewModel.categories, id: \.id) { category in
                            Label(category.name, systemImage: category.iconName)
                                .tag(Optional(category.id))
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section("Reminder") {
                    Toggle("Set reminder interval", isOn: $hasReminder)
                    if hasReminder {
                        HStack {
                            TextField("Value", value: $reminderValue, format: .number)
                                .keyboardType(.numberPad)
                                .frame(width: 60)
                            Picker("Unit", selection: $reminderUnit) {
                                ForEach(ReminderUnit.allCases, id: \.self) { unit in
                                    Text(unit.rawValue).tag(unit)
                                }
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                }

                Section("Icon") {
                    Button {
                        showingIconPicker = true
                    } label: {
                        HStack {
                            Image(systemName: selectedIcon)
                                .font(.title2)
                                .foregroundStyle(.accent)
                                .frame(width: 32)
                            Text(selectedIcon)
                                .foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("New Tracker")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { saveTracker() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if selectedCategoryId == nil {
                    selectedCategoryId = viewModel.categories.first?.id
                }
            }
            .sheet(isPresented: $showingIconPicker) {
                SFSymbolPickerView(selectedIcon: $selectedIcon)
            }
        }
    }

    private func saveTracker() {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty, let categoryId = selectedCategoryId else { return }
        let intervalDays = hasReminder ? reminderUnit.toDays(reminderValue) : nil
        viewModel.createItem(
            name: trimmedName,
            categoryId: categoryId,
            reminderIntervalDays: intervalDays,
            iconName: selectedIcon
        )
        dismiss()
    }
}

// MARK: - SF Symbol Picker

struct SFSymbolPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedIcon: String
    @State private var searchText = ""

    private static let commonSymbols: [String] = [
        // General
        "checkmark.circle", "star.fill", "heart.fill", "bell.fill", "flag.fill",
        "bookmark.fill", "tag.fill", "pin.fill", "bolt.fill", "flame.fill",
        // Home
        "house.fill", "lightbulb.fill", "fan.fill", "drop.fill", "wrench.fill",
        "hammer.fill", "paintbrush.fill", "leaf.fill", "trash.fill", "key.fill",
        // Health
        "heart.text.square.fill", "cross.case.fill", "pills.fill", "bandage.fill",
        "stethoscope", "figure.walk", "figure.run", "dumbbell.fill", "bed.double.fill",
        // Car
        "car.fill", "fuelpump.fill", "gauge.open.with.lines.needle.33percent",
        "engine.combustion.fill", "oilcan.fill", "tire.fill",
        // Personal
        "person.fill", "scissors", "tshirt.fill", "comb.fill", "shower.fill",
        "washer.fill", "sparkles",
        // Social
        "person.2.fill", "phone.fill", "envelope.fill", "gift.fill", "party.popper.fill",
        "hand.wave.fill", "bubble.left.fill", "calendar",
        // Pet
        "pawprint.fill", "hare.fill", "fish.fill", "ant.fill",
        // Food
        "fork.knife", "cup.and.saucer.fill", "takeoutbag.and.cup.and.straw.fill",
        // Nature
        "sun.max.fill", "cloud.fill", "snowflake", "wind",
        // Tech
        "desktopcomputer", "laptopcomputer", "iphone", "printer.fill", "wifi",
        // Other
        "doc.fill", "folder.fill", "tray.fill", "archivebox.fill", "clock.fill",
        "alarm.fill", "stopwatch.fill", "camera.fill", "map.fill", "globe",
    ]

    private var filteredSymbols: [String] {
        if searchText.isEmpty {
            return Self.commonSymbols
        }
        let query = searchText.lowercased()
        return Self.commonSymbols.filter { $0.lowercased().contains(query) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 5), spacing: 16) {
                    ForEach(filteredSymbols, id: \.self) { symbol in
                        Button {
                            selectedIcon = symbol
                            dismiss()
                        } label: {
                            Image(systemName: symbol)
                                .font(.title2)
                                .frame(width: 44, height: 44)
                                .background(
                                    selectedIcon == symbol
                                        ? Color.accentColor.opacity(0.2)
                                        : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 8)
                                )
                                .foregroundStyle(selectedIcon == symbol ? .accent : .primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
            }
            .searchable(text: $searchText, prompt: "Search symbols")
            .navigationTitle("Choose Icon")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    AddTrackerView(viewModel: HomeViewModel(modelContext: {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try! ModelContainer(
            for: TrackerItem.self, CompletionLog.self, TrackerCategory.self,
            configurations: config
        )
        return container.mainContext
    }()))
}
