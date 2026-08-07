import SwiftUI

/// Self-contained list + detail (no NavigationStack pop glitch).
struct TeachersView: View {
    @EnvironmentObject private var theme: ThemeManager
    @Environment(\.dismiss) private var dismiss

    @State private var teachers: [Teacher] = []
    @State private var filtered: [Teacher] = []
    @State private var departments: [String] = ["Все"]
    @State private var selectedDept = "Все"
    @State private var query = ""
    @State private var isLoading = true
    @State private var usingCached = false
    @State private var error: String?
    /// In-place detail — avoids shrink animation when popping NavigationPath.
    @State private var selectedTeacherName: String?

    private var bg: Color { theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA }
    private var onBlue: Bool { !theme.isDark }

    var body: some View {
        Group {
            if let name = selectedTeacherName {
                TeacherDetailView(teacherName: name, teachers: teachers) {
                    selectedTeacherName = nil
                }
            } else {
                listBody
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(bg.ignoresSafeArea())
        .hideSystemNavBar()
        .swipeBack {
            if selectedTeacherName != nil {
                selectedTeacherName = nil
            } else {
                dismiss()
            }
        }
        .task {
            if teachers.isEmpty {
                await load()
            }
        }
    }

    private var listBody: some View {
        VStack(spacing: 0) {
            AppTopBar(
                title: "Преподаватели",
                onBack: { dismiss() },
                onRefresh: { Task { await load() } },
                isDark: theme.isDark
            )
            OfflineBanner(visible: usingCached)

            VStack(alignment: .leading, spacing: 12) {
                TextField("Поиск по имени, предмету…", text: $query)
                    .textFieldStyle(.plain)
                    .padding(12)
                    .background(theme.isDark ? AppColors.darkCard : Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .onChange(of: query) { _, _ in applyFilter() }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(departments, id: \.self) { dept in
                            let selected = selectedDept == dept
                            Button {
                                selectedDept = dept
                                applyFilter()
                            } label: {
                                Text(dept)
                                    .font(.caption)
                                    .lineLimit(1)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(
                                        selected
                                        ? (onBlue ? Color.white : AppColors.blueKGTA)
                                        : (onBlue ? Color.white.opacity(0.2) : AppColors.darkButton)
                                    )
                                    .foregroundStyle(
                                        selected
                                        ? (onBlue ? AppColors.blueKGTA : .white)
                                        : (onBlue ? .white : AppColors.darkOnSurfaceMuted)
                                    )
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Text("\(filtered.count) преподавателей")
                    .font(.caption)
                    .foregroundStyle(onBlue ? Color.white.opacity(0.75) : AppColors.darkOnSurfaceMuted)

                if isLoading && teachers.isEmpty {
                    LoadingState(message: "Загружаем преподавателей…", lightOnBlue: onBlue)
                } else if let error, teachers.isEmpty {
                    ErrorState(message: error) { Task { await load() } }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(filtered) { teacher in
                                teacherRow(teacher)
                            }
                        }
                        .padding(.bottom, 16)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func teacherRow(_ teacher: Teacher) -> some View {
        Button {
            selectedTeacherName = teacher.name
        } label: {
            HStack(spacing: 14) {
                avatar(teacher, size: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text(teacher.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    if !teacher.position.isEmpty {
                        Text(teacher.position)
                            .font(.caption)
                            .foregroundStyle(AppColors.textSecondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(14)
            .background(theme.isDark ? AppColors.darkCard : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func avatar(_ teacher: Teacher, size: CGFloat) -> some View {
        if !teacher.photoUrl.isEmpty, let url = URL(string: teacher.photoUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let img):
                    img.resizable().scaledToFill()
                default:
                    placeholderAvatar(teacher, size: size)
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
        } else {
            placeholderAvatar(teacher, size: size)
        }
    }

    private func placeholderAvatar(_ teacher: Teacher, size: CGFloat) -> some View {
        ZStack {
            Circle().fill(AppColors.blueKGTA.opacity(0.15))
            Text(String(teacher.name.prefix(1)))
                .font(.system(size: size / 2.5, weight: .bold))
                .foregroundStyle(AppColors.blueKGTA)
        }
        .frame(width: size, height: size)
    }

    private func load() async {
        // Instant disk cache so offline reopen works
        if teachers.isEmpty {
            let disk = TeachersRepository.shared.getTeachersFromCacheOnly()
            if !disk.isEmpty {
                teachers = disk
                usingCached = true
                departments = ["Все"] + TeacherUtils.departments(teachers)
                applyFilter()
            }
        }

        if !NetworkMonitor.shared.isOnline {
            isLoading = false
            usingCached = !teachers.isEmpty
            error = teachers.isEmpty ? "Нет сети и нет сохранённого списка" : nil
            return
        }

        let showSpinner = teachers.isEmpty
        if showSpinner { isLoading = true }
        error = nil
        defer { isLoading = false }
        let r = await TeachersRepository.shared.getTeachers()
        if !r.teachers.isEmpty || teachers.isEmpty {
            teachers = r.teachers
        }
        usingCached = r.fromCache
        departments = ["Все"] + TeacherUtils.departments(teachers)
        if !departments.contains(selectedDept) { selectedDept = "Все" }
        applyFilter()
        if teachers.isEmpty { error = "Не удалось загрузить преподавателей" }
    }

    private func applyFilter() {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        filtered = teachers.filter { t in
            let dept = TeacherUtils.extractDepartment(t)
            let deptOk: Bool = {
                if selectedDept == "Все" { return true }
                if selectedDept == "Другие" { return dept.isEmpty }
                return dept == selectedDept
            }()
            guard deptOk else { return false }
            if q.isEmpty { return true }
            let hay = (t.name + " " + t.position + " " + t.subjectList.joined(separator: " ")).lowercased()
            return hay.contains(q)
        }
    }
}

struct TeacherDetailView: View {
    let teacherName: String
    var teachers: [Teacher] = []
    var onBack: () -> Void

    @EnvironmentObject private var theme: ThemeManager
    @State private var teacher: Teacher?
    @State private var isLoading = true
    @State private var copied = false

    private var bg: Color { theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA }

    var body: some View {
        VStack(spacing: 0) {
            AppTopBar(title: "Преподаватель", onBack: onBack, isDark: theme.isDark)
            if isLoading && teacher == nil {
                Spacer()
                LoadingState(lightOnBlue: !theme.isDark)
                Spacer()
            } else if let teacher {
                ScrollView {
                    VStack(spacing: 16) {
                        avatar(teacher, size: 120)
                        Text(teacher.name)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : .white)
                            .multilineTextAlignment(.center)
                        Text(teacher.position)
                            .foregroundStyle(theme.isDark ? AppColors.darkOnSurfaceMuted : Color.white.opacity(0.85))
                            .multilineTextAlignment(.center)

                        if !teacher.email.isEmpty {
                            Button {
                                copyToClipboard(teacher.email)
                                copied = true
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Email").font(.caption).foregroundStyle(.secondary)
                                    Text(teacher.email)
                                        .fontWeight(.medium)
                                        .foregroundStyle(AppColors.blueKGTA)
                                    Text(copied ? "Скопировано" : "Нажмите, чтобы скопировать")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(16)
                                .background(theme.isDark ? AppColors.darkCard : Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }

                        if !teacher.subjectList.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Предметы").font(.headline)
                                ForEach(teacher.subjectList, id: \.self) { s in
                                    Text("• \(s)").font(.subheadline)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                            .background(theme.isDark ? AppColors.darkCard : Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
                        }
                    }
                    .padding(16)
                }
            } else {
                Spacer()
                Text("Преподаватель не найден")
                    .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : .white)
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(bg.ignoresSafeArea())
        .task {
            if let t = teachers.first(where: { $0.name == teacherName }) {
                teacher = t
                isLoading = false
            } else {
                let r = await TeachersRepository.shared.getTeachers()
                teacher = r.teachers.first { $0.name == teacherName }
                isLoading = false
            }
        }
    }

    @ViewBuilder
    private func avatar(_ teacher: Teacher, size: CGFloat) -> some View {
        if !teacher.photoUrl.isEmpty, let url = URL(string: teacher.photoUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let img): img.resizable().scaledToFill()
                default:
                    Circle().fill(Color.white.opacity(0.2))
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
        } else {
            ZStack {
                Circle().fill(Color.white.opacity(0.2))
                Text(String(teacher.name.prefix(1)))
                    .font(.system(size: size / 2.5, weight: .bold))
                    .foregroundStyle(.white)
            }
            .frame(width: size, height: size)
        }
    }

    private func copyToClipboard(_ text: String) {
        #if canImport(UIKit)
        UIKit.UIPasteboard.general.string = text
        #elseif canImport(AppKit)
        let pb = AppKit.NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
        #endif
    }
}
