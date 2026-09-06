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

        // Always try disk first (even if session already had data)
        if let disk = ScheduleRepository.shared.getScheduleFromCacheOnly(
            faculty: prefs.faculty,
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup,
            weekType: week
        ), !disk.schedule.isEmpty {
            schedule = disk.schedule
            weekType = disk.weekType.isEmpty ? week : disk.weekType
            usingCached = true
            updatedLabel = TimeFormat.updatedAtLabel(millis: disk.updatedAtMillis)
            loadedKey = k
            isLoading = false
        }

        // Offline: stop here with cache (or error if empty)
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
        if !result.schedule.isEmpty || schedule.isEmpty {
            schedule = result.schedule
        }
        weekType = result.weekType.isEmpty ? week : result.weekType
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
        weekType = (weekType == "Числитель") ? "Знаменатель" : "Числитель"
        invalidate()
        schedule = []
        await load(prefs: prefs, force: true)
    }

    func invalidate() {
        loadedKey = nil
        lastNetworkAt = nil
    }

    func setGroups(_ value: [String: [String]]) {
        groups = value
    }
}
