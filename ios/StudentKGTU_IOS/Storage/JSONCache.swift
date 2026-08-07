import Foundation

/// Persistent cache: Application Support **and** UserDefaults (belt + suspenders).
enum JSONCache: Sendable {
    private static var dir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let u = base.appendingPathComponent("StudentKGTUCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    nonisolated static func save<T: Encodable>(_ value: T, key: String) {
        do {
            let data = try JSONEncoder().encode(value)
            let url = fileURL(for: key)
            try data.write(to: url, options: .atomic)
            // Backup in UserDefaults (survives even if file IO fails)
            UserDefaults.standard.set(data, forKey: udKey(key))
            UserDefaults.standard.synchronize()
        } catch {
            // Last resort: UserDefaults only
            if let data = try? JSONEncoder().encode(value) {
                UserDefaults.standard.set(data, forKey: udKey(key))
                UserDefaults.standard.synchronize()
            }
        }
    }

    nonisolated static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        // Prefer file
        let url = fileURL(for: key)
        if let data = try? Data(contentsOf: url),
           let value = try? JSONDecoder().decode(T.self, from: data) {
            return value
        }
        // Fallback UserDefaults
        if let data = UserDefaults.standard.data(forKey: udKey(key)),
           let value = try? JSONDecoder().decode(T.self, from: data) {
            return value
        }
        return nil
    }

    nonisolated static func saveMeta(key: String, updatedAt: Double) {
        UserDefaults.standard.set(updatedAt, forKey: "cache_meta_\(key)")
    }

    nonisolated static func meta(key: String) -> Double {
        UserDefaults.standard.double(forKey: "cache_meta_\(key)")
    }

    nonisolated static func hasData(key: String) -> Bool {
        if FileManager.default.fileExists(atPath: fileURL(for: key).path) { return true }
        return UserDefaults.standard.data(forKey: udKey(key)) != nil
    }

    private nonisolated static func fileURL(for key: String) -> URL {
        dir.appendingPathComponent(sanitize(key) + ".json")
    }

    private nonisolated static func udKey(_ key: String) -> String {
        "json_cache_v2_\(sanitize(key))"
    }

    private nonisolated static func sanitize(_ key: String) -> String {
        key
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: " ", with: "_")
    }
}
