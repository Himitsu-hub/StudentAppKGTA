import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var prefs: UserPreferences
    @State private var course: Int = 1
    @State private var groups: [String: [String]] = [:]
    @State private var group: String?
    @State private var subgroup: String?
    @State private var loading = false
    @State private var saving = false
    @State private var error: String?

    var canFinish: Bool {
        !(group ?? "").isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Spacer().frame(height: 12)
                Text("Добро пожаловать")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(.white)
                Text("Сначала выберите курс, группу и подгруппу.\nЭто нужно один раз — потом сразу откроется ваше расписание.\n\nСменить можно в любой момент в разделе «Расписание».")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)

                stepTitle("1. Курс")
                HStack(spacing: 8) {
                    ForEach(1...4, id: \.self) { c in
                        ChoiceChip(label: "\(c) курс", selected: course == c) {
                            course = c
                            Task { await loadGroups() }
                        }
                    }
                }

                stepTitle("2. Группа")
                if loading {
                    ProgressView().tint(.white)
                } else if groups.isEmpty {
                    Text("Группы не загрузились. Проверьте интернет.")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    Button("Обновить") { Task { await loadGroups() } }
                        .foregroundStyle(.white)
                } else {
                    VStack(spacing: 8) {
                        ForEach(groups.keys.sorted(), id: \.self) { name in
                            ChoiceChip(label: name, selected: group == name, fullWidth: true) {
                                group = name
                                subgroup = groups[name]?.first
                            }
                        }
                    }
                }

                if let group, !group.isEmpty {
                    stepTitle("3. Подгруппа")
                    let subs = groups[group] ?? []
                    if subs.isEmpty {
                        Text("Подгруппы не указаны — будет общая группа")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.8))
                    } else {
                        VStack(spacing: 8) {
                            ForEach(subs, id: \.self) { sub in
                                ChoiceChip(label: sub, selected: subgroup == sub, fullWidth: true) {
                                    subgroup = sub
                                }
                            }
                        }
                    }
                }

                if let error {
                    Text(error).font(.caption).foregroundStyle(Color(red: 1, green: 0.7, blue: 0.66))
                }

                Button {
                    Task { await finish() }
                } label: {
                    Group {
                        if saving {
                            ProgressView()
                        } else {
                            Text("Начать").font(.system(size: 17, weight: .bold))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(canFinish ? Color.white : Color.white.opacity(0.4))
                    .foregroundStyle(AppColors.blueKGTA)
                    .clipShape(Capsule())
                }
                .disabled(!canFinish || saving)
                .padding(.top, 12)
            }
            .padding(20)
        }
        .background(AppColors.blueKGTA.ignoresSafeArea())
        .task { await loadGroups() }
    }

    private func stepTitle(_ t: String) -> some View {
        Text(t)
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }

    private func loadGroups() async {
        loading = true
        error = nil
        defer { loading = false }
        groups = await ScheduleRepository.shared.getGroups(course: course)
        if groups.isEmpty {
            error = "Не удалось загрузить группы"
            group = nil
            subgroup = nil
        } else {
            if group == nil || groups[group ?? ""] == nil {
                group = groups.keys.sorted().first
            }
            if let group {
                subgroup = groups[group]?.first
            }
        }
    }

    private func finish() async {
        guard let group else { return }
        saving = true
        prefs.save(course: course, group: group, subgroup: subgroup)
        saving = false
    }
}
