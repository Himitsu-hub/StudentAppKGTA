import Foundation

enum TeacherUtils {
    static func leadershipPriority(_ teacher: Teacher) -> Int {
        let pos = teacher.position.lowercased()
        if pos.contains("ректор") && !pos.contains("проректор") { return 0 }
        if pos.contains("проректор") { return 1 }
        if pos.contains("декан") { return 2 }
        if pos.contains("заведующий кафедр") || pos.contains("зав. кафедр") { return 3 }
        return 4
    }

    static func extractDepartment(_ teacher: Teacher) -> String {
        let pos = teacher.position
        if let r = pos.range(of: #"[Кк]афедр\w*\s+[«"]?([^»";,\n]+)"#, options: .regularExpression) {
            var raw = String(pos[r])
            if let m = raw.range(of: #"[Кк]афедр\w*\s+[«"]?"#, options: .regularExpression) {
                raw = String(raw[m.upperBound...]).trimmingCharacters(in: CharacterSet(charactersIn: "»\" "))
            }
            if raw.count > 3 && !raw.lowercased().hasPrefix("кандидат") {
                return normalizeDept(raw)
            }
        }
        return ""
    }

    static func departments(_ teachers: [Teacher]) -> [String] {
        var set = Set<String>()
        var hasOther = false
        for t in teachers {
            let d = extractDepartment(t)
            if d.isEmpty { hasOther = true } else { set.insert(d) }
        }
        var result = set.sorted()
        if hasOther { result.append("Другие") }
        return result
    }

    static func sort(_ teachers: [Teacher]) -> [Teacher] {
        teachers.sorted {
            let p0 = leadershipPriority($0)
            let p1 = leadershipPriority($1)
            if p0 != p1 { return p0 < p1 }
            return $0.name < $1.name
        }
    }

    private static func normalizeDept(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower.contains("машиностр") && !lower.contains("технолог") { return "Машиностроение" }
        if lower.contains("гидропневм") || lower.contains("гидропривод") { return "Гидропневмоавтоматика и гидропривод" }
        if lower.contains("робототехн") { return "Робототехника и комплексная автоматизация" }
        if lower.contains("пм и сапр") || lower.contains("сапр") { return "ПМ и САПР" }
        if lower.contains("приборостроен") { return "Приборостроение" }
        if lower.contains("электротехн") { return "Электротехника" }
        if lower.contains("лазерн") || lower.contains("оптико-электрон") { return "Лазерные и оптико-электронные системы" }
        if lower.contains("безопасност") || lower.contains("бжде") { return "Безопасность жизнедеятельности" }
        if lower.contains("экономик") || lower.contains("гуманитарн") { return "Экономика и гуманитарные науки" }
        if lower.contains("менеджмент") { return "Менеджмент" }
        if lower.contains("технолог") && lower.contains("машиностр") { return "Технология машиностроения" }
        if lower.contains("иностран") { return "Иностранные языки" }
        return raw
    }
}
