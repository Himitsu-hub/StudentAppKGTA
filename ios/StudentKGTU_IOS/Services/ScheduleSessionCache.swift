import Foundation
import Combine

/// In-memory + disk offline cache for Schedule screen.
@MainActor
final class ScheduleSessionCache: ObservableObject {
    static let shared = ScheduleSessionCache()

    @Published private(set) var weekType: String = DateUtils.currentWeekType()
    @Published private(set) var calendarWeekType: String = DateUtils.currentWeekType()
    @Published private(set) var schedule: [ScheduleDay] = []
    @Published private(set) var groups: [String: [String]] = [:]
    @Published private(set) var usingCached = false
    @Published private(set) var updatedLabel: String?
    @Published private(set) var error: String?
    @Published private(set) var isLoading = false

    private var loadedKey: String?
    private var lastNetworkAt: Date?
    /// Bumps on every week/group load so stale async responses are ignored.
    private var loadEpoch: Int = 0

    private func key(faculty: String, course: Int, group: String?, subgroup: String?, week: String) -> String {
        "\(faculty)|\(course)|\(group ?? "")|\(subgroup ?? "")|\(week)"
    }

    func hasData(for prefs: UserPreferences) -> Bool {
        let k = key(
            faculty: prefs.faculty,
            course: prefs.course,
            group: prefs.group,
            subgroup: prefs.subgroup,
            week: weekType
        )
        return loadedKey == k && !schedule.isEmpty
    }

    func load(prefs: UserPreferences, force: Bool = false) async {
        guard let group = prefs.group, !group.isEmpty else {
            schedule = []
            error = "Выберите группу"
            loadedKey = nil
            return
        }

        calendarWeekType = DateUtils.currentWeekType()
        let week = weekType.isEmpty ? calendarWeekType : weekType
        let epoch = loadEpoch
        let k = key(
            faculty: prefs.faculty,
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup,
            week: week
        )
        let hasExisting = loadedKey == k && !schedule.isEmpty
        let recent = lastNetworkAt.map { Date().timeIntervalSince($0) < 90 } ?? false

        if !force && hasExisting && recent {
            return
        }

        // Disk first for the *requested* week only.
        if let disk = ScheduleRepository.shared.getScheduleFromCacheOnly(
            faculty: prefs.faculty,
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup,
            weekType: week
        ), !disk.schedule.isEmpty {
            guard epoch == loadEpoch, weekType == week || weekType.isEmpty else { return }
            schedule = disk.schedule
            weekType = week
            usingCached = true
            updatedLabel = TimeFormat.updatedAtLabel(millis: disk.updatedAtMillis)
            loadedKey = k
            isLoading = false
        }

        if !NetworkMonitor.shared.isOnline {
            error = schedule.isEmpty ? "Нет сети и нет сохранённого расписания" : nil
            usingCached = !schedule.isEmpty
            isLoading = false
            return
        }

        if schedule.isEmpty {
            isLoading = true
        }
        error = nil
        defer { isLoading = false }

        groups = await ScheduleRepository.shared.getGroups(faculty: prefs.faculty, course: prefs.course)
        let result = await ScheduleRepository.shared.getSchedule(
            faculty: prefs.faculty,
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup,
            weekType: week
        )
        // Ignore stale responses from a previous week toggle.
        guard epoch == loadEpoch else { return }
        guard weekType == week || weekType.isEmpty else { return }

        if !result.schedule.isEmpty || schedule.isEmpty {
            schedule = result.schedule
        }
        weekType = week
        usingCached = result.isOffline
        updatedLabel = TimeFormat.updatedAtLabel(millis: result.updatedAtMillis) ?? updatedLabel
        loadedKey = k
        if !result.isOffline {
            lastNetworkAt = Date()
        }
        if schedule.isEmpty && result.isOffline {
            error = "Нет сети и нет сохранённого расписания"
        }
        await WidgetUpdater.updateNow()
    }

    func toggleWeekType(prefs: UserPreferences) async {
        let other = (weekType == "Числитель") ? "Знаменатель" : "Числитель"
        loadEpoch += 1
        weekType = other
        invalidate()
        // Keep current grid visible until the new week arrives (no blank flash).
        isRefreshingSoft()
        await load(prefs: prefs, force: true)
    }

    private func isRefreshingSoft() {
        isLoading = false
    }

    func invalidate() {
        loadedKey = nil
        lastNetworkAt = nil
    }

    func setGroups(_ value: [String: [String]]) {
        groups = value
    }
}
