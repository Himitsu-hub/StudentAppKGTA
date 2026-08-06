import Foundation

enum ScheduleLogic {
    static func findNextLesson(schedule: [ScheduleDay], now: Date = Date()) -> Lesson? {
        findNextLessonInfo(schedule: schedule, now: now)?.lesson
    }

    static func findNextLessonInfo(schedule: [ScheduleDay], now: Date = Date()) -> NextLessonInfo? {
        let todayName = DateUtils.todayName(now: now)
        let cal = Calendar.current

        if let today = schedule.first(where: { $0.dayName.caseInsensitiveCompare(todayName) == .orderedSame }) {
            let upcoming = today.lessons.first { lesson in
                if lesson.type.lowercased() == "праздник" { return false }
                if lesson.subject.trimmingCharacters(in: .whitespaces).isEmpty { return false }
                guard let start = parseStartMinutes(lesson.time) else { return false }
                let nowM = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)
                return start > nowM
            }
            if let upcoming {
                return NextLessonInfo(lesson: upcoming, dayName: todayName, isToday: true)
            }
        }

        let days = DateUtils.russianWeekdayNames()
        guard let curr = days.firstIndex(where: { $0.caseInsensitiveCompare(todayName) == .orderedSame }) else {
            // Sunday or unknown — next Monday onwards
            for name in days {
                if let first = firstRealLesson(in: schedule, day: name) {
                    return NextLessonInfo(lesson: first, dayName: name, isToday: false)
                }
            }
            return nil
        }

        for i in 1...6 {
            let name = days[(curr + i) % days.count]
            if let first = firstRealLesson(in: schedule, day: name) {
                return NextLessonInfo(lesson: first, dayName: name, isToday: false)
            }
        }
        return nil
    }

    private static func firstRealLesson(in schedule: [ScheduleDay], day: String) -> Lesson? {
        schedule.first(where: { $0.dayName.caseInsensitiveCompare(day) == .orderedSame })?
            .lessons
            .first { $0.type.lowercased() != "праздник" && !$0.subject.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    /// Parse "8:00-09:25" or "08:00" → minutes from midnight
    private static func parseStartMinutes(_ time: String) -> Int? {
        let start = time.split(separator: "-").first.map(String.init)?.trimmingCharacters(in: .whitespaces) ?? ""
        let parts = start.split(separator: ":")
        guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }
}
