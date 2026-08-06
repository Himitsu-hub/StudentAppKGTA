import Foundation

enum TimeFormat {
    static func updatedAtLabel(millis: Double, now: Date = Date()) -> String? {
        guard millis > 0 else { return nil }
        let date = Date(timeIntervalSince1970: millis / 1000)
        let diff = max(0, now.timeIntervalSince(date))
        let minutes = Int(diff / 60)
        let hours = Int(diff / 3600)

        let relative: String
        if minutes < 1 {
            relative = "только что"
        } else if minutes < 60 {
            relative = "\(minutes) мин назад"
        } else if hours < 24 {
            switch hours {
            case 1: relative = "1 час назад"
            case 2...4: relative = "\(hours) часа назад"
            default: relative = "\(hours) часов назад"
            }
        } else {
            let df = DateFormatter()
            df.locale = Locale(identifier: "ru_RU")
            df.dateFormat = "HH:mm"
            let time = df.string(from: date)
            let cal = Calendar.current
            if cal.isDateInToday(date) {
                return "Обновлено: сегодня, \(time)"
            } else if cal.isDateInYesterday(date) {
                return "Обновлено: вчера, \(time)"
            } else {
                df.dateFormat = "d MMM"
                return "Обновлено: \(df.string(from: date)), \(time)"
            }
        }
        return "Обновлено: \(relative)"
    }
}
