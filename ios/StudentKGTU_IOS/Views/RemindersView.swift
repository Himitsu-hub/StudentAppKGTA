import SwiftUI

struct RemindersView: View {
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var store: RemindersStore
    @Environment(\.dismiss) private var dismiss

    @State private var editor: Reminder?
    @State private var completeCandidate: Reminder?
    @State private var draftText = ""
    @State private var draftDate = Date().addingTimeInterval(3600)

    private var bg: Color { theme.isDark ? AppColors.darkNavy : AppColors.blueKGTA }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppTopBar(title: "Напоминания", onBack: { dismiss() }, isDark: theme.isDark)

                if store.reminders.isEmpty {
                    Spacer()
                    EmptyStateView(title: "Нет напоминаний", subtitle: "Нажмите +, чтобы добавить")
                        .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : .white)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(store.reminders) { r in
                                reminderRow(r)
                            }
                        }
                        .padding(16)
                        .padding(.bottom, 72)
                    }
                }
            }

            Button {
                Task {
                    _ = await store.requestPermission()
                    let id = UUID().uuidString
                    editor = Reminder(id: id, text: "", dateTimeMillis: Date().addingTimeInterval(3600).timeIntervalSince1970 * 1000)
                    draftText = ""
                    draftDate = Date().addingTimeInterval(3600)
                }
            } label: {
                Image(systemName: "plus")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.blueKGTA)
                    .frame(width: 56, height: 56)
                    .background(theme.isDark ? AppColors.darkButton : Color.white)
                    .clipShape(Circle())
                    .shadow(radius: 4, y: 2)
            }
            .padding(20)
        }
        .background(bg.ignoresSafeArea())
        .hideSystemNavBar()
        .swipeBack { dismiss() }
        .sheet(item: $editor) { rem in
            NavigationStack {
                Form {
                    TextField("Текст", text: $draftText)
                    DatePicker("Когда", selection: $draftDate)
                }
                .navigationTitle(rem.text.isEmpty ? "Новое" : "Изменить")
                #if os(iOS)
                .navigationBarTitleDisplayMode(.inline)
                #endif
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Отмена") { editor = nil }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Сохранить") {
                            store.save(
                                id: rem.id,
                                text: draftText,
                                dateTimeMillis: draftDate.timeIntervalSince1970 * 1000
                            )
                            editor = nil
                        }
                        .disabled(draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .confirmationDialog(
            "Завершить напоминание?",
            isPresented: Binding(
                get: { completeCandidate != nil },
                set: { if !$0 { completeCandidate = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                if let id = completeCandidate?.id {
                    store.delete(id: id)
                }
                completeCandidate = nil
            }
            Button("Отмена", role: .cancel) { completeCandidate = nil }
        } message: {
            Text(completeCandidate.map { "Удалить «\($0.text)»?" } ?? "")
        }
    }

    private func reminderRow(_ r: Reminder) -> some View {
        let df = DateFormatter()
        df.locale = Locale(identifier: "ru_RU")
        df.dateStyle = .medium
        df.timeStyle = .short
        let date = Date(timeIntervalSince1970: r.dateTimeMillis / 1000)

        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(r.text)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
                Text(df.string(from: date))
                    .font(.caption)
                    .foregroundStyle(AppColors.textSecondary)
            }
            Spacer()
            Button {
                draftText = r.text
                draftDate = date
                editor = r
            } label: {
                Image(systemName: "pencil")
            }
            Button {
                completeCandidate = r
            } label: {
                Image(systemName: "checkmark.circle")
            }
        }
        .padding(14)
        .background(theme.isDark ? AppColors.darkCard : Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
