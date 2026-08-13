import Foundation
import UserNotifications
import BackgroundTasks

/// Polls /api/news-updates and posts a local notification when the feed fingerprint changes.
enum NewsUpdateChecker {
    static let bgTaskId = "Univers.StudentKGTU-IOS.news-update"
    private static let versionKey = "news_feed_version_v1"

    struct UpdatesResponse: Decodable {
        let version: String?
        let fingerprint: String?
        let latestTitle: String?
        let latestDate: String?
        let latestUrl: String?
        let count: Int?
        let updatedAt: Double?
    }

    @discardableResult
    static func check(notify: Bool = true) async -> Bool {
        do {
            guard let remote = try await fetchUpdates() else { return false }
            let remoteVersion = (remote.version?.isEmpty == false ? remote.version : remote.fingerprint) ?? ""
            guard !remoteVersion.isEmpty else { return false }

            let known = UserDefaults.standard.string(forKey: versionKey) ?? ""
            if known.isEmpty {
                UserDefaults.standard.set(remoteVersion, forKey: versionKey)
                return false
            }
            if known != remoteVersion {
                UserDefaults.standard.set(remoteVersion, forKey: versionKey)
                if notify {
                    let title = (remote.latestTitle?.isEmpty == false)
                        ? (remote.latestTitle ?? "Появилась новая запись")
                        : "Появилась новая запись"
                    let date = remote.latestDate ?? ""
                    await showNotification(title: title, date: date)
                }
                return true
            }
            return false
        } catch {
            return false
        }
    }

    private static func fetchUpdates() async throws -> UpdatesResponse? {
        guard let url = URL(string: "https://apistudentkgtu.ru/api/news-updates") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        req.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            return nil
        }
        return try JSONDecoder().decode(UpdatesResponse.self, from: data)
    }

    private static func showNotification(title: String, date: String) async {
        let center = UNUserNotificationCenter.current()
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        let content = UNMutableNotificationContent()
        content.title = "Новая новость КГТУ"
        if date.isEmpty {
            content.body = title
        } else {
            content.body = "\(date) · \(title)"
        }
        content.sound = .default
        let req = UNNotificationRequest(
            identifier: "news-update-\(Date().timeIntervalSince1970)",
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
                _ = await check(notify: true)
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func scheduleNextBackground() {
        let req = BGAppRefreshTaskRequest(identifier: bgTaskId)
        // Ask iOS for ~5 min; system may delay, but sooner is better for news.
        req.earliestBeginDate = Date(timeIntervalSinceNow: 5 * 60)
        try? BGTaskScheduler.shared.submit(req)
    }
}
