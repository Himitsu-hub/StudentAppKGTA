import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
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
}
