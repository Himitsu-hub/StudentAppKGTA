import Foundation

enum ScheduleLogic {
    static func findNextLesson(schedule: [ScheduleDay], now: Date = Date()) -> Lesson? {
        findNextLessonInfo(schedule: schedule, now: now)?.lesson
    }

    static func findNextLessonInfo(schedule: [ScheduleDay], now: Date = Date()) -> NextLessonInfo? {
        let todayName = DateUtils.todayName(now: now)
        let weekNow = DateUtils.currentWeekType(now: now)
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
                return NextLessonInfo(lesson: upcoming, dayName: todayName, isToday: true, weekType: weekNow)
            }
        }

        let days = DateUtils.russianWeekdayNames()
        let ordered: [String]
        if let curr = days.firstIndex(where: { $0.caseInsensitiveCompare(todayName) == .orderedSame }) {
            ordered = (1...6).map { days[(curr + $0) % days.count] }
        } else {
            // Sunday / unknown — start from Monday
            ordered = days
        }

        for name in ordered {
            guard let first = firstRealLesson(in: schedule, day: name) else { continue }
            let weekForDay = DateUtils.weekTypeForDayName(name, now: now)
            return NextLessonInfo(lesson: first, dayName: name, isToday: false, weekType: weekForDay)
        }
        return nil
    }

    /// Home/widget: load the academic week that matches the next pair's calendar day.
    static func findNextLessonInfoAcrossWeeks(
        faculty: String,
        course: Int,
        group: String,
        subgroup: String?,
        now: Date = Date()
    ) async -> NextLessonInfo? {
        let todayName = DateUtils.todayName(now: now)
        let weekNow = DateUtils.currentWeekType(now: now)
        let current = await ScheduleRepository.shared.getSchedule(
            faculty: faculty,
            course: course,
            group: group,
            subgroup: subgroup,
            weekType: weekNow
        )

        if let info = findNextLessonInfo(schedule: current.schedule, now: now),
           info.isToday || info.weekType.isEmpty || info.weekType == weekNow {
            // Same-week result from current schedule is fine when still today
            // or when weekType matches. If findNextLessonInfo marked another week,
            // reload that week's grid below.
            if info.isToday { return info }
            if info.weekType == weekNow || info.weekType.isEmpty {
                return NextLessonInfo(
                    lesson: info.lesson,
                    dayName: info.dayName,
                    isToday: false,
                    weekType: weekNow
                )
            }
        }

        let days = DateUtils.russianWeekdayNames()
        let ordered: [String]
        if let curr = days.firstIndex(where: { $0.caseInsensitiveCompare(todayName) == .orderedSame }) {
            ordered = (1...6).map { days[(curr + $0) % days.count] }
        } else {
            ordered = days
        }

        var other: ScheduleResult?
        for name in ordered {
            let weekForDay = DateUtils.weekTypeForDayName(name, now: now)
            let schedule: [ScheduleDay]
            if weekForDay == weekNow {
                schedule = current.schedule
            } else {
                if other == nil {
                    other = await ScheduleRepository.shared.getSchedule(
                        faculty: faculty,
                        course: course,
                        group: group,
                        subgroup: subgroup,
                        weekType: weekForDay
                    )
                }
                schedule = other?.schedule ?? []
            }
            if let first = firstRealLesson(in: schedule, day: name) {
                return NextLessonInfo(lesson: first, dayName: name, isToday: false, weekType: weekForDay)
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
