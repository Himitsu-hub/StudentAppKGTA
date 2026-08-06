import Foundation

/// Shared storage for widgets.
/// Free Personal Team cannot use App Groups — fall back to standard UserDefaults.
/// (Widgets may show last-known / placeholder until paid team + App Groups.)
enum AppGroup {
    static let id = "group.Univers.StudentKGTU-IOS"

    static var defaults: UserDefaults {
        // Prefer app group when available (paid Developer); else standard.
        if let suite = UserDefaults(suiteName: id),
           suite.object(forKey: "app_group_probe") != nil || true {
            // On free team suiteName often returns non-nil but data is not shared.
            // Always also write to standard; widget reads standard if group empty.
            return suite
        }
        return .standard
    }

    static var storage: UserDefaults {
        // Reliable for free team: standard. When App Groups work, both app & widget use suite.
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
        guard let data = try? JSONEncoder().encode(snap) else { return }
        // Write both places so paid App Group OR free standard can work
        UserDefaults.standard.set(data, forKey: key)
        if let suite = UserDefaults(suiteName: AppGroup.id) {
            suite.set(data, forKey: key)
            suite.synchronize()
        }
        UserDefaults.standard.synchronize()
    }

    static func load() -> WidgetSnapshot {
        if let suite = UserDefaults(suiteName: AppGroup.id),
           let data = suite.data(forKey: key),
           let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snap
        }
        if let data = UserDefaults.standard.data(forKey: key),
           let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snap
        }
        return .placeholder
    }
}
