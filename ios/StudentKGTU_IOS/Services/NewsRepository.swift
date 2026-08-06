import Foundation

actor NewsRepository {
    static let shared = NewsRepository()

    func getNews(limit: Int = 10) async -> (news: [NewsItem], fromCache: Bool, updatedAt: Double) {
        let key = "news_\(limit)"
        do {
            let remote = try await APIClient.shared.news(limit: limit)
            let now = Date().timeIntervalSince1970 * 1000
            JSONCache.save(remote, key: key)
            JSONCache.saveMeta(key: key, updatedAt: now)
            return (remote, false, now)
        } catch {
            let cached = JSONCache.load([NewsItem].self, key: key) ?? []
            return (cached, true, JSONCache.meta(key: key))
        }
    }
}
