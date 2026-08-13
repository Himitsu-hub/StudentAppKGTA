import Foundation
import UserNotifications
import BackgroundTasks

enum ScheduleUpdateChecker {
    static let bgTaskId = "Univers.StudentKGTU-IOS.schedule-update"
    private static let versionKeyPrefix = "schedule_version_"

    struct CourseUpdate: Decodable {
        let course: Int
        let version: String?
        let updatedAt: String?
        let available: Bool?
    }

    struct UpdatesResponse: Decodable {
        let courses: [CourseUpdate]
        let fingerprint: String?
    }

    /// Poll /api/schedule-updates — notify if Excel for selected course changed.
    @discardableResult
    static func check(notify: Bool = true) async -> Bool {
        do {
            let course = await MainActor.run { UserPreferences.shared.course }
            guard let updates = try await fetchUpdates() else { return false }
            guard let remote = updates.courses.first(where: { $0.course == course }) else { return false }
            let remoteVersion = remote.version ?? ""
            guard !remoteVersion.isEmpty, remote.available == true else { return false }

            let key = versionKeyPrefix + "\(course)"
            let known = UserDefaults.standard.string(forKey: key) ?? ""
            if known.isEmpty {
                UserDefaults.standard.set(remoteVersion, forKey: key)
                return false
            }
            if known != remoteVersion {
                UserDefaults.standard.set(remoteVersion, forKey: key)
                if notify {
                    await showNotification(course: course)
                }
                return true
            }
            return false
        } catch {
            return false
        }
    }

    private static func fetchUpdates() async throws -> UpdatesResponse? {
        guard let url = URL(string: "https://apistudentkgtu.ru/api/schedule-updates") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        req.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            return nil
        }
        return try JSONDecoder().decode(UpdatesResponse.self, from: data)
    }

    private static func showNotification(course: Int) async {
        let center = UNUserNotificationCenter.current()
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        let content = UNMutableNotificationContent()
        content.title = "Расписание обновлено"
        content.body = "На сервере новое расписание для \(course) курса. Откройте приложение."
        content.sound = .default
        content.userInfo = ["route": "schedule", "course": course]
        content.categoryIdentifier = "schedule"
        let req = UNNotificationRequest(
            identifier: "schedule-update-\(course)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: nil
        )
        try? await center.add(req)
    }

    static func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: bgTaskId, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            scheduleNextBackground()
            let work = Task {
                let changed = await check(notify: true)
                await WidgetUpdater.updateNow()
                task.setTaskCompleted(success: true)
                _ = changed
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func scheduleNextBackground() {
        let req = BGAppRefreshTaskRequest(identifier: bgTaskId)
        // Ask iOS for ~2 min; system may delay, but sooner is better after Excel upload.
        req.earliestBeginDate = Date(timeIntervalSinceNow: 2 * 60)
        try? BGTaskScheduler.shared.submit(req)
    }
}
