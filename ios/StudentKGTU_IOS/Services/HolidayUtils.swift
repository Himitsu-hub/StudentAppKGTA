import Foundation

enum HolidayUtils {
    /// Same basic Russian public holidays as Android HolidayUtils.
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
}
