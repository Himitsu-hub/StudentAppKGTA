import Foundation

actor NewsRepository {
    static let shared = NewsRepository()

    func getNews(limit: Int = 10) async -> (news: [NewsItem], fromCache: Bool, updatedAt: Double) {
        let key = "news_\(limit)"

        // Offline: cache only, no long network wait
        if !NetworkMonitor.shared.isOnline {
            let cached = JSONCache.load([NewsItem].self, key: key) ?? []
            return (cached, true, JSONCache.meta(key: key))
        }

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

    /// Instant disk read without network.
    nonisolated func getNewsFromCacheOnly(limit: Int = 10) -> (news: [NewsItem], updatedAt: Double) {
        let key = "news_\(limit)"
        let cached = JSONCache.load([NewsItem].self, key: key) ?? []
        return (cached, JSONCache.meta(key: key))
    }
}
