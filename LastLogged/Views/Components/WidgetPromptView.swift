import SwiftUI

struct WidgetPromptView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("widgetPromptDismissed") private var widgetPromptDismissed = false

    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "square.grid.2x2.fill")
                .font(.system(size: 56))
                .foregroundStyle(.accent)

            Text("Add the Widget")
                .font(.title2)
                .fontWeight(.bold)

            Text("See your most overdue items and log completions right from your home screen — no need to open the app.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            instructionsCard

            Spacer()

            Button {
                widgetPromptDismissed = true
                dismiss()
            } label: {
                Text("Got It!")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityHint("Dismiss this prompt")

            Button {
                widgetPromptDismissed = true
                dismiss()
            } label: {
                Text("Maybe Later")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(minHeight: 44)

            Spacer().frame(height: 8)
        }
        .padding()
    }

    private var instructionsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            instructionRow(step: "1", text: "Long-press your home screen")
            instructionRow(step: "2", text: "Tap the + button in the top corner")
            instructionRow(step: "3", text: "Search for \"Last Logged\"")
            instructionRow(step: "4", text: "Choose a widget size and tap Add")
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func instructionRow(step: String, text: String) -> some View {
        HStack(spacing: 12) {
            Text(step)
                .font(.caption.bold())
                .frame(width: 24, height: 24)
                .background(Color.accentColor)
                .foregroundStyle(.white)
                .clipShape(Circle())
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Step \(step): \(text)")
    }
}

#Preview {
    WidgetPromptView()
}
