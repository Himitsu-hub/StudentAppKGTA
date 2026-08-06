import SwiftUI

struct ScheduleView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var cache: ScheduleSessionCache
    @Environment(\.dismiss) private var dismiss

    @State private var showCourse = false
    @State private var showGroup = false
    @State private var showSubgroup = false

    private var bg: Color { theme.isDark ? AppColors.darkNavy : AppColors.surfaceLight }
    private var accent: Color { theme.isDark ? AppColors.darkOnSurface : AppColors.blueKGTA }
    private var muted: Color { theme.isDark ? AppColors.darkOnSurfaceMuted : AppColors.textSecondary }

    var body: some View {
        VStack(spacing: 0) {
            AppTopBar(
                title: "Расписание",
                onBack: { dismiss() },
                onRefresh: { Task { await cache.load(prefs: prefs, force: true) } },
                isDark: theme.isDark
            )
            OfflineBanner(visible: cache.usingCached, updatedLabel: cache.updatedLabel)
            if !cache.usingCached {
                UpdatedAtLabel(text: cache.updatedLabel, color: muted)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Неделя: \(cache.weekType)")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(accent)

                    Text("Нажмите, чтобы сменить (курс / группа / подгруппа)")
                        .font(.caption)
                        .foregroundStyle(muted)

                    selectionChip("Курс: \(prefs.course)") { showCourse = true }
                    selectionChip("Группа: \(prefs.group ?? "выбрать")") {
                        if !cache.groups.isEmpty { showGroup = true }
                    }
                    selectionChip("Подгруппа: \(prefs.subgroup ?? "выбрать")") {
                        if let g = prefs.group, !(cache.groups[g] ?? []).isEmpty {
                            showSubgroup = true
                        }
                    }

                    if cache.isLoading && cache.schedule.isEmpty {
                        LoadingState(message: "Загружаем расписание…")
                    } else if let error = cache.error, cache.schedule.isEmpty {
                        ErrorState(message: error) {
                            Task { await cache.load(prefs: prefs, force: true) }
                        }
                    } else if cache.schedule.isEmpty {
                        EmptyStateView(title: "Нет занятий", subtitle: "Для выбранной группы расписание отсутствует")
                    } else {
                        ForEach(cache.schedule) { day in
                            dayCard(day)
                        }
                    }
                }
                .padding(16)
            }
            .refreshable { await cache.load(prefs: prefs, force: true) }
        }
        .background(bg.ignoresSafeArea())
        .hideSystemNavBar()
        .swipeBack { dismiss() }
        .task {
            // No spinner if session cache already has this group (Android-like)
            await cache.load(prefs: prefs, force: false)
        }
        .confirmationDialog("Выберите курс", isPresented: $showCourse, titleVisibility: .visible) {
            ForEach(1...4, id: \.self) { c in
                Button("\(c) курс") {
                    prefs.save(course: c, group: nil, subgroup: nil)
                    cache.invalidate()
                    Task {
                        await cache.load(prefs: prefs, force: true)
                        if prefs.group == nil, let first = cache.groups.keys.sorted().first {
                            prefs.save(course: c, group: first, subgroup: cache.groups[first]?.first)
                            await cache.load(prefs: prefs, force: true)
                        }
                    }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Выберите группу", isPresented: $showGroup, titleVisibility: .visible) {
            ForEach(cache.groups.keys.sorted(), id: \.self) { name in
                Button(name) {
                    let sub = cache.groups[name]?.first
                    prefs.save(course: prefs.course, group: name, subgroup: sub)
                    cache.invalidate()
                    Task { await cache.load(prefs: prefs, force: true) }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Подгруппа", isPresented: $showSubgroup, titleVisibility: .visible) {
            ForEach(cache.groups[prefs.group ?? ""] ?? [], id: \.self) { sub in
                Button(sub) {
                    prefs.save(course: prefs.course, group: prefs.group, subgroup: sub)
                    cache.invalidate()
                    Task { await cache.load(prefs: prefs, force: true) }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
    }

    private func selectionChip(_ text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.body.weight(.medium))
                .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : .white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(theme.isDark ? AppColors.darkButton : AppColors.blueKGTA)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private func dayCard(_ day: ScheduleDay) -> some View {
        let cardBg = theme.isDark ? AppColors.darkCard : Color.white
        let holidayName = HolidayUtils.holidayName(DateUtils.dateForDay(day.dayName))
        let isHolidayLesson = day.lessons.contains(where: { $0.type.lowercased() == "праздник" })
        return VStack(alignment: .leading, spacing: 10) {
            Text(day.dayName)
                .font(.title3.weight(.bold))
                .foregroundStyle(accent)
            if holidayName != nil || isHolidayLesson {
                Text(
                    holidayName
                        ?? day.lessons.first(where: { $0.type.lowercased() == "праздник" })?.subject
                        ?? "Праздничный день"
                )
                .foregroundStyle(AppColors.errorRed)
                .fontWeight(.bold)
                Text("Занятий нет").foregroundStyle(muted)
            } else if day.lessons.isEmpty {
                Text("Пар нет").foregroundStyle(muted)
            } else {
                ForEach(day.lessons) { lesson in
                    lessonCard(lesson)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(theme.isDark ? 0 : 0.06), radius: 2, y: 1)
    }

    private func lessonCard(_ lesson: Lesson) -> some View {
        let lessonBg = theme.isDark ? AppColors.darkButton : AppColors.lessonBgLight
        let typeColor: Color = {
            switch lesson.type.lowercased() {
            case "лекция": return theme.isDark ? Color(red: 0.39, green: 0.71, blue: 0.96) : Color(red: 0.1, green: 0.46, blue: 0.82)
            case "практика": return theme.isDark ? Color(red: 0.5, green: 0.78, blue: 0.52) : Color(red: 0.22, green: 0.56, blue: 0.24)
            case "лабораторная": return theme.isDark ? Color(red: 1, green: 0.72, blue: 0.3) : Color(red: 0.96, green: 0.49, blue: 0)
            default: return muted
            }
        }()
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(lesson.time).fontWeight(.bold).foregroundStyle(accent)
                Spacer()
                Text(lesson.type).font(.caption.weight(.medium)).foregroundStyle(typeColor)
            }
            Text(lesson.subject).fontWeight(.semibold)
                .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
            if !lesson.teacher.isEmpty {
                Text(lesson.teacher).foregroundStyle(muted)
            }
            if !lesson.room.isEmpty {
                Text("Аудитория: \(lesson.room)").foregroundStyle(muted)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(lessonBg)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
