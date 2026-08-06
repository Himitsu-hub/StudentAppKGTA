import SwiftUI
import Combine

@MainActor
final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()
    private let key = "dark_theme"

    @Published var isDark: Bool {
        didSet { UserDefaults.standard.set(isDark, forKey: key) }
    }

    init() {
        isDark = UserDefaults.standard.bool(forKey: key)
    }

    func toggle() { isDark.toggle() }

    var preferredScheme: ColorScheme? { isDark ? .dark : .light }
}
