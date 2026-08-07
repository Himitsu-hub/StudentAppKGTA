import Foundation
import Combine

/// In-memory + disk offline cache for Schedule screen.
@MainActor
final class ScheduleSessionCache: ObservableObject {
    static let shared = ScheduleSessionCache()

    @Published private(set) var weekType: String = DateUtils.currentWeekType()
    @Published private(set) var schedule: [ScheduleDay] = []
    @Published private(set) var groups: [String: [String]] = [:]
    @Published private(set) var usingCached = false
    @Published private(set) var updatedLabel: String?
    @Published private(set) var error: String?
    @Published private(set) var isLoading = false

    private var loadedKey: String?
    private var lastNetworkAt: Date?

    private func key(course: Int, group: String?, subgroup: String?) -> String {
        "\(course)|\(group ?? "")|\(subgroup ?? "")"
    }

    func hasData(for prefs: UserPreferences) -> Bool {
        let k = key(course: prefs.course, group: prefs.group, subgroup: prefs.subgroup)
        return loadedKey == k && !schedule.isEmpty
    }

    func load(prefs: UserPreferences, force: Bool = false) async {
        guard let group = prefs.group, !group.isEmpty else {
            schedule = []
            error = "Выберите группу"
            loadedKey = nil
            return
        }

        let k = key(course: prefs.course, group: group, subgroup: prefs.subgroup)
        let hasExisting = loadedKey == k && !schedule.isEmpty
        let recent = lastNetworkAt.map { Date().timeIntervalSince($0) < 90 } ?? false

        if !force && hasExisting && recent {
            return
        }

        // Instant disk paint
        if !hasExisting {
            if let disk = ScheduleRepository.shared.getScheduleFromCacheOnly(
                course: prefs.course,
                group: group,
                subgroup: prefs.subgroup
            ), !disk.schedule.isEmpty {
                schedule = disk.schedule
                weekType = disk.weekType.isEmpty ? DateUtils.currentWeekType() : disk.weekType
                usingCached = true
                updatedLabel = TimeFormat.updatedAtLabel(millis: disk.updatedAtMillis)
                loadedKey = k
            }
        }

        // Offline: stop here with cache (or error if empty)
        if !NetworkMonitor.shared.isOnline {
            isLoading = schedule.isEmpty
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

        groups = await ScheduleRepository.shared.getGroups(course: prefs.course)
        let result = await ScheduleRepository.shared.getSchedule(
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup
        )
        if !result.schedule.isEmpty || schedule.isEmpty {
            schedule = result.schedule
        }
        weekType = result.weekType.isEmpty ? DateUtils.currentWeekType() : result.weekType
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

    func invalidate() {
        loadedKey = nil
        lastNetworkAt = nil
    }
}
