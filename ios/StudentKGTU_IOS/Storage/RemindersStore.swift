import Foundation
import Combine
import UserNotifications

@MainActor
final class RemindersStore: ObservableObject {
    static let shared = RemindersStore()
    private let key = "reminders_v1"

    @Published private(set) var reminders: [Reminder] = []

    init() { load() }

    func load() {
        guard let data = UserDefaults.standard.data(forKey: key),
              let list = try? JSONDecoder().decode([Reminder].self, from: data) else {
            reminders = []
            return
        }
        reminders = list.sorted { $0.dateTimeMillis < $1.dateTimeMillis }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(reminders) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    func save(id: String, text: String, dateTimeMillis: Double) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if let idx = reminders.firstIndex(where: { $0.id == id }) {
            reminders[idx].text = trimmed
            reminders[idx].dateTimeMillis = dateTimeMillis
        } else {
            reminders.append(Reminder(id: id, text: trimmed, dateTimeMillis: dateTimeMillis))
        }
        reminders.sort { $0.dateTimeMillis < $1.dateTimeMillis }
        persist()
        scheduleNotification(id: id, text: trimmed, at: dateTimeMillis)
    }

    func delete(id: String) {
        reminders.removeAll { $0.id == id }
        persist()
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
    }

    func requestPermission() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    private func scheduleNotification(id: String, text: String, at millis: Double) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        let content = UNMutableNotificationContent()
        content.title = "Напоминание"
        content.body = text
        content.sound = .default
        let date = Date(timeIntervalSince1970: millis / 1000)
        guard date > Date() else { return }
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
        center.add(req)
    }
}
