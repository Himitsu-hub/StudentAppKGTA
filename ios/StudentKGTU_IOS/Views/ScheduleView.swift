import SwiftUI

struct ScheduleView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var cache: ScheduleSessionCache
    @Environment(\.dismiss) private var dismiss

    @State private var showFaculty = false
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

                    Text("Факультет / курс / группа / подгруппа")
                        .font(.caption2)
                        .foregroundStyle(muted)

                    HStack(spacing: 8) {
                        selectionChip("\(prefs.facultyShort)", compact: true) { showFaculty = true }
                        selectionChip("\(prefs.course) курс", compact: true) { showCourse = true }
                    }
                    selectionChip("Группа: \(prefs.group ?? "выбрать")", compact: true) {
                        if !cache.groups.isEmpty { showGroup = true }
                    }
                    selectionChip("Подгруппа: \(prefs.subgroup ?? "выбрать")", compact: true) {
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
        .sheet(isPresented: $showFaculty) {
            schedulePickerSheet(
                title: "Факультет",
                labels: FacultyCatalog.all.map { "\($0.short) — \(FacultyCatalog.fullName($0.id))" }
            ) { index in
                let fid = FacultyCatalog.all[index].id
                // Keep previous group until new list loads — avoids Onboarding flash.
                Task {
                    let loaded = await ScheduleRepository.shared.getGroups(faculty: fid, course: 1)
                    cache.setGroups(loaded)
                    let first = loaded.keys.sorted().first
                    prefs.save(
                        faculty: fid,
                        course: 1,
                        group: first,
                        subgroup: first.flatMap { loaded[$0]?.first }
                    )
                    cache.invalidate()
                    await cache.load(prefs: prefs, force: true)
                }
            }
        }
        .sheet(isPresented: $showCourse) {
            let courses = FacultyCatalog.courses(for: prefs.faculty)
            schedulePickerSheet(
                title: "Выберите курс",
                labels: courses.map { "\($0) курс" }
            ) { index in
                let c = courses[index]
                let fac = prefs.faculty
                Task {
                    let loaded = await ScheduleRepository.shared.getGroups(faculty: fac, course: c)
                    cache.setGroups(loaded)
                    let first = loaded.keys.sorted().first
                    prefs.save(
                        faculty: fac,
                        course: c,
                        group: first,
                        subgroup: first.flatMap { loaded[$0]?.first }
                    )
                    cache.invalidate()
                    await cache.load(prefs: prefs, force: true)
                }
            }
        }
        .sheet(isPresented: $showGroup) {
            let names = cache.groups.keys.sorted()
            schedulePickerSheet(title: "Выберите группу", labels: names) { index in
                let name = names[index]
                let sub = cache.groups[name]?.first
                prefs.save(faculty: prefs.faculty, course: prefs.course, group: name, subgroup: sub)
                cache.invalidate()
                Task { await cache.load(prefs: prefs, force: true) }
            }
        }
        .sheet(isPresented: $showSubgroup) {
            let subs = cache.groups[prefs.group ?? ""] ?? []
            schedulePickerSheet(title: "Подгруппа", labels: subs) { index in
                let sub = subs[index]
                prefs.save(faculty: prefs.faculty, course: prefs.course, group: prefs.group, subgroup: sub)
                cache.invalidate()
                Task { await cache.load(prefs: prefs, force: true) }
            }
        }
    }

    /// High-contrast picker: navy sheet + pure white labels (never muted system blue).
    private func schedulePickerSheet(
        title: String,
        labels: [String],
        onPick: @escaping (Int) -> Void
    ) -> some View {
        // Slightly lighter than darkButton so white text is clearly readable.
        let rowBg = Color(red: 0x2E / 255, green: 0x3D / 255, blue: 0x5C / 255)
        return NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                        Button {
                            onPick(index)
                            showFaculty = false
                            showCourse = false
                            showGroup = false
                            showSubgroup = false
                        } label: {
                            Text(label)
                                .font(.body.weight(.bold))
                                .foregroundStyle(Color.white)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 18)
                                .background(rowBg)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(AppColors.darkButtonBorder, lineWidth: 1)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(AppColors.darkNavy.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(Color.white)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") {
                        showFaculty = false
                        showCourse = false
                        showGroup = false
                        showSubgroup = false
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.white)
                }
            }
            .toolbarBackground(AppColors.darkNavy, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .preferredColorScheme(.dark)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColors.darkNavy)
    }

    private func selectionChip(_ text: String, compact: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font((compact ? Font.subheadline : Font.body).weight(.semibold))
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, compact ? 12 : 16)
                .padding(.vertical, compact ? 10 : 14)
                .background(theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA)
                .overlay(
                    RoundedRectangle(cornerRadius: compact ? 10 : 12)
                        .stroke(theme.isDark ? AppColors.darkButtonBorder : Color.clear, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: compact ? 10 : 12))
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
        let isOnline = lesson.room.localizedCaseInsensitiveContains("онлайн")
            || lesson.subject.localizedCaseInsensitiveContains("онлайн")
            || lesson.teacher.localizedCaseInsensitiveContains("онлайн")
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
                if isOnline {
                    Text("ОНЛАЙН")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(red: 0.91, green: 0.38, blue: 0.30))
                        .clipShape(Capsule())
                }
                Text(lesson.type).font(.caption.weight(.medium)).foregroundStyle(typeColor)
            }
            Text(lesson.subject).fontWeight(.semibold)
                .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
            if !lesson.teacher.isEmpty {
                Text(lesson.teacher).foregroundStyle(muted)
            }
            if !lesson.room.isEmpty {
                Text(isOnline && lesson.room.lowercased() == "онлайн" ? "Формат: онлайн" : (isOnline ? "Аудитория / формат: \(lesson.room)" : "Аудитория: \(lesson.room)"))
                    .foregroundStyle(isOnline ? Color(red: 0.91, green: 0.38, blue: 0.30) : muted)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(lessonBg)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
