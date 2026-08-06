import Foundation

actor TeachersRepository {
    static let shared = TeachersRepository()

    func getTeachers() async -> (teachers: [Teacher], fromCache: Bool) {
        let key = "teachers"
        do {
            let remote = try await APIClient.shared.teachers()
            JSONCache.save(remote, key: key)
            return (TeacherUtils.sort(remote), false)
        } catch {
            let cached = JSONCache.load([Teacher].self, key: key) ?? []
            return (TeacherUtils.sort(cached), true)
        }
    }
}
