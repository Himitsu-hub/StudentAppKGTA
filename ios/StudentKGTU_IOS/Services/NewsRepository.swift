import Foundation

actor NewsRepository {
    static let shared = NewsRepository()

    /// Stable cache key so limit changes still hit the same offline store.
    private static let cacheKey = "news_latest"

    func getNews(limit: Int = 15) async -> (news: [NewsItem], fromCache: Bool, updatedAt: Double) {
        let key = Self.cacheKey

        // Offline: cache only, no long network wait
        if !NetworkMonitor.shared.isOnline {
            let cached = JSONCache.load([NewsItem].self, key: key) ?? []
            return (Array(cached.prefix(limit)), true, JSONCache.meta(key: key))
        }

        do {
            let remote = try await APIClient.shared.news(limit: limit)
            let now = Date().timeIntervalSince1970 * 1000
            if !remote.isEmpty {
                JSONCache.save(remote, key: key)
                // Also keep limit-specific key for older builds
                JSONCache.save(remote, key: "news_\(limit)")
                JSONCache.saveMeta(key: key, updatedAt: now)
                return (remote, false, now)
            }
            let cached = JSONCache.load([NewsItem].self, key: key) ?? []
            return (Array(cached.prefix(limit)), true, JSONCache.meta(key: key))
        } catch {
            let cached = JSONCache.load([NewsItem].self, key: key)
                ?? JSONCache.load([NewsItem].self, key: "news_\(limit)")
                ?? []
            return (Array(cached.prefix(limit)), true, JSONCache.meta(key: key))
        }
    }

    /// Instant disk read without network.
    nonisolated func getNewsFromCacheOnly(limit: Int = 15) -> (news: [NewsItem], updatedAt: Double) {
        let key = "news_latest"
        let cached = JSONCache.load([NewsItem].self, key: key)
            ?? JSONCache.load([NewsItem].self, key: "news_\(limit)")
            ?? []
        return (Array(cached.prefix(limit)), JSONCache.meta(key: key))
    }
}
