import Foundation

struct CourseInfo: Codable, Identifiable, Hashable {
    let course: Int
    let available: Bool
    var faculty: String?
    var id: Int { course }
}

struct FacultyInfo: Codable, Identifiable, Hashable {
    let id: String
    let short: String
    let name: String
    var courses: [CourseInfo] = []
}

struct FacultiesResponse: Codable {
    var faculties: [FacultyInfo] = []
    var defaultFaculty: String?
}

enum FacultyCatalog {
    static let fae = "fae"
    static let mtf = "mtf"
    static let masters = "masters"

    static func shortName(_ id: String) -> String {
        switch id {
        case mtf: return "МТФ"
        case masters: return "Маг."
        default: return "АиЭ"
        }
    }

    static func fullName(_ id: String) -> String {
        switch id {
        case mtf: return "Машиностроительный технологический"
        case masters: return "Магистратура (очное)"
        default: return "Автоматика и электроника"
        }
    }

    static func courses(for id: String) -> [Int] {
        switch id {
        case mtf: return Array(1...5)
        case masters: return [2]
        default: return Array(1...4)
        }
    }

    static let all: [(id: String, short: String)] = [
        (fae, "АиЭ"),
        (mtf, "МТФ"),
        (masters, "Маг."),
    ]
}

struct TeacherLesson: Codable, Identifiable, Hashable {
    var dayName: String = ""
    var time: String = ""
    var subject: String = ""
    var teacher: String = ""
    var room: String = ""
    var type: String = ""
    var faculty: String = ""
    var course: Int = 0
    var group: String = ""
    var subgroup: String = ""

    var id: String {
        "\(dayName)|\(time)|\(subject)|\(room)|\(group)|\(faculty)|\(course)"
    }
}

struct TeacherScheduleResponse: Codable {
    var query: String = ""
    var weekType: String = ""
    var day: String = ""
    var count: Int = 0
    var lessons: [TeacherLesson] = []
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
    var faculty: String
    let course: Int
    let group: String
    let subgroup: String?
    let weekType: String
    var schedule: [ScheduleDay]
    var fromCache: Bool
    var isOffline: Bool
    var updatedAtMillis: Double

    init(
        faculty: String = FacultyCatalog.fae,
        course: Int,
        group: String,
        subgroup: String?,
        weekType: String,
        schedule: [ScheduleDay] = [],
        fromCache: Bool = false,
        isOffline: Bool = false,
        updatedAtMillis: Double = 0
    ) {
        self.faculty = faculty
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
        case faculty, course, group, subgroup, weekType, schedule, fromCache, isOffline, updatedAtMillis
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        faculty = try c.decodeIfPresent(String.self, forKey: .faculty) ?? FacultyCatalog.fae
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
        try c.encode(faculty, forKey: .faculty)
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
