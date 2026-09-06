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
    private let decoder = JSONDecoder()

    init() {
        // NEVER waitsForConnectivity — that hangs for minutes in airplane mode / bad VPN.
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = false
        // VPN-friendly timeouts: enough for slow handshake, still fail over to disk cache.
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 20
        config.httpMaximumConnectionsPerHost = 4
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.allowsExpensiveNetworkAccess = true
        config.allowsConstrainedNetworkAccess = true
        self.session = URLSession(configuration: config)
    }

    func faculties() async throws -> FacultiesResponse {
        try await get("/api/faculties")
    }

    func courses(faculty: String = FacultyCatalog.fae) async throws -> [CourseInfo] {
        try await get("/api/courses", query: [("faculty", faculty)])
    }

    func groups(faculty: String = FacultyCatalog.fae, course: Int) async throws -> [String: [String]] {
        try await get("/api/groups", query: [
            ("faculty", faculty),
            ("course", String(course)),
        ])
    }

    func schedule(
        faculty: String = FacultyCatalog.fae,
        course: Int,
        group: String,
        subgroup: String?,
        week: String? = nil
    ) async throws -> ScheduleResult {
        var q: [(String, String)] = [
            ("faculty", faculty),
            ("course", String(course)),
            ("group", group),
        ]
        if let subgroup, !subgroup.isEmpty { q.append(("subgroup", subgroup)) }
        if let week, !week.isEmpty { q.append(("week", week)) }
        var result: ScheduleResult = try await get("/api/schedule", query: q)
        result.isOffline = false
        result.fromCache = false
        result.updatedAtMillis = Date().timeIntervalSince1970 * 1000
        return result
    }

    func scheduleByTeacher(query: String, day: String = "today", week: String? = nil) async throws -> TeacherScheduleResponse {
        var q: [(String, String)] = [
            ("q", query),
            ("day", day),
        ]
        if let week, !week.isEmpty { q.append(("week", week)) }
        return try await get("/api/schedule/by-teacher", query: q)
    }

    func weekType() async throws -> String {
        let r: WeekTypeResponse = try await get("/api/week-type")
        return r.weekType
    }

    func news(limit: Int = 10, force: Bool = false) async throws -> [NewsItem] {
        var q: [(String, String)] = [("limit", String(limit))]
        // force=true → ask server for a background scrape; response is still from cache
        if force { q.append(("force", "true")) }
        // Single attempt — retries make PTR feel "eternal" when API is down
        let r: NewsResponse = try await get("/api/news", query: q, retries: 1)
        return r.news
    }

    func teachers() async throws -> [Teacher] {
        let r: TeachersResponse = try await get("/api/teachers")
        return r.teachers
    }

    private func get<T: Decodable>(
        _ path: String,
        query: [(String, String)] = [],
        retries: Int = 1
    ) async throws -> T {
        // Hard offline gate — do not touch the network at all
        if !NetworkMonitor.shared.isOnline {
            throw APIError.offline
        }

        var lastError: Error?
        // Default 1 attempt: on VPN, retries turn one slow failure into a long freeze.
        let attempts = max(1, retries)
        for attempt in 0..<attempts {
            // Re-check each attempt (user may toggle airplane mid-flight)
            if !NetworkMonitor.shared.isOnline {
                throw APIError.offline
            }
            do {
                return try await getOnce(path, query: query)
            } catch let e as APIError {
                if case .decoding = e { throw e }
                if case .badURL = e { throw e }
                if case .offline = e { throw e }
                if case .http(let code) = e, (400...499).contains(code) { throw e }
                lastError = e
            } catch {
                lastError = error
            }
            if attempt + 1 < attempts {
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }
        if let e = lastError as? APIError { throw e }
        throw APIError.transport(lastError ?? URLError(.notConnectedToInternet))
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
        request.timeoutInterval = 12

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
