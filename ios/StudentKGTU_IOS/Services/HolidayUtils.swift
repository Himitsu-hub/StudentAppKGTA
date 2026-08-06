import Foundation

enum HolidayUtils {
    /// Official public holidays (schedule day cards).
    private static let holidayNames: [String: String] = [
        "01.01": "Новый год",
        "02.01": "Новогодние каникулы",
        "03.01": "Новогодние каникулы",
        "04.01": "Новогодние каникулы",
        "05.01": "Новогодние каникулы",
        "06.01": "Новогодние каникулы",
        "07.01": "Рождество Христово",
        "08.01": "Новогодние каникулы",
        "23.02": "День защитника Отечества",
        "08.03": "Международный женский день",
        "01.05": "Праздник Весны и Труда",
        "09.05": "День Победы",
        "12.06": "День России",
        "04.11": "День народного единства",
    ]

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "dd.MM"
        return f
    }()

    static func isHoliday(_ date: Date = Date()) -> Bool {
        holidayNames[formatter.string(from: date)] != nil
    }

    static func holidayName(_ date: Date = Date()) -> String? {
        holidayNames[formatter.string(from: date)]
    }

    /// Summer academic break: 1 July … 31 August inclusive (studies from 1 September).
    static func isSummerVacation(_ date: Date = Date()) -> Bool {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let m = cal.component(.month, from: date)
        // July = 7, August = 8
        return m == 7 || m == 8
    }

    static func academicBreakTitle(_ date: Date = Date()) -> String? {
        isSummerVacation(date) ? "Каникулы" : nil
    }

    static func academicBreakSubtitle(_ date: Date = Date()) -> String? {
        isSummerVacation(date) ? "Лето · занятия с 1 сентября" : nil
    }
}
