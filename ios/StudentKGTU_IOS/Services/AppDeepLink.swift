import Foundation
import Combine
import UserNotifications

/// Deep-link target when user taps a notification.
@MainActor
final class AppDeepLink: ObservableObject {
    static let shared = AppDeepLink()

    @Published var pendingRoute: AppRoute?

    func openReminders() { pendingRoute = .reminders }
    func openSchedule() { pendingRoute = .schedule }
    func consume() { pendingRoute = nil }
}

/// Handles notification taps + shows banners while app is in foreground.
final class AppNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = AppNotificationDelegate()

    /// Show banner/sound even when the app is open.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge, .list])
    }

    /// Tap on notification → open the right screen.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let route = (info["route"] as? String)
            ?? response.notification.request.content.categoryIdentifier
        let id = response.notification.request.identifier
        Task { @MainActor in
            if route == "reminders" || route == "reminder" {
                AppDeepLink.shared.openReminders()
            } else if route == "schedule" || route == "schedule-update" || id.hasPrefix("schedule-update") {
                AppDeepLink.shared.openSchedule()
            } else if route == "news" || route == "news-update" || id.hasPrefix("news-update") {
                // stay on home
            } else if !id.isEmpty {
                // Reminder requests use reminder UUID as identifier
                AppDeepLink.shared.openReminders()
            }
            completionHandler()
        }
    }
}
