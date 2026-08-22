import SwiftUI
import BackgroundTasks
import UserNotifications

@main
struct StudentKGTU_IOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var prefs = UserPreferences.shared
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var reminders = RemindersStore.shared
    @StateObject private var scheduleCache = ScheduleSessionCache.shared
    @StateObject private var deepLink = AppDeepLink.shared

    init() {
        // Start path monitor early for offline-first loads
        _ = NetworkMonitor.shared
        UNUserNotificationCenter.current().delegate = AppNotificationDelegate.shared
        ScheduleUpdateChecker.registerBackgroundTask()
        NewsUpdateChecker.registerBackgroundTask()
    }

    var body: some Scene {
        WindowGroup {
            RootContainer()
                .environmentObject(prefs)
                .environmentObject(theme)
                .environmentObject(reminders)
                .environmentObject(scheduleCache)
                .environmentObject(deepLink)
                .task {
                    ScheduleUpdateChecker.scheduleNextBackground()
                    NewsUpdateChecker.scheduleNextBackground()
                    await ScheduleUpdateChecker.check(notify: true)
                    await NewsUpdateChecker.check(notify: true)
                    await WidgetUpdater.updateNow()
                }
                .onChange(of: scenePhase) { _, phase in
                    switch phase {
                    case .active:
                        Task {
                            NewsUpdateChecker.scheduleNextBackground()
                            ScheduleUpdateChecker.scheduleNextBackground()
                            _ = await NewsUpdateChecker.check(notify: true)
                            _ = await ScheduleUpdateChecker.check(notify: true)
                        }
                    case .background, .inactive:
                        // Re-arm BG refresh + one last poll while still allowed
                        NewsUpdateChecker.checkOnEnterBackground()
                        ScheduleUpdateChecker.scheduleNextBackground()
                    @unknown default:
                        break
                    }
                }
                // Foreground poll ~20s while app is open — news feel near-instant
                .task {
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 20 * 1_000_000_000)
                        guard scenePhase == .active else { continue }
                        _ = await NewsUpdateChecker.check(notify: true)
                        _ = await ScheduleUpdateChecker.check(notify: true)
                    }
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
