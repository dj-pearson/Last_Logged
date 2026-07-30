import SwiftUI

struct ContentView: View {
    @AppStorage("onboardingCompleted") private var onboardingCompleted = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var biometricService = BiometricService.shared

    var body: some View {
        ZStack {
            if onboardingCompleted {
                HomeView()
            } else {
                OnboardingView()
            }

            if biometricService.shouldShowLockScreen && onboardingCompleted {
                lockScreen
            }
        }
        .onOpenURL { url in
            // Universal Links and the widget's lastlogged:// scheme both land
            // here. Unrecognised URLs are dropped by the router.
            DeepLinkRouter.shared.handle(url)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                biometricService.lock()
            } else if newPhase == .active && biometricService.shouldShowLockScreen {
                Task {
                    await biometricService.authenticate()
                }
            }
        }
    }

    // MARK: - Lock Screen

    private var lockScreen: some View {
        ZStack {
            Color(.systemBackground)
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Image(systemName: biometricService.biometricIcon)
                    .font(.system(size: 64))
                    .foregroundStyle(.accent)

                Text("Last Logged is Locked")
                    .font(.title2.bold())

                Text("Authenticate to access your data")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Button {
                    Task {
                        await biometricService.authenticate()
                    }
                } label: {
                    Label("Unlock with \(biometricService.biometricName)", systemImage: biometricService.biometricIcon)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 48)
                .accessibilityLabel("Unlock with \(biometricService.biometricName)")
            }
        }
        .transition(.opacity)
    }
}

#Preview {
    ContentView()
}
