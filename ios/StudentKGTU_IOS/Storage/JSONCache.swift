import Foundation

enum JSONCache: Sendable {
    private static var dir: URL {
        let u = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("StudentKGTUCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    nonisolated static func save<T: Encodable>(_ value: T, key: String) {
        let url = dir.appendingPathComponent(key.replacingOccurrences(of: "/", with: "_") + ".json")
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }

    nonisolated static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        let url = dir.appendingPathComponent(key.replacingOccurrences(of: "/", with: "_") + ".json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    nonisolated static func saveMeta(key: String, updatedAt: Double) {
        UserDefaults.standard.set(updatedAt, forKey: "cache_meta_\(key)")
    }

    nonisolated static func meta(key: String) -> Double {
        UserDefaults.standard.double(forKey: "cache_meta_\(key)")
    }
}
