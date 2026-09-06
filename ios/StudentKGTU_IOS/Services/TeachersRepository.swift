import Foundation

actor TeachersRepository {
    static let shared = TeachersRepository()

    func getTeachers() async -> (teachers: [Teacher], fromCache: Bool) {
        let key = "teachers"

        if !NetworkMonitor.shared.isOnline {
            let cached = Self.ensureLeadership(JSONCache.load([Teacher].self, key: key) ?? [])
            return (TeacherUtils.sort(cached), true)
        }

        do {
            let remote = Self.ensureLeadership(try await APIClient.shared.teachers())
            JSONCache.save(remote, key: key)
            JSONCache.saveMeta(key: key, updatedAt: Date().timeIntervalSince1970 * 1000)
            return (TeacherUtils.sort(remote), false)
        } catch {
            let cached = Self.ensureLeadership(JSONCache.load([Teacher].self, key: key) ?? [])
            return (TeacherUtils.sort(cached), true)
        }
    }

    nonisolated func getTeachersFromCacheOnly() -> [Teacher] {
        let key = "teachers"
        let cached = Self.ensureLeadership(JSONCache.load([Teacher].self, key: key) ?? [])
        return TeacherUtils.sort(cached)
    }

    nonisolated private static func ensureLeadership(_ teachers: [Teacher]) -> [Teacher] {
        let existing = Set(teachers.map { $0.name.lowercased().replacingOccurrences(of: "ё", with: "е") })
        let rectorName = "Егоров Алексей Васильевич"
        let key = rectorName.lowercased().replacingOccurrences(of: "ё", with: "е")
        guard !existing.contains(key) else { return teachers }
        let rector = Teacher(name: rectorName, position: "Ректор")
        return [rector] + teachers
    }
}
