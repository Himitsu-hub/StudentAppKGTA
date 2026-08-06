import Foundation

enum AppRoute: Hashable {
    case schedule
    case teachers
    case teacherDetail(String) // unused (detail inside TeachersView)
    case reminders
    case campus
}
