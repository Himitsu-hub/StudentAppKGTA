import Foundation

enum APIError: LocalizedError {
    case badURL
    case http(Int)
    case decoding(Error)
    case transport(Error)

    var errorDescription: String? {
        switch self {
        case .badURL: return "Некорректный URL"
        case .http(let c): return "Ошибка сервера (\(c))"
        case .decoding(let e): return "Ошибка разбора: \(e.localizedDescription)"
        case .transport(let e): return e.localizedDescription
        }
    }
}

actor APIClient {
    static let shared = APIClient()
    let baseURL = URL(string: "https://apistudentkgtu.ru")!
    private let session: URLSession
    private let decoder = JSONDecoder()

    init() {
        // VPN-friendly session: wait for path, longer timeouts, retries at call site
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 45
        config.timeoutIntervalForResource = 90
        config.httpMaximumConnectionsPerHost = 4
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        // Helps when interface switches (Wi‑Fi ↔ cellular ↔ VPN)
        config.allowsExpensiveNetworkAccess = true
        config.allowsConstrainedNetworkAccess = true
        self.session = URLSession(configuration: config)
    }

    func courses() async throws -> [CourseInfo] {
        try await get("/api/courses")
    }

    func groups(course: Int) async throws -> [String: [String]] {
        try await get("/api/groups", query: [("course", String(course))])
    }

    func schedule(course: Int, group: String, subgroup: String?) async throws -> ScheduleResult {
        var q: [(String, String)] = [("course", String(course)), ("group", group)]
        if let subgroup, !subgroup.isEmpty { q.append(("subgroup", subgroup)) }
        var result: ScheduleResult = try await get("/api/schedule", query: q)
        result.isOffline = false
        result.fromCache = false
        result.updatedAtMillis = Date().timeIntervalSince1970 * 1000
        return result
    }

    func weekType() async throws -> String {
        let r: WeekTypeResponse = try await get("/api/week-type")
        return r.weekType
    }

    func news(limit: Int = 10) async throws -> [NewsItem] {
        let r: NewsResponse = try await get("/api/news", query: [("limit", String(limit))])
        return r.news
    }

    func teachers() async throws -> [Teacher] {
        let r: TeachersResponse = try await get("/api/teachers")
        return r.teachers
    }

    /// GET with retries — better under VPN / unstable routes.
    private func get<T: Decodable>(_ path: String, query: [(String, String)] = [], retries: Int = 3) async throws -> T {
        var lastError: Error?
        for attempt in 0..<retries {
            do {
                return try await getOnce(path, query: query)
            } catch let e as APIError {
                // Don't retry bad decode / bad URL
                if case .decoding = e { throw e }
                if case .badURL = e { throw e }
                if case .http(let code) = e, (400...499).contains(code) { throw e }
                lastError = e
            } catch {
                lastError = error
            }
            if attempt + 1 < retries {
                let delay = UInt64((attempt + 1) * 700_000_000) // 0.7s, 1.4s, …
                try? await Task.sleep(nanoseconds: delay)
            }
        }
        if let e = lastError as? APIError { throw e }
        throw APIError.transport(lastError ?? URLError(.unknown))
    }

    private func getOnce<T: Decodable>(_ path: String, query: [(String, String)]) async throws -> T {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw APIError.badURL
        }
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.0, value: $0.1) }
        }
        guard let url = components.url else { throw APIError.badURL }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("StudentKGTU-iOS", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 45
        do {
            let (data, response) = try await session.data(for: request)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw APIError.http(http.statusCode)
            }
            do { return try decoder.decode(T.self, from: data) }
            catch { throw APIError.decoding(error) }
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.transport(error)
        }
    }
}
