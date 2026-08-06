import Foundation
import Combine

@MainActor
final class UserPreferences: ObservableObject {
    static let shared = UserPreferences()
    private let d = UserDefaults.standard

    private enum Key {
        static let course = "selected_course"
        static let group = "selected_group"
        static let subgroup = "selected_subgroup"
        static let onboarding = "onboarding_done"
    }

    @Published var course: Int {
        didSet { d.set(course, forKey: Key.course) }
    }
    @Published var group: String? {
        didSet { d.set(group, forKey: Key.group) }
    }
    @Published var subgroup: String? {
        didSet { d.set(subgroup, forKey: Key.subgroup) }
    }
    @Published var onboardingDone: Bool {
        didSet { d.set(onboardingDone, forKey: Key.onboarding) }
    }

    var isConfigured: Bool {
        guard let group, !group.isEmpty else { return false }
        return onboardingDone || true
    }

    var hasGroup: Bool {
        !(group ?? "").isEmpty
    }

    init() {
        let c = d.integer(forKey: Key.course)
        let g = d.string(forKey: Key.group)
        let s = d.string(forKey: Key.subgroup)
        let done = d.bool(forKey: Key.onboarding)
        course = c >= 1 ? c : 1
        group = g
        subgroup = s
        onboardingDone = done || !(g ?? "").isEmpty
    }

    func save(course: Int, group: String?, subgroup: String?) {
        self.course = course
        self.group = group
        self.subgroup = subgroup
        if let group, !group.isEmpty {
            onboardingDone = true
        }
    }

    func clearGroup() {
        group = nil
        subgroup = nil
        onboardingDone = false
    }
}
