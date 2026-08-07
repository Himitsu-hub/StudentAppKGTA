import Foundation

/// Persistent JSON disk cache (Application Support — not purged like Caches).
enum JSONCache: Sendable {
    private static var dir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let u = base.appendingPathComponent("StudentKGTUCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    nonisolated static func save<T: Encodable>(_ value: T, key: String) {
        let url = fileURL(for: key)
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }

    nonisolated static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        let url = fileURL(for: key)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    nonisolated static func saveMeta(key: String, updatedAt: Double) {
        UserDefaults.standard.set(updatedAt, forKey: "cache_meta_\(key)")
    }

    nonisolated static func meta(key: String) -> Double {
        UserDefaults.standard.double(forKey: "cache_meta_\(key)")
    }

    nonisolated static func hasData(key: String) -> Bool {
        FileManager.default.fileExists(atPath: fileURL(for: key).path)
    }

    private nonisolated static func fileURL(for key: String) -> URL {
        dir.appendingPathComponent(key.replacingOccurrences(of: "/", with: "_") + ".json")
    }
}
