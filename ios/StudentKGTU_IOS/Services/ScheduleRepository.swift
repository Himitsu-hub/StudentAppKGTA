import Foundation

struct LoadMeta {
    let fromCache: Bool
    let updatedAt: Double
}

actor ScheduleRepository {
    static let shared = ScheduleRepository()

    func getGroups(course: Int) async -> [String: [String]] {
        let key = "groups_\(course)"
        do {
            let remote = try await APIClient.shared.groups(course: course)
            JSONCache.save(remote, key: key)
            return remote
        } catch {
            return JSONCache.load([String: [String]].self, key: key) ?? [:]
        }
    }

    func getSchedule(course: Int, group: String, subgroup: String?) async -> ScheduleResult {
        let week = DateUtils.currentWeekType()
        let key = "schedule_\(course)_\(group)_\(subgroup ?? "")_\(week)"
        do {
            var remote = try await APIClient.shared.schedule(course: course, group: group, subgroup: subgroup)
            remote.isOffline = false
            remote.fromCache = false
            let now = Date().timeIntervalSince1970 * 1000
            remote.updatedAtMillis = now
            JSONCache.save(remote, key: key)
            JSONCache.saveMeta(key: key, updatedAt: now)
            return remote
        } catch {
            if var cached = JSONCache.load(ScheduleResult.self, key: key) {
                cached.isOffline = true
                cached.fromCache = true
                cached.updatedAtMillis = JSONCache.meta(key: key)
                return cached
            }
            return ScheduleResult(
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
}
