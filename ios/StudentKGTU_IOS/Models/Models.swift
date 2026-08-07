import Foundation

struct CourseInfo: Codable, Identifiable, Hashable {
    let course: Int
    let available: Bool
    var id: Int { course }
}

struct Lesson: Codable, Identifiable, Hashable {
    var time: String = ""
    var subject: String = ""
    var teacher: String = ""
    var room: String = ""
    var type: String = ""

    var id: String { "\(time)|\(subject)|\(room)|\(type)|\(teacher)" }
}

struct ScheduleDay: Codable, Identifiable, Hashable {
    let dayName: String
    var lessons: [Lesson] = []
    var id: String { dayName }
}

struct ScheduleResult: Codable {
    let course: Int
    let group: String
    let subgroup: String?
    let weekType: String
    var schedule: [ScheduleDay]
    var fromCache: Bool
    var isOffline: Bool
    var updatedAtMillis: Double

    init(
        course: Int,
        group: String,
        subgroup: String?,
        weekType: String,
        schedule: [ScheduleDay] = [],
        fromCache: Bool = false,
        isOffline: Bool = false,
        updatedAtMillis: Double = 0
    ) {
        self.course = course
        self.group = group
        self.subgroup = subgroup
        self.weekType = weekType
        self.schedule = schedule
        self.fromCache = fromCache
        self.isOffline = isOffline
        self.updatedAtMillis = updatedAtMillis
    }

    enum CodingKeys: String, CodingKey {
        case course, group, subgroup, weekType, schedule, fromCache, isOffline, updatedAtMillis
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        course = try c.decode(Int.self, forKey: .course)
        group = try c.decode(String.self, forKey: .group)
        subgroup = try c.decodeIfPresent(String.self, forKey: .subgroup)
        weekType = try c.decodeIfPresent(String.self, forKey: .weekType) ?? ""
        schedule = try c.decodeIfPresent([ScheduleDay].self, forKey: .schedule) ?? []
        fromCache = try c.decodeIfPresent(Bool.self, forKey: .fromCache) ?? false
        isOffline = try c.decodeIfPresent(Bool.self, forKey: .isOffline) ?? false
        updatedAtMillis = try c.decodeIfPresent(Double.self, forKey: .updatedAtMillis) ?? 0
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(course, forKey: .course)
        try c.encode(group, forKey: .group)
        try c.encodeIfPresent(subgroup, forKey: .subgroup)
        try c.encode(weekType, forKey: .weekType)
        try c.encode(schedule, forKey: .schedule)
        try c.encode(fromCache, forKey: .fromCache)
        try c.encode(isOffline, forKey: .isOffline)
        try c.encode(updatedAtMillis, forKey: .updatedAtMillis)
    }
}

struct Teacher: Codable, Identifiable, Hashable {
    var name: String = ""
    var profile_url: String = ""
    var photo_url: String = ""
    var position: String = ""
    var email: String = ""
    var subjects: [String]? = []

    var id: String { name }
    var profileUrl: String { profile_url }
    var photoUrl: String { photo_url }
    var subjectList: [String] { subjects ?? [] }
}

struct TeachersResponse: Codable {
    var teachers: [Teacher] = []
}

struct NewsItem: Codable, Identifiable, Hashable {
    var title: String = ""
    var url: String = ""
    var image_url: String = ""
    var date: String = ""
    var description: String = ""

    var id: String { url.isEmpty ? title : url }
    var imageUrl: String { image_url }
}

struct NewsResponse: Codable {
    var news: [NewsItem] = []
    var count: Int?
}

struct WeekTypeResponse: Codable {
    let weekType: String
}

struct Reminder: Identifiable, Codable, Hashable {
    var id: String
    var text: String
    var dateTimeMillis: Double
    var isCompleted: Bool = false
}

struct NextLessonInfo {
    let lesson: Lesson
    let dayName: String
    let isToday: Bool
}
