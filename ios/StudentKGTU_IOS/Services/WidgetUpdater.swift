import Foundation
import WidgetKit

enum WidgetUpdater {
    static func updateAsync() {
        Task { await updateNow() }
    }

    static func updateNow() async {
        let prefs = await MainActor.run { UserPreferences.shared }
        let weekType = DateUtils.currentWeekType()
        let weekLine = "Неделя: \(weekType)"

        let course = await MainActor.run { prefs.course }
        let group = await MainActor.run { prefs.group }
        let subgroup = await MainActor.run { prefs.subgroup }

        let snap: WidgetSnapshot
        if group == nil || group?.isEmpty == true {
            snap = WidgetSnapshot(
                weekLine: weekLine,
                groupLine: "",
                label: "Следующая пара",
                subject: "Группа не выбрана",
                details: "Откройте приложение и выберите курс / группу",
                hint: "Нажмите, чтобы настроить"
            )
        } else {
            let groupLabel: String = {
                var s = group!
                if let subgroup, !subgroup.isEmpty {
                    s += " · \(subgroup)"
                }
                return s
            }()
            let result = await ScheduleRepository.shared.getSchedule(
                course: course,
                group: group!,
                subgroup: subgroup
            )
            if let next = ScheduleLogic.findNextLessonInfo(schedule: result.schedule) {
                let lesson = next.lesson
                let dayLabel = next.isToday ? "сегодня" : shortDay(next.dayName)
                var details = ""
                if !lesson.time.isEmpty { details = lesson.time }
                if !lesson.room.isEmpty {
                    if !details.isEmpty { details += " · " }
                    details += "каб. \(lesson.room)"
                }
                if !details.isEmpty { details += " · " }
                details += dayLabel
                if !lesson.type.isEmpty && lesson.type.lowercased() != "праздник" {
                    details += " · \(lesson.type)"
                }
                let teacher = lesson.teacher.trimmingCharacters(in: .whitespaces)
                let detailsFull = [details, teacher].filter { !$0.isEmpty }.joined(separator: "\n")
                snap = WidgetSnapshot(
                    weekLine: weekLine,
                    groupLine: groupLabel,
                    label: next.isToday ? "Следующая пара" : "Ближайшая пара",
                    subject: lesson.subject.isEmpty ? "Пара" : lesson.subject,
                    details: detailsFull,
                    hint: "Нажмите, чтобы открыть расписание"
                )
            } else {
                snap = WidgetSnapshot(
                    weekLine: weekLine,
                    groupLine: groupLabel,
                    label: "Следующая пара",
                    subject: "Сейчас пар нет",
                    details: result.isOffline
                        ? "Нет данных · проверьте сеть в приложении"
                        : "На этой неделе занятий не найдено",
                    hint: "Нажмите, чтобы открыть приложение"
                )
            }
        }

        WidgetSnapshot.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func shortDay(_ dayName: String) -> String {
        let d = dayName.lowercased()
        if d.hasPrefix("пон") { return "пн" }
        if d.hasPrefix("вто") { return "вт" }
        if d.hasPrefix("сре") { return "ср" }
        if d.hasPrefix("чет") { return "чт" }
        if d.hasPrefix("пят") { return "пт" }
        if d.hasPrefix("суб") { return "сб" }
        if d.hasPrefix("вос") { return "вс" }
        return String(dayName.prefix(2)).lowercased()
    }
}
