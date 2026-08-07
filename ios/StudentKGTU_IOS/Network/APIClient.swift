import Foundation

enum APIError: LocalizedError {
    case badURL
    case offline
    case http(Int)
    case decoding(Error)
    case transport(Error)

    var errorDescription: String? {
        switch self {
        case .badURL: return "Некорректный URL"
        case .offline: return "Нет сети"
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
    private let offlineSession: URLSession
    private let decoder = JSONDecoder()

    init() {
        // Online / VPN: wait for path, longer timeouts
        let online = URLSessionConfiguration.default
        online.waitsForConnectivity = true
        online.timeoutIntervalForRequest = 45
        online.timeoutIntervalForResource = 90
        online.httpMaximumConnectionsPerHost = 4
        online.requestCachePolicy = .reloadIgnoringLocalCacheData
        online.allowsExpensiveNetworkAccess = true
        online.allowsConstrainedNetworkAccess = true
        self.session = URLSession(configuration: online)

        // Offline: fail fast so we can show disk cache immediately
        let offline = URLSessionConfiguration.default
        offline.waitsForConnectivity = false
        offline.timeoutIntervalForRequest = 3
        offline.timeoutIntervalForResource = 5
        offline.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.offlineSession = URLSession(configuration: offline)
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

    private func get<T: Decodable>(_ path: String, query: [(String, String)] = [], retries: Int = 3) async throws -> T {
        let online = NetworkMonitor.shared.isOnline
        if !online {
            // One quick attempt then fail — repositories use disk cache
            return try await getOnce(path, query: query, useOfflineSession: true)
        }

        var lastError: Error?
        let attempts = max(1, retries)
        for attempt in 0..<attempts {
            do {
                return try await getOnce(path, query: query, useOfflineSession: false)
            } catch let e as APIError {
                if case .decoding = e { throw e }
                if case .badURL = e { throw e }
                if case .http(let code) = e, (400...499).contains(code) { throw e }
                lastError = e
            } catch {
                lastError = error
            }
            if attempt + 1 < attempts {
                let delay = UInt64((attempt + 1) * 500_000_000)
                try? await Task.sleep(nanoseconds: delay)
            }
        }
        if let e = lastError as? APIError { throw e }
        throw APIError.transport(lastError ?? URLError(.notConnectedToInternet))
    }

    private func getOnce<T: Decodable>(
        _ path: String,
        query: [(String, String)],
        useOfflineSession: Bool
    ) async throws -> T {
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
        request.timeoutInterval = useOfflineSession ? 3 : 45

        let active = useOfflineSession ? offlineSession : session
        do {
            let (data, response) = try await active.data(for: request)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw APIError.http(http.statusCode)
            }
            do { return try decoder.decode(T.self, from: data) }
            catch { throw APIError.decoding(error) }
        } catch let e as APIError {
            throw e
        } catch {
            if useOfflineSession {
                throw APIError.offline
            }
            throw APIError.transport(error)
        }
    }
}
