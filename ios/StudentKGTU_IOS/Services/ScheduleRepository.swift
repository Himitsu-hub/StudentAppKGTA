import Foundation

struct LoadMeta {
    let fromCache: Bool
    let updatedAt: Double
}

actor ScheduleRepository {
    static let shared = ScheduleRepository()

    /// Instant disk read for group picker (no network).
    nonisolated func getGroupsFromCacheOnly(faculty: String = FacultyCatalog.fae, course: Int) -> [String: [String]] {
        let key = "groups_\(faculty)_\(course)"
        return JSONCache.load([String: [String]].self, key: key)
            ?? JSONCache.load([String: [String]].self, key: "groups_\(course)")
            ?? [:]
    }

    func getGroups(faculty: String = FacultyCatalog.fae, course: Int) async -> [String: [String]] {
        let key = "groups_\(faculty)_\(course)"
        // Prefer disk immediately so VPN never blocks the picker.
        let cached = getGroupsFromCacheOnly(faculty: faculty, course: course)
        if !cached.isEmpty {
            return cached
        }
        if !NetworkMonitor.shared.isOnline {
            return [:]
        }
        do {
            let remote = try await APIClient.shared.groups(faculty: faculty, course: course)
            JSONCache.save(remote, key: key)
            return remote
        } catch {
            return getGroupsFromCacheOnly(faculty: faculty, course: course)
        }
    }

    /// Force network refresh of groups (pull-to-refresh / first visit).
    func refreshGroups(faculty: String = FacultyCatalog.fae, course: Int) async -> [String: [String]] {
        let key = "groups_\(faculty)_\(course)"
        do {
            let remote = try await APIClient.shared.groups(faculty: faculty, course: course)
            JSONCache.save(remote, key: key)
            return remote
        } catch {
            return getGroupsFromCacheOnly(faculty: faculty, course: course)
        }
    }

    /// Warm disk cache for all faculty×course group lists.
    func prefetchAllGroups() async {
        for fac in FacultyCatalog.all {
            for course in FacultyCatalog.courses(for: fac.id) {
                let cached = getGroupsFromCacheOnly(faculty: fac.id, course: course)
                if !cached.isEmpty { continue }
                _ = await refreshGroups(faculty: fac.id, course: course)
            }
        }
    }

    /// Offline-first schedule: disk cache first when offline; online tries server then cache.
    func getSchedule(
        faculty: String = FacultyCatalog.fae,
        course: Int,
        group: String,
        subgroup: String?
    ) async -> ScheduleResult {
        let week = DateUtils.currentWeekType()
        let weekKey = Self.cacheKey(faculty: faculty, course: course, group: group, subgroup: subgroup, week: week)
        let latestKey = "schedule_latest_\(faculty)_\(course)_\(group)_\(subgroup ?? "")"

        if !NetworkMonitor.shared.isOnline {
            return cachedResult(weekKey: weekKey, latestKey: latestKey)
                ?? emptyOffline(faculty: faculty, course: course, group: group, subgroup: subgroup, week: week)
        }

        do {
            var remote = try await APIClient.shared.schedule(
                faculty: faculty,
                course: course,
                group: group,
                subgroup: subgroup
            )
            remote.isOffline = false
            remote.fromCache = false
            let now = Date().timeIntervalSince1970 * 1000
            remote.updatedAtMillis = now
            JSONCache.save(remote, key: weekKey)
            JSONCache.save(remote, key: latestKey)
            JSONCache.saveMeta(key: weekKey, updatedAt: now)
            JSONCache.saveMeta(key: latestKey, updatedAt: now)
            return remote
        } catch {
            return cachedResult(weekKey: weekKey, latestKey: latestKey)
                ?? emptyOffline(faculty: faculty, course: course, group: group, subgroup: subgroup, week: week)
        }
    }

    /// Instant disk read (no network) for home screen first paint.
    nonisolated func getScheduleFromCacheOnly(
        faculty: String = FacultyCatalog.fae,
        course: Int,
        group: String,
        subgroup: String?
    ) -> ScheduleResult? {
        let week = DateUtils.currentWeekType()
        let weekKey = Self.cacheKey(faculty: faculty, course: course, group: group, subgroup: subgroup, week: week)
        let latestKey = "schedule_latest_\(faculty)_\(course)_\(group)_\(subgroup ?? "")"
        // Legacy key without faculty (pre-multi-faculty installs)
        let legacyWeek = "schedule_\(course)_\(group)_\(subgroup ?? "")_\(week)"
        let legacyLatest = "schedule_latest_\(course)_\(group)_\(subgroup ?? "")"
        return Self.cachedResult(weekKey: weekKey, latestKey: latestKey)
            ?? Self.cachedResult(weekKey: legacyWeek, latestKey: legacyLatest)
    }

    func scheduleByTeacher(query: String, day: String = "today") async -> TeacherScheduleResponse {
        if !NetworkMonitor.shared.isOnline {
            return TeacherScheduleResponse(query: query, day: day)
        }
        do {
            return try await APIClient.shared.scheduleByTeacher(query: query, day: day)
        } catch {
            return TeacherScheduleResponse(query: query, day: day)
        }
    }

    private nonisolated static func cacheKey(
        faculty: String,
        course: Int,
        group: String,
        subgroup: String?,
        week: String
    ) -> String {
        "schedule_\(faculty)_\(course)_\(group)_\(subgroup ?? "")_\(week)"
    }

    private nonisolated static func cachedResult(weekKey: String, latestKey: String) -> ScheduleResult? {
        if var cached = JSONCache.load(ScheduleResult.self, key: weekKey)
            ?? JSONCache.load(ScheduleResult.self, key: latestKey) {
            cached.isOffline = true
            cached.fromCache = true
            let meta = JSONCache.meta(key: weekKey)
            let metaLatest = JSONCache.meta(key: latestKey)
            cached.updatedAtMillis = max(meta, metaLatest)
            return cached
        }
        return nil
    }

    private func cachedResult(weekKey: String, latestKey: String) -> ScheduleResult? {
        Self.cachedResult(weekKey: weekKey, latestKey: latestKey)
    }

    private func emptyOffline(
        faculty: String,
        course: Int,
        group: String,
        subgroup: String?,
        week: String
    ) -> ScheduleResult {
        ScheduleResult(
            faculty: faculty,
            course: course,
            group: group,
            subgroup: subgroup,
            weekType: week,
            schedule: [],
            fromCache: false,
            isOffline: true,
            updatedAtMillis: 0
        )
    }
}
