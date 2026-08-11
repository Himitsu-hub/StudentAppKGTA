import SwiftUI

struct CampusView: View {
    @EnvironmentObject private var theme: ThemeManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    /// Light theme home/campus use brand blue background (like Android `onBlue`).
    private var onBlue: Bool { !theme.isDark }
    private var bg: Color { onBlue ? AppColors.blueKGTA : AppColors.darkNavy }

    /// Section titles / hints on the blue or navy screen (must stay light — Android white / onSurface).
    private var screenTitle: Color { onBlue ? .white : AppColors.darkOnSurface }
    private var screenHint: Color { onBlue ? Color.white.opacity(0.85) : AppColors.darkOnSurfaceMuted }

    /// Text inside white/dark cards
    private var cardTitle: Color { onBlue ? AppColors.blueKGTA : AppColors.darkOnSurface }
    private var cardBody: Color { onBlue ? AppColors.textPrimary : AppColors.darkOnSurface }
    private var cardMuted: Color { onBlue ? AppColors.textSecondary : AppColors.darkOnSurfaceMuted }
    private var cardBg: Color { onBlue ? Color.white : AppColors.darkCard }
    /// Icons: brand blue on light cards; soft light tint in dark (like phone/mail rows).
    private var iconAccent: Color {
        onBlue ? AppColors.blueKGTA : AppColors.darkOnSurfaceMuted
    }
    /// Capsule actions (Карта / Позвонить / …): lighter blue in light theme.
    private var actionPillBg: Color {
        onBlue
            ? Color(red: 0x4A / 255, green: 0x7B / 255, blue: 0xC8 / 255)
            : AppColors.blueKGTALight
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopBar(title: "Кампус и контакты", onBack: { dismiss() }, isDark: theme.isDark)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sectionTitle("Корпуса")
                    Text("Основные здания кампуса КГТУ")
                        .font(.system(size: 13))
                        .foregroundStyle(screenHint)
                        .fixedSize(horizontal: false, vertical: true)

                    ForEach(CampusData.buildings) { b in
                        buildingCard(b)
                    }

                    sectionTitle("Контакты")
                    Text("Адрес, телефон и службы вуза")
                        .font(.system(size: 13))
                        .foregroundStyle(screenHint)

                    headerCard

                    sectionTitle("Быстрые контакты")
                    Text("Базовый номер \(CampusData.mainPhoneDisplay) — наберите и добавьте добавочный.")
                        .font(.system(size: 13))
                        .foregroundStyle(screenHint)
                        .fixedSize(horizontal: false, vertical: true)
                    ForEach(CampusData.quickContacts) { c in contactCard(c) }

                    sectionTitle("Деканаты")
                    Text("Факультеты и колледж")
                        .font(.system(size: 13))
                        .foregroundStyle(screenHint)
                    ForEach(CampusData.deaneries) { c in contactCard(c) }

                    sectionTitle("Службы")
                    Text("Общежитие, ВУЦ, бухгалтерия и другие")
                        .font(.system(size: 13))
                        .foregroundStyle(screenHint)
                    ForEach(CampusData.services) { c in contactCard(c) }

                    Text("Источник: dksta.ru/kontakty-1 · КГТУ им. В.А. Дегтярева")
                        .font(.system(size: 11))
                        .foregroundStyle(screenHint.opacity(0.85))
                        .padding(.top, 8)
                }
                .padding(16)
                .padding(.bottom, 28)
            }
        }
        .background(bg.ignoresSafeArea())
        // Force light content colors on brand-blue screen (system dark scheme must not darken labels).
        .environment(\.colorScheme, onBlue ? .light : .dark)
        .hideSystemNavBar()
        .swipeBack { dismiss() }
    }

    private func sectionTitle(_ t: String) -> some View {
        Text(t)
            .font(.headline.weight(.bold))
            .foregroundStyle(screenTitle)
            .padding(.top, 10)
    }

    private func buildingCard(_ b: CampusBuilding) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "building.2")
                    .foregroundStyle(iconAccent)
                Text(b.title)
                    .font(.headline)
                    .foregroundStyle(cardBody)
            }
            Text(b.subtitle)
                .font(.subheadline)
                .foregroundStyle(cardMuted)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(onBlue ? Color.clear : AppColors.darkButtonBorder, lineWidth: 1)
        )
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "graduationcap.fill")
                    .font(.title2)
                    .foregroundStyle(iconAccent)
                    .frame(width: 44, height: 44)
                    .background(iconAccent.opacity(onBlue ? 0.12 : 0.18))
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("КГТУ им. В.А. Дегтярева")
                        .font(.headline)
                        .foregroundStyle(cardTitle)
                    Text("Ковров · студенческий кампус")
                        .font(.caption)
                        .foregroundStyle(cardMuted)
                }
            }
            Divider().overlay(cardMuted.opacity(0.35))
            infoRow(icon: "mappin.and.ellipse", label: CampusData.address, action: "Карта") {
                if let u = URL(string: CampusData.mapsURI) { openURL(u) }
            }
            infoRow(icon: "phone.fill", label: CampusData.mainPhoneDisplay, action: "Позвонить") {
                if let u = URL(string: CampusData.mainPhoneTel) { openURL(u) }
            }
            infoRow(icon: "clock", label: CampusData.workHours, action: nil, onAction: nil)
            infoRow(icon: "globe", label: "dksta.ru/kontakty-1", action: "Сайт") {
                if let u = URL(string: CampusData.siteContacts) { openURL(u) }
            }
        }
        .padding(16)
        .background(cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(onBlue ? Color.clear : AppColors.darkButtonBorder, lineWidth: 1)
        )
    }

    private func infoRow(icon: String, label: String, action: String?, onAction: (() -> Void)?) -> some View {
        HStack(alignment: .center) {
            Image(systemName: icon)
                .foregroundStyle(iconAccent)
                .frame(width: 24)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(cardBody)
            Spacer(minLength: 8)
            if let action, let onAction {
                Button(action, action: onAction)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(actionPillBg)
                    .clipShape(Capsule())
            }
        }
    }

    private func contactCard(_ item: ContactItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.title)
                .font(.headline)
                .foregroundStyle(cardBody)
            if let role = item.role {
                Text(role)
                    .font(.caption)
                    .foregroundStyle(cardMuted)
            }
            if let phone = item.phone {
                contactActionRow(icon: "phone", text: phone, button: "Позвонить") {
                    if let u = URL(string: CampusData.mainPhoneTel) { openURL(u) }
                }
            }
            if let email = item.email {
                contactActionRow(icon: "envelope", text: email, button: "Написать") {
                    if let u = URL(string: "mailto:\(email)") { openURL(u) }
                }
            }
            if let note = item.note {
                Text(note)
                    .font(.caption2)
                    .foregroundStyle(cardMuted)
            }
            if let web = item.webUri {
                Button {
                    if let u = URL(string: web) { openURL(u) }
                } label: {
                    Text("Открыть на сайте")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(actionPillBg)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(onBlue ? Color.clear : AppColors.darkButtonBorder, lineWidth: 1)
        )
    }

    /// Phone/email chips: soft blue in light theme, lighter navy in dark.
    private func contactActionRow(icon: String, text: String, button: String, action: @escaping () -> Void) -> some View {
        let rowBg = onBlue
            ? Color(red: 0xE3 / 255, green: 0xEB / 255, blue: 0xF8 / 255)
            : Color(red: 0x2E / 255, green: 0x3D / 255, blue: 0x5C / 255)
        let iconColor = onBlue ? AppColors.blueKGTA : AppColors.darkOnSurfaceMuted
        let textColor = onBlue ? AppColors.blueKGTADark : Color.white
        let btnBg = actionPillBg
        return HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(iconColor)
            Text(text)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(textColor)
                .lineLimit(2)
            Spacer(minLength: 4)
            Button(button, action: action)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(btnBg)
                .clipShape(Capsule())
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(rowBg)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(onBlue ? AppColors.blueKGTA.opacity(0.2) : AppColors.darkButtonBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
