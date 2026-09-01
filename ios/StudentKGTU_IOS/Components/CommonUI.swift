import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct OfflineBanner: View {
    /// Only show when device is actually offline (not when online + cached).
    let visible: Bool
    var updatedLabel: String? = nil

    var body: some View {
        // Never claim «нет сети» if NetworkMonitor says online (VPN false positives fixed on monitor side)
        let reallyOffline = visible && !NetworkMonitor.shared.isOnline
        if reallyOffline {
            let text: String = {
                if let updatedLabel, !updatedLabel.isEmpty {
                    return "Нет сети · \(updatedLabel)"
                }
                return "Нет сети — показаны сохранённые данные"
            }()
            HStack(spacing: 8) {
                Image(systemName: "icloud.slash")
                Text(text)
                    .font(.caption.weight(.medium))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(AppColors.offlineAmber)
        }
    }
}

struct UpdatedAtLabel: View {
    let text: String?
    var color: Color = AppColors.textSecondary
    /// Extra top inset so Dynamic Island / notch does not cover the line.
    var extraTop: CGFloat = 0

    var body: some View {
        if let text, !text.isEmpty {
            Text(text)
                .font(.caption2)
                .foregroundStyle(color)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.top, 4 + extraTop)
                .padding(.bottom, 4)
        }
    }
}

struct AppTopBar: View {
    let title: String
    var onBack: (() -> Void)? = nil
    var onRefresh: (() -> Void)? = nil
    var isDark: Bool = false

    var body: some View {
        HStack {
            if let onBack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(isDark ? AppColors.darkOnSurface : .white)
                }
                .frame(width: 36, height: 36)
            } else {
                Color.clear.frame(width: 36, height: 36)
            }
            Spacer()
            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(isDark ? AppColors.darkOnSurface : .white)
            Spacer()
            if let onRefresh {
                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(isDark ? AppColors.darkOnSurface : .white)
                }
                .frame(width: 36, height: 36)
            } else {
                Color.clear.frame(width: 36, height: 36)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(isDark ? AppColors.darkSurface : AppColors.blueKGTA)
    }
}

struct LoadingState: View {
    var message: String = "Загрузка…"
    var lightOnBlue: Bool = false

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(lightOnBlue ? .white : AppColors.blueKGTA)
            Text(message)
                .foregroundStyle(lightOnBlue ? Color.white.opacity(0.85) : AppColors.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }
}

struct ErrorState: View {
    let message: String
    var onRetry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(AppColors.errorRed)
            if let onRetry {
                Button("Повторить", action: onRetry)
                    .buttonStyle(.borderedProminent)
                    .tint(AppColors.blueKGTA)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }
}

struct EmptyStateView: View {
    let title: String
    let subtitle: String
    var body: some View {
        VStack(spacing: 8) {
            Text(title).font(.headline)
            Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }
}

struct ChoiceChip: View {
    let label: String
    let selected: Bool
    let onClick: () -> Void
    var fullWidth: Bool = false
    var compact: Bool = false

    var body: some View {
        Button(action: onClick) {
            Text(label)
                .font((compact ? Font.caption : Font.subheadline).weight(.semibold))
                .foregroundStyle(selected ? AppColors.blueKGTA : .white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .padding(.horizontal, compact ? 10 : 16)
                .padding(.vertical, compact ? 8 : 12)
                .background(selected ? Color.white : Color.white.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: compact ? 10 : 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

extension View {
    /// Hides system nav bar on iOS (we use custom AppTopBar).
    @ViewBuilder
    func hideSystemNavBar() -> some View {
        #if os(iOS)
        self.navigationBarHidden(true)
        #else
        self
        #endif
    }

    /// Swipe from left edge → back (like system interactive pop).
    /// - Parameter enableSystemPop: when false, only our gesture runs (needed for
    ///   in-place detail screens like teacher card → list → home).
    func swipeBack(enableSystemPop: Bool = true, perform action: @escaping () -> Void) -> some View {
        self.modifier(EdgeSwipeBackModifier(onBack: action, enableSystemPop: enableSystemPop))
    }
}

/// Edge swipe right from the left side of the screen to go back.
/// Uses simultaneousGesture so ScrollView vertical scrolling still works.
private struct EdgeSwipeBackModifier: ViewModifier {
    let onBack: () -> Void
    var enableSystemPop: Bool = true

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                DragGesture(minimumDistance: 28, coordinateSpace: .global)
                    .onEnded { value in
                        // Only left-edge → right horizontal swipes count as "back".
                        // Vertical list scrolling must not be blocked.
                        let fromEdge = value.startLocation.x < 28
                        let swipedRight = value.translation.width > 80
                        let mostlyHorizontal =
                            abs(value.translation.width) > abs(value.translation.height) * 1.4
                        let notTooVertical = abs(value.translation.height) < 100
                        if fromEdge && swipedRight && mostlyHorizontal && notTooVertical {
                            onBack()
                        }
                    }
            )
            #if os(iOS)
            .background(InteractivePopEnabler(enabled: enableSystemPop))
            #endif
    }
}

#if os(iOS)
/// Re-enable UINavigationController edge-swipe when nav bar is hidden.
private struct InteractivePopEnabler: UIViewControllerRepresentable {
    var enabled: Bool = true

    func makeUIViewController(context: Context) -> Controller {
        let c = Controller()
        c.popEnabled = enabled
        return c
    }

    func updateUIViewController(_ uiViewController: Controller, context: Context) {
        uiViewController.popEnabled = enabled
        uiViewController.applyPopEnabled()
    }

    final class Controller: UIViewController, UIGestureRecognizerDelegate {
        var popEnabled: Bool = true

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            applyPopEnabled()
        }

        func applyPopEnabled() {
            guard let nav = findNav() else { return }
            // When disabled, block system pop so in-place detail (teacher card) can
            // handle swipe → list without jumping to Home.
            nav.interactivePopGestureRecognizer?.isEnabled = popEnabled
            nav.interactivePopGestureRecognizer?.delegate = popEnabled ? self : nil
        }

        private func findNav() -> UINavigationController? {
            if let nav = navigationController { return nav }
            var responder: UIResponder? = self
            while let r = responder {
                if let nav = r as? UINavigationController { return nav }
                responder = r.next
            }
            return nil
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard popEnabled else { return false }
            return (findNav()?.viewControllers.count ?? 0) > 1
        }
    }
}
#endif
