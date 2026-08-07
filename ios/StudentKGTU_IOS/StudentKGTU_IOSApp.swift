import SwiftUI
import BackgroundTasks

@main
struct StudentKGTU_IOSApp: App {
    @StateObject private var prefs = UserPreferences.shared
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var reminders = RemindersStore.shared
    @StateObject private var scheduleCache = ScheduleSessionCache.shared

    init() {
        // Start path monitor early for offline-first loads
        _ = NetworkMonitor.shared
        ScheduleUpdateChecker.registerBackgroundTask()
    }

    var body: some Scene {
        WindowGroup {
            RootContainer()
                .environmentObject(prefs)
                .environmentObject(theme)
                .environmentObject(reminders)
                .environmentObject(scheduleCache)
                .task {
                    ScheduleUpdateChecker.scheduleNextBackground()
                    await ScheduleUpdateChecker.check(notify: true)
                    await WidgetUpdater.updateNow()
                }
        }
    }
}

/// Brand splash (like Android) then main UI.
private struct RootContainer: View {
    @EnvironmentObject private var theme: ThemeManager
    @State private var showSplash = true

    var body: some View {
        ZStack {
            ContentView()
                .opacity(showSplash ? 0 : 1)

            if showSplash {
                BrandSplash(darkTheme: theme.isDark)
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.15) {
                withAnimation(.easeOut(duration: 0.35)) {
                    showSplash = false
                }
            }
        }
    }
}
