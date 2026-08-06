import Foundation

enum AppGroup {
    /// Shared between app and widgets
    static let id = "group.Univers.StudentKGTU-IOS"
    static var defaults: UserDefaults {
        UserDefaults(suiteName: id) ?? .standard
    }
}

struct WidgetSnapshot: Codable, Equatable {
    var weekLine: String
    var groupLine: String
    var label: String
    var subject: String
    var details: String
    var hint: String

    static let placeholder = WidgetSnapshot(
        weekLine: "Неделя: —",
        groupLine: "",
        label: "Следующая пара",
        subject: "Откройте приложение",
        details: "Выберите группу в расписании",
        hint: "Нажмите, чтобы открыть"
    )

    private static let key = "widget_snapshot_v1"

    static func save(_ snap: WidgetSnapshot) {
        if let data = try? JSONEncoder().encode(snap) {
            AppGroup.defaults.set(data, forKey: key)
            AppGroup.defaults.synchronize()
        }
    }

    static func load() -> WidgetSnapshot {
        guard let data = AppGroup.defaults.data(forKey: key),
              let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) else {
            return .placeholder
        }
        return snap
    }
}
