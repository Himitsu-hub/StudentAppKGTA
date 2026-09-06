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
        let rectorName = "Егоров Алексей Васильевич"
        let key = rectorName.lowercased().replacingOccurrences(of: "ё", with: "е")
        var list = teachers
        if let idx = list.firstIndex(where: {
            $0.name.lowercased().replacingOccurrences(of: "ё", with: "е") == key
        }) {
            if list[idx].photo_url.trimmingCharacters(in: .whitespaces).isEmpty {
                list[idx].photo_url = "https://dksta.ru/d/egorov_av.jpg"
            }
            if list[idx].profile_url.trimmingCharacters(in: .whitespaces).isEmpty {
                list[idx].profile_url = "https://dksta.ru/egorov-aleksey-vasilyevich"
            }
            if list[idx].position.trimmingCharacters(in: .whitespaces).isEmpty {
                list[idx].position = "И.о. ректора"
            }
            if list[idx].email.trimmingCharacters(in: .whitespaces).isEmpty {
                list[idx].email = "egorov@dksta.ru"
            }
            return list
        }
        let rector = Teacher(
            name: rectorName,
            profile_url: "https://dksta.ru/egorov-aleksey-vasilyevich",
            photo_url: "https://dksta.ru/d/egorov_av.jpg",
            position: "И.о. ректора",
            email: "egorov@dksta.ru"
        )
        return [rector] + list
    }
}
