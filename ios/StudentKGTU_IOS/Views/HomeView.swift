import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
    @Environment(\.openURL) private var openURL
    @Binding var path: NavigationPath

    @State private var weekType = DateUtils.currentWeekType()
    @State private var nextLesson: Lesson?
    @State private var news: [NewsItem] = []
    @State private var isLoadingLesson = true
    @State private var isLoadingNews = true
    @State private var isRefreshing = false
    @State private var usingCached = false
    @State private var updatedLabel: String?
    @State private var expandedNewsURL: String?
    /// Like Android ViewModel: load once; do not reload when popping back from other screens.
    @State private var didInitialLoad = false

    /// Always from device date (not cached state) — July–August = vacation.
    private var onVacation: Bool { HolidayUtils.isSummerVacation() }

    private var homeBg: Color { theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA }
    private var onHome: Color { theme.isDark ? AppColors.darkOnSurface : .white }
    private var onHomeMuted: Color { theme.isDark ? AppColors.darkOnSurfaceMuted : Color.white.opacity(0.85) }

    private var headerWeekLine: String {
        onVacation ? "Каникулы" : weekType
    }

    var body: some View {
        GeometryReader { geo in
            // Below Dynamic Island, but not too much empty space (~half of previous gap).
            let topInset = geo.safeAreaInsets.top + 12

            ZStack(alignment: .top) {
                homeBg.ignoresSafeArea()

                VStack(spacing: 0) {
                    Color.clear.frame(height: topInset)

                    OfflineBanner(visible: usingCached, updatedLabel: updatedLabel)

                    if !usingCached {
                        UpdatedAtLabel(text: updatedLabel, color: onHomeMuted, extraTop: 0)
                    }

                    HStack {
                        Spacer()
                        Button {
                            theme.toggle()
                        } label: {
                            Image(systemName: theme.isDark ? "sun.max.fill" : "moon.fill")
                                .foregroundStyle(onHome)
                                .padding(8)
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.top, 2)

                    VStack(spacing: 8) {
                        Image("KgtaLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 168, height: 168)
                            .padding(.top, -2)

                        Text(headerWeekLine)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(onHome)

                        if onVacation {
                            // Summer: never show next lesson (even if old cache had pairs)
                            vacationBlock
                        } else if isLoadingLesson {
                            ProgressView().tint(onHome)
                        } else if !prefs.hasGroup {
                            Text("Выберите группу в разделе «Расписание»")
                                .font(.system(size: 13))
                                .foregroundStyle(onHomeMuted)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 12)
                        } else {
                            nextLessonBlock
                        }

                        Spacer(minLength: 8)

                        navButton("Расписание") { path.append(AppRoute.schedule) }
                        navButton("Преподаватели") { path.append(AppRoute.teachers) }
                        navButton("Напоминания") { path.append(AppRoute.reminders) }
                        navButton("Кампус и контакты") { path.append(AppRoute.campus) }

                        newsSection
                            .padding(.top, 8)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .ignoresSafeArea(edges: .top)
        }
        .task {
            // Only first open — returning from Reminders/Schedule must not reload (Android-like).
            guard !didInitialLoad else { return }
            didInitialLoad = true
            await refresh(showLoading: true)
        }
        .onChange(of: prefs.course) { _, _ in
            Task { await refresh(showLoading: false) }
        }
        .onChange(of: prefs.group) { _, _ in
            Task { await refresh(showLoading: false) }
        }
        .onChange(of: prefs.subgroup) { _, _ in
            Task { await refresh(showLoading: false) }
        }
    }

    private var vacationBlock: some View {
        VStack(spacing: 4) {
            Text("Каникулы")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(onHome)
                .multilineTextAlignment(.center)
            Text("Лето · занятия с 1 сентября")
                .font(.system(size: 15))
                .foregroundStyle(onHomeMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var nextLessonBlock: some View {
        VStack(spacing: 4) {
            if let lesson = nextLesson {
                Text("Следующая пара")
                    .font(.system(size: 15))
                    .foregroundStyle(onHomeMuted)
                Text(lesson.subject)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(onHome)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                let details: String = {
                    var s = ""
                    if !lesson.time.isEmpty { s = lesson.time }
                    if !lesson.room.isEmpty {
                        if !s.isEmpty { s += "  •  " }
                        s += "каб. \(lesson.room)"
                    }
                    return s
                }()
                if !details.isEmpty {
                    Text(details).font(.system(size: 15)).foregroundStyle(onHome.opacity(0.9))
                }
                if !lesson.teacher.isEmpty {
                    Text(lesson.teacher)
                        .font(.system(size: 13))
                        .foregroundStyle(onHomeMuted)
                        .lineLimit(1)
                }
            } else {
                Text("Сейчас пар нет")
                    .font(.system(size: 17))
                    .foregroundStyle(onHomeMuted)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func navButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(theme.isDark ? AppColors.darkButton : Color.white)
                .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.blueKGTA)
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(theme.isDark ? AppColors.darkButtonBorder : Color.clear, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .shadow(color: theme.isDark ? .clear : .black.opacity(0.12), radius: 2, y: 1)
        }
        .buttonStyle(.plain)
    }

    private var newsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Новости КГТУ")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(onHome)
                Spacer()
                Text("листайте ↓")
                    .font(.system(size: 12))
                    .foregroundStyle(onHomeMuted)
            }

            let panelBg = theme.isDark ? AppColors.darkCard.opacity(0.65) : Color.white.opacity(0.1)
            if isLoadingNews {
                ZStack {
                    RoundedRectangle(cornerRadius: 14).fill(panelBg)
                    ProgressView().tint(onHome)
                }
                .frame(height: 240)
            } else if news.isEmpty {
                ZStack {
                    RoundedRectangle(cornerRadius: 14).fill(panelBg)
                    Text("Новости пока недоступны")
                        .font(.system(size: 13))
                        .foregroundStyle(onHomeMuted)
                }
                .frame(height: 220)
            } else {
                ScrollView {
                    LazyVStack(spacing: 5) {
                        ForEach(news) { item in
                            newsCard(item)
                        }
                    }
                    .padding(5)
                }
                .frame(height: 220)
                .background(RoundedRectangle(cornerRadius: 14).fill(panelBg))
            }
        }
    }

    private func newsCard(_ item: NewsItem) -> some View {
        let expanded = expandedNewsURL == item.url
        let cardBg = theme.isDark ? AppColors.darkButton : Color.white
        let titleC = theme.isDark ? AppColors.darkOnSurface : .black
        let metaC = theme.isDark ? AppColors.darkOnSurfaceMuted : AppColors.textSecondary
        let bodyC = theme.isDark ? AppColors.darkOnSurfaceMuted : Color(white: 0.25)
        // Android: dark → DarkCard + DarkOnSurface; light → BlueKGTA + White
        let buttonBg = theme.isDark ? AppColors.darkCard : AppColors.blueKGTA
        let buttonFg = theme.isDark ? AppColors.darkOnSurface : Color.white

        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                if !item.imageUrl.isEmpty, let url = URL(string: item.imageUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img):
                            img.resizable().scaledToFill()
                        default:
                            Color.gray.opacity(0.2)
                        }
                    }
                    .frame(width: 46, height: 46)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(titleC)
                        .lineLimit(expanded ? 4 : 2)
                    Text(item.date.isEmpty ? "Дата не указана" : item.date)
                        .font(.caption)
                        .foregroundStyle(metaC)
                }
            }
            .padding(10)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.15)) {
                    expandedNewsURL = expanded ? nil : item.url
                }
            }

            if expanded {
                if !item.description.isEmpty {
                    Text(item.description)
                        .font(.caption)
                        .foregroundStyle(bodyC)
                        .lineLimit(4)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 4)
                }
                if !item.url.isEmpty, let url = URL(string: item.url) {
                    Button {
                        openURL(url)
                    } label: {
                        Text("Подробнее на сайте")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(buttonFg)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(buttonBg)
                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                    .padding(.top, 6)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(theme.isDark ? AppColors.darkButtonBorder : Color.clear, lineWidth: 1)
        )
    }

    private func refresh(showLoading: Bool) async {
        isRefreshing = true
        defer { isRefreshing = false }

        // 1) Instant paint from disk (no network wait)
        applyDiskCacheSnapshot()
        let hadLesson = nextLesson != nil || onVacation
        let hadNews = !news.isEmpty
        if showLoading {
            isLoadingLesson = !hadLesson && !onVacation
            isLoadingNews = !hadNews
        }

        // 2) Summer: only refresh news if online; never show next pair
        if HolidayUtils.isSummerVacation() {
            weekType = "Каникулы"
            nextLesson = nil
            isLoadingLesson = false
            if NetworkMonitor.shared.isOnline {
                let newsMeta = await loadNews()
                usingCached = newsMeta.fromCache
                updatedLabel = TimeFormat.updatedAtLabel(millis: newsMeta.updatedAt) ?? updatedLabel
            } else {
                usingCached = true
                if updatedLabel == nil {
                    let disk = await NewsRepository.shared.getNewsFromCacheOnly(limit: 10)
                    updatedLabel = TimeFormat.updatedAtLabel(millis: disk.updatedAt)
                }
            }
            isLoadingNews = false
            await WidgetUpdater.updateNow()
            return
        }

        weekType = weekType.isEmpty ? DateUtils.currentWeekType() : weekType

        // 3) Offline: keep disk data, show banner, done
        if !NetworkMonitor.shared.isOnline {
            usingCached = true
            isLoadingLesson = false
            isLoadingNews = false
            await WidgetUpdater.updateNow()
            return
        }

        // 4) Online: refresh from server (repositories fall back to cache on error)
        async let lessonMeta = loadLesson()
        async let newsMeta = loadNews()
        let (lm, nm) = await (lessonMeta, newsMeta)
        let latest = max(lm.updatedAt, nm.updatedAt)
        usingCached = lm.fromCache || nm.fromCache
        if let label = TimeFormat.updatedAtLabel(millis: latest) {
            updatedLabel = label
        }
        isLoadingLesson = false
        isLoadingNews = false
        await WidgetUpdater.updateNow()
    }

    /// Fill UI from Application Support cache without any network call.
    private func applyDiskCacheSnapshot() {
        if HolidayUtils.isSummerVacation() {
            weekType = "Каникулы"
            nextLesson = nil
        } else if let group = prefs.group, !group.isEmpty {
            if let cached = ScheduleRepository.shared.getScheduleFromCacheOnly(
                course: prefs.course,
                group: group,
                subgroup: prefs.subgroup
            ), !cached.schedule.isEmpty {
                nextLesson = ScheduleLogic.findNextLesson(schedule: cached.schedule)
                weekType = cached.weekType.isEmpty ? DateUtils.currentWeekType() : cached.weekType
                updatedLabel = TimeFormat.updatedAtLabel(millis: cached.updatedAtMillis) ?? updatedLabel
                usingCached = true
            }
        }
        let diskNews = NewsRepository.shared.getNewsFromCacheOnly(limit: 10)
        if !diskNews.news.isEmpty {
            news = diskNews.news
            if let label = TimeFormat.updatedAtLabel(millis: diskNews.updatedAt) {
                updatedLabel = label
            }
            usingCached = true
        }
    }

    private func loadLesson() async -> LoadMeta {
        if HolidayUtils.isSummerVacation() {
            weekType = "Каникулы"
            nextLesson = nil
            return LoadMeta(fromCache: false, updatedAt: 0)
        }
        guard let group = prefs.group, !group.isEmpty else {
            nextLesson = nil
            return LoadMeta(fromCache: false, updatedAt: 0)
        }
        let result = await ScheduleRepository.shared.getSchedule(
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup
        )
        if !result.schedule.isEmpty || nextLesson == nil {
            nextLesson = ScheduleLogic.findNextLesson(schedule: result.schedule)
        }
        if !result.weekType.isEmpty {
            weekType = result.weekType
        }
        return LoadMeta(fromCache: result.isOffline, updatedAt: result.updatedAtMillis)
    }

    private func loadNews() async -> LoadMeta {
        let r = await NewsRepository.shared.getNews(limit: 10)
        if !r.news.isEmpty || news.isEmpty {
            news = r.news
        }
        return LoadMeta(fromCache: r.fromCache, updatedAt: r.updatedAt)
    }
}
