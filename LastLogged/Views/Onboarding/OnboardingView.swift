import SwiftUI
import SwiftData

struct OnboardingView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: OnboardingViewModel?
    @State private var showingPaywall = false

    var body: some View {
        Group {
            if let viewModel {
                onboardingContent(viewModel: viewModel)
            } else {
                ProgressView()
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = OnboardingViewModel(modelContext: modelContext)
            }
        }
    }

    private func onboardingContent(viewModel: OnboardingViewModel) -> some View {
        TabView(selection: Bindable(viewModel).currentPage) {
            welcomeScreen(viewModel: viewModel)
                .tag(0)

            categorySelectionScreen(viewModel: viewModel)
                .tag(1)

            widgetPromptScreen(viewModel: viewModel)
                .tag(2)
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .indexViewStyle(.page(backgroundDisplayMode: .always))
        .sheet(isPresented: $showingPaywall) {
            PaywallView(isHardPaywall: false)
        }
    }

    // MARK: - Screen 1: Welcome

    private func welcomeScreen(viewModel: OnboardingViewModel) -> some View {
        VStack(spacing: 32) {
            Spacer()

            Image(systemName: "clock.badge.checkmark.fill")
                .font(.system(size: 80))
                .foregroundStyle(.accent)

            VStack(spacing: 12) {
                Text("Last Logged")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("When did I last...?")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .italic()
            }

            VStack(alignment: .leading, spacing: 16) {
                valueRow(icon: "hand.tap.fill", text: "One-tap logging for recurring tasks")
                valueRow(icon: "clock.fill", text: "See how long since you last did it")
                valueRow(icon: "bell.badge.fill", text: "Smart reminders when items are due")
                valueRow(icon: "square.grid.2x2.fill", text: "Home screen widgets at a glance")
            }
            .padding(.horizontal, 32)

            Spacer()

            Button {
                withAnimation {
                    viewModel.currentPage = 1
                }
            } label: {
                Text("Get Started")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
        }
    }

    private func valueRow(icon: String, text: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(.accent)
                .frame(width: 28)
            Text(text)
                .font(.subheadline)
        }
    }

    // MARK: - Screen 2: Category Selection

    private func categorySelectionScreen(viewModel: OnboardingViewModel) -> some View {
        VStack(spacing: 24) {
            VStack(spacing: 8) {
                Text("Choose Your Categories")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Select the categories you want to track. You can always change these later.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            .padding(.top, 32)

            ScrollView {
                VStack(spacing: 12) {
                    ForEach(viewModel.categories) { category in
                        categoryRow(category: category, viewModel: viewModel)
                    }
                }
                .padding(.horizontal, 24)
            }

            Button {
                withAnimation {
                    viewModel.currentPage = 2
                }
            } label: {
                Text("Continue")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
        }
    }

    private func categoryRow(category: TrackerCategory, viewModel: OnboardingViewModel) -> some View {
        let isSelected = viewModel.isCategorySelected(category)
        return Button {
            viewModel.toggleCategory(category)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: category.iconName)
                    .font(.title3)
                    .foregroundStyle(Color(hex: category.colorHex) ?? .primary)
                    .frame(width: 32)

                VStack(alignment: .leading, spacing: 2) {
                    Text(category.name)
                        .font(.headline)
                    let templateCount = CategoryTemplates.templates(for: category.name).count
                    Text("\(templateCount) starter items")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? .accent : .secondary)
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
    }

    // MARK: - Screen 3: Widget Prompt

    private func widgetPromptScreen(viewModel: OnboardingViewModel) -> some View {
        VStack(spacing: 32) {
            Spacer()

            Image(systemName: "square.grid.2x2.fill")
                .font(.system(size: 72))
                .foregroundStyle(.accent)

            VStack(spacing: 12) {
                Text("Add a Widget")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("See your most overdue items right on your home screen without opening the app.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            VStack(alignment: .leading, spacing: 16) {
                instructionRow(step: "1", text: "Long-press your home screen")
                instructionRow(step: "2", text: "Tap the + button in the top corner")
                instructionRow(step: "3", text: "Search for \"Last Logged\"")
                instructionRow(step: "4", text: "Choose a widget size and tap Add")
            }
            .padding(.horizontal, 32)

            Spacer()

            Button {
                viewModel.completeOnboarding()
                showingPaywall = true
            } label: {
                Text("Get Started")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
        }
    }

    private func instructionRow(step: String, text: String) -> some View {
        HStack(spacing: 14) {
            Text(step)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(Circle().fill(.accent))
            Text(text)
                .font(.subheadline)
        }
    }
}

#Preview {
    OnboardingView()
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
