import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct OfflineBanner: View {
    let visible: Bool
    var updatedLabel: String? = nil

    var body: some View {
        if visible {
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

    var body: some View {
        Button(action: onClick) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(selected ? AppColors.blueKGTA : .white)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(selected ? Color.white : Color.white.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
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
    func swipeBack(perform action: @escaping () -> Void) -> some View {
        self.modifier(EdgeSwipeBackModifier(onBack: action))
    }
}

/// Edge swipe right from the left side of the screen to go back.
private struct EdgeSwipeBackModifier: ViewModifier {
    let onBack: () -> Void
    @State private var dragging = false

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                DragGesture(minimumDistance: 24, coordinateSpace: .global)
                    .onChanged { value in
                        if value.startLocation.x < 32 {
                            dragging = true
                        }
                    }
                    .onEnded { value in
                        defer { dragging = false }
                        let fromEdge = value.startLocation.x < 36
                        let swipedRight = value.translation.width > 70
                        let notVertical = abs(value.translation.height) < 120
                        if fromEdge && swipedRight && notVertical {
                            onBack()
                        }
                    }
            )
            #if os(iOS)
            .background(InteractivePopEnabler())
            #endif
    }
}

#if os(iOS)
/// Re-enable UINavigationController edge-swipe when nav bar is hidden.
private struct InteractivePopEnabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller {
        Controller()
    }

    func updateUIViewController(_ uiViewController: Controller, context: Context) {
        uiViewController.enablePop()
    }

    final class Controller: UIViewController, UIGestureRecognizerDelegate {
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            enablePop()
        }

        func enablePop() {
            guard let nav = navigationController else {
                // Walk parents — SwiftUI hosting may nest
                var responder: UIResponder? = self
                while let r = responder {
                    if let nav = r as? UINavigationController {
                        nav.interactivePopGestureRecognizer?.isEnabled = true
                        nav.interactivePopGestureRecognizer?.delegate = self
                        return
                    }
                    responder = r.next
                }
                return
            }
            nav.interactivePopGestureRecognizer?.isEnabled = true
            nav.interactivePopGestureRecognizer?.delegate = self
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            (navigationController?.viewControllers.count ?? 0) > 1
        }
    }
}
#endif
