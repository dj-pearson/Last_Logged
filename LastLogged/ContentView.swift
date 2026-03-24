import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 60))
                    .foregroundStyle(.accent)
                Text("Last Logged")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Text("Track your recurring life events")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .navigationTitle("Last Logged")
        }
    }
}

#Preview {
    ContentView()
}
