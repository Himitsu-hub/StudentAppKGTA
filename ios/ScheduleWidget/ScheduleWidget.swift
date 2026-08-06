import WidgetKit
import SwiftUI

struct ScheduleEntry: TimelineEntry {
    let date: Date
    let snap: WidgetSnapshot
}

struct ScheduleProvider: TimelineProvider {
    func placeholder(in context: Context) -> ScheduleEntry {
        ScheduleEntry(date: Date(), snap: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        completion(ScheduleEntry(date: Date(), snap: WidgetSnapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        let entry = ScheduleEntry(date: Date(), snap: WidgetSnapshot.load())
        let next = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

// MARK: - Wide ~4x2 (systemLarge / systemMedium landscape-ish)
struct WideWidgetView: View {
    let entry: ScheduleEntry
    var body: some View {
        let s = entry.snap
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(s.weekLine)
                    .font(.caption.weight(.semibold))
                Spacer()
                if !s.groupLine.isEmpty {
                    Text(s.groupLine)
                        .font(.caption2)
                        .lineLimit(1)
                }
            }
            .foregroundStyle(Color.white.opacity(0.9))

            Text(s.label)
                .font(.caption2)
                .foregroundStyle(Color.white.opacity(0.75))

            Text(s.subject)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .minimumScaleFactor(0.8)

            if !s.details.isEmpty {
                Text(s.details)
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.85))
                    .lineLimit(3)
            }

            Spacer(minLength: 0)
            Text(s.hint)
                .font(.caption2)
                .foregroundStyle(Color.white.opacity(0.65))
        }
        .padding(14)
        .containerBackground(for: .widget) {
            LinearGradient(
                colors: [
                    Color(red: 0x1A/255, green: 0x33/255, blue: 0x6C/255),
                    Color(red: 0x0F/255, green: 0x1F/255, blue: 0x45/255),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

// MARK: - Square ~2x2
struct SquareWidgetView: View {
    let entry: ScheduleEntry
    var body: some View {
        let s = entry.snap
        VStack(alignment: .leading, spacing: 4) {
            Text(s.weekLine)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.9))
                .lineLimit(1)
            Text(s.label)
                .font(.system(size: 10))
                .foregroundStyle(Color.white.opacity(0.7))
            Text(s.subject)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(3)
                .minimumScaleFactor(0.75)
            if !s.details.isEmpty {
                Text(s.details)
                    .font(.system(size: 10))
                    .foregroundStyle(Color.white.opacity(0.8))
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .containerBackground(for: .widget) {
            Color(red: 0x1A/255, green: 0x33/255, blue: 0x6C/255)
        }
    }
}

struct ScheduleWideWidget: Widget {
    let kind = "ScheduleWideWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ScheduleProvider()) { entry in
            WideWidgetView(entry: entry)
        }
        .configurationDisplayName("Расписание (неделя)")
        .description("Тип недели и ближайшая пара — как виджет 4×2 на Android.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct ScheduleSquareWidget: Widget {
    let kind = "ScheduleSquareWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ScheduleProvider()) { entry in
            SquareWidgetView(entry: entry)
        }
        .configurationDisplayName("Следующая пара")
        .description("Компактный виджет 2×2 — следующая пара.")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct ScheduleWidgetBundle: WidgetBundle {
    var body: some Widget {
        ScheduleWideWidget()
        ScheduleSquareWidget()
    }
}
