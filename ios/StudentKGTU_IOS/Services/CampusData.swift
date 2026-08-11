import Foundation

struct CampusBuilding: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
}

struct ContactItem: Identifiable {
    let id = UUID()
    let title: String
    var role: String? = nil
    var phone: String? = nil
    var email: String? = nil
    var note: String? = nil
    var webUri: String? = nil
}

enum CampusData {
    static let mainPhoneDisplay = "8 (49232) 6-96-00"
    static let mainPhoneTel = "tel:+74923269600"
    static let address = "г. Ковров, ул. Маяковского, 19"
    static let mapsURI = "https://yandex.ru/maps/-/CDS77ZJZ"
    static let siteContacts = "https://dksta.ru/kontakty-1"
    static let siteAll = "https://dksta.ru/kontakty-2"
    static let workHours = "Пн–Пт, 8:00–17:00"

    static let buildings: [CampusBuilding] = [
        .init(title: "Главный корпус", subtitle: "Учебные аудитории, деканаты, ректорат"),
        .init(title: "Корпус лабораторий", subtitle: "Лаб. занятия, кафедры"),
        .init(title: "Спортивный комплекс", subtitle: "Физкультура, секции"),
        .init(title: "Общежитие / столовая", subtitle: "Быт и питание"),
    ]

    static let quickContacts: [ContactItem] = [
        .init(title: "Приёмная ректора", role: "Егоров А.В., и.о. ректора", phone: "доб. 246", email: "ksta@dksta.ru"),
        .init(title: "Учебно-методическое управление", role: "Хрусталёв П.Е. · расписание, УМУ", phone: "доб. 220", email: "umu@dksta.ru"),
        .init(title: "Приёмная комиссия", role: "Шварёва И.С. · довузовская подготовка", phone: "доб. 100 · 6-96-02", email: "pk@dksta.ru"),
        .init(title: "Научно-техническая библиотека", role: "Красавина Н.С.", phone: "доб. 126–129", email: "ntb@dksta.ru"),
    ]

    static let deaneries: [ContactItem] = [
        .init(title: "Деканат МТФ", role: "Механико-технологический факультет · Грачёва И.В.", phone: "доб. 206 / 207", email: "mtf@dksta.ru"),
        .init(title: "Деканат ФАиЭ", role: "Факультет автоматики и электроники · Митрофанов А.А.", phone: "доб. 326 / 327", email: "aie@dksta.ru"),
        .init(title: "Деканат ФЭиМ", role: "Факультет экономики и менеджмента · Быкова А.В.", phone: "доб. 400 / 404 / 409", email: "eim@dksta.ru"),
        .init(title: "Энергомеханический колледж", role: "Антонова М.Е., директор ЭМК", phone: "доб. 28", email: "emk@dksta.ru"),
    ]

    static let services: [ContactItem] = [
        .init(title: "Общежитие", role: "Кочергина Г.А. · Илясов Н.И.", phone: "доб. 114 / 193", email: "otel@dksta.ru"),
        .init(title: "Иностранные студенты", role: "Крылова Э.Ю.", phone: "доб. 219", email: "inostr@dksta.ru"),
        .init(title: "Молодёжная политика", role: "Демьянова Е.В. · Жук А.А.", phone: "доб. 248 / 230", email: "molodezhnaya_politika@dksta.ru"),
        .init(title: "Военный учебный центр", role: "Баженов Ю.В.", phone: "доб. 14", email: "voenka@dksta.ru"),
        .init(title: "Бухгалтерия", role: "Шитова Н.Н., главный бухгалтер", phone: "доб. 680", email: "buh@dksta.ru"),
        .init(title: "IT / техподдержка", role: "Кузнецов Д.А.", phone: "доб. 229", email: "admin@dksta.ru"),
        .init(title: "Юрист", role: "Торопова Т.Е.", phone: "доб. 999", email: "urist@dksta.ru"),
        .init(title: "Все контакты на сайте", role: "Полный список подразделений", note: "dksta.ru/kontakty-2", webUri: siteAll),
    ]
}
