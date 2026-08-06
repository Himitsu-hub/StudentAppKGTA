import Foundation
import Combine

/// In-memory session cache so Schedule screen does not flash-load every open (Android ViewModel-like).
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

    /// True if we already have data for this selection in this session.
    func hasData(for prefs: UserPreferences) -> Bool {
        let k = key(course: prefs.course, group: prefs.group, subgroup: prefs.subgroup)
        return loadedKey == k && !schedule.isEmpty
    }

    /// Load: show spinner only if nothing to display; otherwise silent refresh.
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

        // Skip network entirely if just opened again within 90s with same selection
        if !force && hasExisting && recent {
            return
        }

        // Spinner only on first open for this selection
        if !hasExisting {
            isLoading = true
        }
        error = nil
        defer { isLoading = false }

        // Groups (cached by repository too)
        groups = await ScheduleRepository.shared.getGroups(course: prefs.course)

        let result = await ScheduleRepository.shared.getSchedule(
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup
        )
        schedule = result.schedule
        weekType = result.weekType.isEmpty ? DateUtils.currentWeekType() : result.weekType
        usingCached = result.isOffline
        updatedLabel = TimeFormat.updatedAtLabel(millis: result.updatedAtMillis)
        loadedKey = k
        if !result.isOffline {
            lastNetworkAt = Date()
        }
        if result.schedule.isEmpty && result.isOffline {
            error = "Нет сети и нет сохранённого расписания"
        }
        await WidgetUpdater.updateNow()
    }

    func invalidate() {
        loadedKey = nil
        lastNetworkAt = nil
    }
}
