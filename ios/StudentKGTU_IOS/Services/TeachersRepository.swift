import Foundation

actor TeachersRepository {
    static let shared = TeachersRepository()

    func getTeachers() async -> (teachers: [Teacher], fromCache: Bool) {
        let key = "teachers"

        if !NetworkMonitor.shared.isOnline {
            let cached = JSONCache.load([Teacher].self, key: key) ?? []
            return (TeacherUtils.sort(cached), true)
        }

        do {
            let remote = try await APIClient.shared.teachers()
            JSONCache.save(remote, key: key)
            JSONCache.saveMeta(key: key, updatedAt: Date().timeIntervalSince1970 * 1000)
            return (TeacherUtils.sort(remote), false)
        } catch {
            let cached = JSONCache.load([Teacher].self, key: key) ?? []
            return (TeacherUtils.sort(cached), true)
        }
    }

    nonisolated func getTeachersFromCacheOnly() -> [Teacher] {
        let key = "teachers"
        let cached = JSONCache.load([Teacher].self, key: key) ?? []
        return TeacherUtils.sort(cached)
    }
}
