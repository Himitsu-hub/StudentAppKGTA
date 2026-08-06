import Foundation

enum DateUtils: Sendable {
    private static let cal = Calendar.current

    /// Same idea as Android DateUtils.getCurrentWeekType
    static func currentWeekType(now: Date = Date()) -> String {
        let start = semesterStart(for: now)
        let startDay = cal.startOfDay(for: start)
        let nowDay = cal.startOfDay(for: now)
        let days = cal.dateComponents([.day], from: startDay, to: nowDay).day ?? 0
        let weeks = max(0, days / 7)
        return weeks % 2 == 0 ? "Числитель" : "Знаменатель"
    }

    static func semesterStart(for now: Date) -> Date {
        let y = cal.component(.year, from: now)
        let m = cal.component(.month, from: now)
        if m >= 9 {
            return date(year: y, month: 9, day: 1)
        } else {
            let jan13 = date(year: y, month: 1, day: 13)
            if now < jan13 {
                return date(year: y - 1, month: 9, day: 1)
            }
            return jan13
        }
    }

    static func todayName(now: Date = Date()) -> String {
        let map = [
            1: "Воскресенье",
            2: "Понедельник",
            3: "Вторник",
            4: "Среда",
            5: "Четверг",
            6: "Пятница",
            7: "Суббота",
        ]
        let wd = cal.component(.weekday, from: now)
        return map[wd] ?? "Понедельник"
    }

    static func russianWeekdayNames() -> [String] {
        ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"]
    }

    /// Next calendar date for a weekday name (this week or next) — for holidays.
    static func dateForDay(_ dayName: String, now: Date = Date()) -> Date {
        let map: [String: Int] = [
            "Понедельник": 2,
            "Вторник": 3,
            "Среда": 4,
            "Четверг": 5,
            "Пятница": 6,
            "Суббота": 7,
            "Воскресенье": 1,
        ]
        guard let target = map[dayName] else { return now }
        let current = cal.component(.weekday, from: now)
        var diff = target - current
        if diff < 0 { diff += 7 }
        return cal.date(byAdding: .day, value: diff, to: cal.startOfDay(for: now)) ?? now
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        var c = DateComponents()
        c.year = year; c.month = month; c.day = day
        return Calendar.current.date(from: c) ?? Date()
    }
}
