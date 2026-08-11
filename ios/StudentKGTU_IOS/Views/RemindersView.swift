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

    /// Theme-aware editor: readable light sheet in light mode, soft card (not pure black) in dark.
    private func reminderEditorSheet(_ rem: Reminder) -> some View {
        let isDark = theme.isDark
        // Dark: elevated card (readable). Light: clean white.
        let sheetBg = isDark
            ? Color(red: 0x1E / 255, green: 0x28 / 255, blue: 0x3C / 255)
            : Color.white
        let fieldBg = isDark
            ? Color(red: 0x2E / 255, green: 0x3D / 255, blue: 0x5C / 255)
            : Color(red: 0xF0 / 255, green: 0xF3 / 255, blue: 0xF8 / 255)
        let titleColor: Color = isDark ? .white : AppColors.textPrimary
        let bodyColor: Color = isDark ? .white : AppColors.textPrimary
        let mutedColor: Color = isDark ? AppColors.darkOnSurfaceMuted : AppColors.textSecondary
        let actionColor: Color = isDark ? .white : AppColors.blueKGTALight
        let canSave = !draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Текст")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(mutedColor)
                    TextField("Что напомнить?", text: $draftText)
                        .foregroundStyle(bodyColor)
                        .padding(14)
                        .background(fieldBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .tint(isDark ? Color.white : AppColors.blueKGTA)

                    Text("Когда")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(mutedColor)
                    DatePicker("", selection: $draftDate)
                        .datePickerStyle(.compact)
                        .labelsHidden()
                        .colorScheme(isDark ? .dark : .light)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(fieldBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(20)
            }
            .background(sheetBg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(rem.text.isEmpty ? "Новое" : "Изменить")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(titleColor)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { editor = nil }
                        .fontWeight(.semibold)
                        .foregroundStyle(actionColor)
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
                    .foregroundStyle(canSave ? actionColor : mutedColor)
                    .disabled(!canSave)
                }
            }
            .toolbarBackground(sheetBg, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(isDark ? .dark : .light, for: .navigationBar)
        }
        .preferredColorScheme(isDark ? .dark : .light)
        .presentationDetents([.medium])
        .presentationBackground(sheetBg)
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
