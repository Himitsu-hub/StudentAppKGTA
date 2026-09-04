import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @EnvironmentObject private var theme: ThemeManager
    @Environment(\.openURL) private var openURL
    @Binding var path: NavigationPath

    @State private var weekType = DateUtils.currentWeekType()
    @State private var nextLesson: Lesson?
    @State private var nextLessonDayName: String?
    @State private var nextLessonIsToday = true
    @State private var news: [NewsItem] = []
    @State private var isLoadingLesson = true
    @State private var isLoadingNews = true
    @State private var isRefreshing = false
    @State private var usingCached = false
    @State private var updatedLabel: String?
    @State private var expandedNewsURL: String?
    /// Like Android ViewModel: load once; do not reload when popping back from other screens.
    @State private var didInitialLoad = false
    /// Manual pull-down (screen itself stays fixed — no free scroll).
    @State private var pullDistance: CGFloat = 0
    @State private var isPullRefreshing = false

    /// Always from device date (not cached state) — July–August = vacation.
    private var onVacation: Bool { HolidayUtils.isSummerVacation() }

    private let pullThreshold: CGFloat = 80

    private var homeBg: Color { theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA }
    private var onHome: Color { theme.isDark ? AppColors.darkOnSurface : .white }
    private var onHomeMuted: Color { theme.isDark ? AppColors.darkOnSurfaceMuted : Color.white.opacity(0.85) }

    private var headerWeekLine: String {
        onVacation ? "Каникулы" : weekType
    }

    var body: some View {
        GeometryReader { geo in
            // Below Dynamic Island — slightly tighter so lesson/buttons/news sit a bit higher.
            let topInset = geo.safeAreaInsets.top + 4
            let pullY = isPullRefreshing ? pullThreshold * 0.55 : max(0, pullDistance * 0.35)

            ZStack(alignment: .top) {
                homeBg.ignoresSafeArea()

                // Fixed layout — whole home does not scroll.
                // Pull-to-refresh is ONLY on the upper chrome (logo / nav), never on news list.
                VStack(spacing: 0) {
                    Color.clear.frame(height: topInset)

                    VStack(spacing: 0) {
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

                        VStack(spacing: 6) {
                            Image("KgtaLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 168, height: 168)
                                .padding(.top, -6)

                            Text(headerWeekLine)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(onHome)

                            if onVacation {
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

                            Spacer(minLength: 2)

                            navButton("Расписание") { path.append(AppRoute.schedule) }
                            navButton("Преподаватели") { path.append(AppRoute.teachers) }
                            navButton("Напоминания") { path.append(AppRoute.reminders) }
                            navButton("Кампус и контакты") { path.append(AppRoute.campus) }
                        }
                        .padding(.horizontal, 16)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .contentShape(Rectangle())
                    // Only this upper zone accepts pull-to-refresh (not the news box).
                    .simultaneousGesture(homePullGesture)

                    newsSection
                        .padding(.horizontal, 16)
                        .padding(.top, 2)
                        .padding(.bottom, 10)
                }
                .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
                .offset(y: pullY)

                if pullDistance > 12 || isPullRefreshing {
                    ProgressView()
                        .tint(onHome)
                        .scaleEffect(1.05)
                        .padding(.top, topInset + 4)
                        .opacity(isPullRefreshing ? 1 : Double(min(1, pullDistance / pullThreshold)))
                }
            }
            .ignoresSafeArea(edges: .top)
        }
        .task {
            // Only first open — returning from Reminders/Schedule must not reload (Android-like).
            guard !didInitialLoad else { return }
            didInitialLoad = true
            await refresh(showLoading: true)
            // Poll news every ~60s while home is open (server keeps cache fresh)
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
                if NetworkMonitor.shared.isOnline {
                    let meta = await loadNews(force: false)
                    if meta.updatedAt > 0 {
                        updatedLabel = TimeFormat.updatedAtLabel(millis: meta.updatedAt) ?? updatedLabel
                    }
                    usingCached = meta.fromCache ? true : usingCached
                    _ = await NewsUpdateChecker.check(notify: true)
                }
            }
        }
        .onChange(of: prefs.faculty) { _, _ in
            Task { await refresh(showLoading: false) }
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

    /// Pull-down refresh on the UPPER chrome only (logo / buttons). News scrolling never triggers this.
    private var homePullGesture: some Gesture {
        DragGesture(minimumDistance: 20, coordinateSpace: .local)
            .onChanged { value in
                guard !isPullRefreshing else { return }
                let dy = value.translation.height
                // Require a mostly-vertical downward pull (ignore horizontal / tiny jiggle).
                guard dy > 0, abs(value.translation.width) < dy * 0.85 else {
                    if pullDistance > 0 { pullDistance = 0 }
                    return
                }
                pullDistance = dy < pullThreshold ? dy : pullThreshold + (dy - pullThreshold) * 0.2
            }
            .onEnded { value in
                guard !isPullRefreshing else { return }
                let dy = value.translation.height
                if dy >= pullThreshold, abs(value.translation.width) < dy * 0.85 {
                    isPullRefreshing = true
                    pullDistance = pullThreshold
                    Task {
                        // Always stop the spinner — even if API is slow/down
                        defer {
                            Task { @MainActor in
                                withAnimation(.easeOut(duration: 0.2)) {
                                    isPullRefreshing = false
                                    pullDistance = 0
                                }
                            }
                        }
                        await refresh(showLoading: false)
                        // One light pass without force (cache-first on server)
                        _ = await loadNews(force: false)
                        if let label = TimeFormat.updatedAtLabel(
                            millis: NewsRepository.shared.getNewsFromCacheOnly(limit: 1).updatedAt
                        ) {
                            updatedLabel = label
                        }
                    }
                } else {
                    withAnimation(.easeOut(duration: 0.2)) {
                        pullDistance = 0
                    }
                }
            }
    }

    /// Subtitle only — «Каникулы» already shown once in the week header line.
    private var vacationBlock: some View {
        Text("Лето · занятия с 1 сентября")
            .font(.system(size: 15))
            .foregroundStyle(onHomeMuted)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private var nextLessonBlock: some View {
        VStack(spacing: 4) {
            if let lesson = nextLesson {
                let heading: String = {
                    if nextLessonIsToday { return "Следующая пара" }
                    if isTomorrow(dayName: nextLessonDayName) { return "Завтра" }
                    if let d = nextLessonDayName, !d.isEmpty { return d }
                    return "Ближайшая пара"
                }()
                Text(heading)
                    .font(.system(size: 15))
                    .foregroundStyle(onHomeMuted)
                if !nextLessonIsToday, isTomorrow(dayName: nextLessonDayName), let d = nextLessonDayName {
                    Text(d)
                        .font(.system(size: 12))
                        .foregroundStyle(onHomeMuted.opacity(0.85))
                }
                Text(lesson.subject)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(onHome)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                let isOnline = lesson.room.localizedCaseInsensitiveContains("онлайн")
                let details: String = {
                    var s = ""
                    if !lesson.time.isEmpty { s = lesson.time }
                    if !lesson.room.isEmpty {
                        if !s.isEmpty { s += "  •  " }
                        if isOnline && lesson.room.lowercased() == "онлайн" {
                            s += "онлайн"
                        } else if isOnline {
                            s += lesson.room
                        } else {
                            s += "каб. \(lesson.room)"
                        }
                    }
                    return s
                }()
                if !details.isEmpty {
                    Text(details)
                        .font(.system(size: 15))
                        .foregroundStyle(isOnline ? Color(red: 1, green: 0.75, blue: 0.7) : onHome.opacity(0.9))
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
                if !item.imageUrl.isEmpty {
                    CachedAsyncImage(urlString: item.imageUrl) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Color.gray.opacity(0.2)
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

        // Always: paint from disk FIRST — never wait for network to show known data
        applyDiskCacheSnapshot()
        isLoadingLesson = false
        isLoadingNews = false

        let offline = !NetworkMonitor.shared.isOnline

        // Airplane / no network: stop here with cached UI (or empty if first launch offline)
        if offline {
            usingCached = nextLesson != nil || !news.isEmpty || onVacation
            if onVacation {
                weekType = "Каникулы"
                nextLesson = nil
            }
            await WidgetUpdater.updateNow()
            return
        }

        // Online only: background refresh (may update UI when done)
        if showLoading && nextLesson == nil && !onVacation {
            isLoadingLesson = true
        }
        if showLoading && news.isEmpty {
            isLoadingNews = true
        }

        if HolidayUtils.isSummerVacation() {
            weekType = "Каникулы"
            nextLesson = nil
            isLoadingLesson = false
            let newsMeta = await loadNews(force: true)
            usingCached = !NetworkMonitor.shared.isOnline
            updatedLabel = TimeFormat.updatedAtLabel(millis: newsMeta.updatedAt) ?? updatedLabel
            isLoadingNews = false
            await WidgetUpdater.updateNow()
            return
        }

        async let lessonMeta = loadLesson()
        async let newsMeta = loadNews(force: true)
        let (lm, nm) = await (lessonMeta, newsMeta)
        let latest = max(lm.updatedAt, nm.updatedAt)
        // Banner only if truly offline — not when online with any cached fallback
        usingCached = !NetworkMonitor.shared.isOnline
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
                faculty: prefs.faculty,
                course: prefs.course,
                group: group,
                subgroup: prefs.subgroup
            ), !cached.schedule.isEmpty {
                if let info = ScheduleLogic.findNextLessonInfo(schedule: cached.schedule) {
                    nextLesson = info.lesson
                    nextLessonDayName = info.dayName
                    nextLessonIsToday = info.isToday
                } else {
                    nextLesson = nil
                    nextLessonDayName = nil
                    nextLessonIsToday = true
                }
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
            faculty: prefs.faculty,
            course: prefs.course,
            group: group,
            subgroup: prefs.subgroup
        )
        if !result.schedule.isEmpty || nextLesson == nil {
            if let info = ScheduleLogic.findNextLessonInfo(schedule: result.schedule) {
                nextLesson = info.lesson
                nextLessonDayName = info.dayName
                nextLessonIsToday = info.isToday
            } else {
                nextLesson = nil
                nextLessonDayName = nil
                nextLessonIsToday = true
            }
        }
        if !result.weekType.isEmpty {
            weekType = result.weekType
        }
        return LoadMeta(fromCache: result.isOffline, updatedAt: result.updatedAtMillis)
    }

    private func isTomorrow(dayName: String?) -> Bool {
        guard let dayName, !dayName.isEmpty else { return false }
        let days = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"]
        let today = DateUtils.todayName()
        guard let todayIdx = days.firstIndex(where: { $0.caseInsensitiveCompare(today) == .orderedSame }) else {
            return false
        }
        let tomorrow = days[(todayIdx + 1) % days.count]
        return dayName.caseInsensitiveCompare(tomorrow) == .orderedSame
    }

    private func loadNews(force: Bool = false) async -> LoadMeta {
        let r = await NewsRepository.shared.getNews(limit: 15, force: force)
        // Prefer non-empty remote; never wipe good cache with empty response
        if !r.news.isEmpty {
            news = r.news
        } else if news.isEmpty {
            let disk = NewsRepository.shared.getNewsFromCacheOnly(limit: 15)
            news = disk.news
        }
        ImageDiskCache.prefetch(urls: news.map(\.imageUrl))
        return LoadMeta(fromCache: r.fromCache, updatedAt: r.updatedAt)
    }
}
