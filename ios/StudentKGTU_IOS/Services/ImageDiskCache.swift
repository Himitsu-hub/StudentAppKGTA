import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Disk cache for remote images (news + teacher photos) so they work offline.
enum ImageDiskCache: Sendable {
    private static var dir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let u = base.appendingPathComponent("StudentKGTUImageCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    nonisolated static func fileURL(forRemote urlString: String) -> URL {
        let name = stableName(urlString) + ".img"
        return dir.appendingPathComponent(name)
    }

    nonisolated static func hasCached(urlString: String) -> Bool {
        FileManager.default.fileExists(atPath: fileURL(forRemote: urlString).path)
    }

    nonisolated static func loadData(urlString: String) -> Data? {
        let path = fileURL(forRemote: urlString).path
        return try? Data(contentsOf: URL(fileURLWithPath: path))
    }

    nonisolated static func save(data: Data, urlString: String) {
        let url = fileURL(forRemote: urlString)
        try? data.write(to: url, options: .atomic)
    }

    /// Load from disk, or download+save if online.
    nonisolated static func data(for urlString: String) async -> Data? {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if let local = loadData(urlString: trimmed), !local.isEmpty {
            return local
        }

        guard NetworkMonitor.shared.isOnline,
              let remote = URL(string: trimmed) else {
            return loadData(urlString: trimmed)
        }

        do {
            var request = URLRequest(url: remote)
            request.timeoutInterval = 20
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                return nil
            }
            guard !data.isEmpty else { return nil }
            save(data: data, urlString: trimmed)
            return data
        } catch {
            return loadData(urlString: trimmed)
        }
    }

    /// Prefetch many URLs in background (after online load of news/teachers).
    nonisolated static func prefetch(urls: [String]) {
        let unique = Array(Set(urls.filter { !$0.isEmpty && !hasCached(urlString: $0) }))
        guard !unique.isEmpty, NetworkMonitor.shared.isOnline else { return }
        Task.detached(priority: .utility) {
            for u in unique.prefix(40) {
                _ = await data(for: u)
            }
        }
    }

    private nonisolated static func stableName(_ urlString: String) -> String {
        // Stable filesystem-safe key from URL
        var hasher = Hasher()
        hasher.combine(urlString)
        let h = UInt64(bitPattern: Int64(hasher.finalize()))
        return String(h, radix: 16)
    }
}
