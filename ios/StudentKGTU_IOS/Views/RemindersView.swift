import SwiftUI

struct RemindersView: View {
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var store: RemindersStore
    @Environment(\.dismiss) private var dismiss

    @State private var editor: Reminder?
    @State private var draftText = ""
    @State private var draftDate = Date().addingTimeInterval(3600)
    /// Reminder ids pending removal after the iOS-style check animation.
    @State private var completingIds: Set<String> = []
    @State private var completeTasks: [String: Task<Void, Never>] = [:]

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
            reminderEditorSheet(rem)
        }
    }

    /// High-contrast editor: white «Отмена» / «Сохранить» on dark navy sheet.
    private func reminderEditorSheet(_ rem: Reminder) -> some View {
        NavigationStack {
            Form {
                TextField("Текст", text: $draftText)
                DatePicker("Когда", selection: $draftDate)
            }
            .scrollContentBackground(.hidden)
            .background(AppColors.darkNavy)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(rem.text.isEmpty ? "Новое" : "Изменить")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(Color.white)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { editor = nil }
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.white)
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
                    .fontWeight(.bold)
                    .foregroundStyle(Color.white)
                    .disabled(draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .toolbarBackground(AppColors.darkNavy, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .preferredColorScheme(.dark)
        .presentationDetents([.medium])
        .presentationBackground(AppColors.darkNavy)
    }

    private func reminderRow(_ r: Reminder) -> some View {
        let df = DateFormatter()
        df.locale = Locale(identifier: "ru_RU")
        df.dateStyle = .medium
        df.timeStyle = .short
        let date = Date(timeIntervalSince1970: r.dateTimeMillis / 1000)
        let isCompleting = completingIds.contains(r.id)

        return HStack(spacing: 14) {
            // Empty circle → filled check → remove after ~2s (like Apple Reminders)
            Button {
                toggleComplete(r)
            } label: {
                ZStack {
                    Circle()
                        .strokeBorder(
                            isCompleting ? AppColors.successGreen : (theme.isDark ? AppColors.darkButtonBorder : Color(white: 0.75)),
                            lineWidth: 2
                        )
                        .frame(width: 26, height: 26)
                    if isCompleting {
                        Circle()
                            .fill(AppColors.successGreen)
                            .frame(width: 26, height: 26)
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Color.white)
                    }
                }
            }
            .buttonStyle(.plain)

            Button {
                draftText = r.text
                draftDate = date
                editor = r
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(r.text)
                        .font(.body.weight(.semibold))
                        .strikethrough(isCompleting, color: AppColors.textSecondary)
                        .foregroundStyle(
                            isCompleting
                                ? AppColors.textSecondary
                                : (theme.isDark ? AppColors.darkOnSurface : AppColors.textPrimary)
                        )
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(df.string(from: date))
                        .font(.caption)
                        .foregroundStyle(AppColors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(theme.isDark ? AppColors.darkCard : Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .opacity(isCompleting ? 0.55 : 1)
        .animation(.easeInOut(duration: 0.2), value: isCompleting)
    }

    private func toggleComplete(_ r: Reminder) {
        if completingIds.contains(r.id) {
            // Undo before auto-delete
            completeTasks[r.id]?.cancel()
            completeTasks[r.id] = nil
            completingIds.remove(r.id)
            return
        }
        completingIds.insert(r.id)
        let task = Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                store.delete(id: r.id)
                completingIds.remove(r.id)
                completeTasks[r.id] = nil
            }
        }
        completeTasks[r.id] = task
    }
}
