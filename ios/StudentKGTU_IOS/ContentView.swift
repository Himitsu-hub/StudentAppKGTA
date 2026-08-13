import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var deepLink: AppDeepLink
    @State private var path = NavigationPath()

    var body: some View {
        Group {
            if prefs.onboardingDone && prefs.hasGroup {
                NavigationStack(path: $path) {
                    HomeView(path: $path)
                        .navigationDestination(for: AppRoute.self) { route in
                            switch route {
                            case .schedule:
                                ScheduleView()
                            case .teachers:
                                TeachersView()
                            case .teacherDetail:
                                // Detail opens inside TeachersView — should not reach here
                                EmptyView()
                            case .reminders:
                                RemindersView()
                            case .campus:
                                CampusView()
                            }
                        }
                }
                .onChange(of: deepLink.pendingRoute) { _, route in
                    guard let route else { return }
                    // Reset stack then push target (from notification tap)
                    path = NavigationPath()
                    path.append(route)
                    deepLink.consume()
                }
                .onAppear {
                    if let route = deepLink.pendingRoute {
                        path = NavigationPath()
                        path.append(route)
                        deepLink.consume()
                    }
                }
            } else {
                OnboardingView()
            }
        }
        .preferredColorScheme(theme.preferredScheme)
        .tint(AppColors.blueKGTA)
    }
}

#Preview {
    ContentView()
        .environmentObject(UserPreferences.shared)
        .environmentObject(ThemeManager.shared)
        .environmentObject(RemindersStore.shared)
        .environmentObject(ScheduleSessionCache.shared)
        .environmentObject(AppDeepLink.shared)
}
