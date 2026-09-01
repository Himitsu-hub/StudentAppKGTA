import Foundation
import Combine

@MainActor
final class UserPreferences: ObservableObject {
    static let shared = UserPreferences()
    private let d = UserDefaults.standard

    private enum Key {
        static let faculty = "selected_faculty"
        static let course = "selected_course"
        static let group = "selected_group"
        static let subgroup = "selected_subgroup"
        static let onboarding = "onboarding_done"
    }

    @Published var faculty: String {
        didSet { d.set(faculty, forKey: Key.faculty) }
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

    var facultyShort: String { FacultyCatalog.shortName(faculty) }

    var isConfigured: Bool {
        guard let group, !group.isEmpty else { return false }
        return onboardingDone || true
    }

    var hasGroup: Bool {
        !(group ?? "").isEmpty
    }

    init() {
        let fRaw = d.string(forKey: Key.faculty) ?? FacultyCatalog.fae
        let resolvedFaculty: String = {
            switch fRaw {
            case FacultyCatalog.mtf: return FacultyCatalog.mtf
            case FacultyCatalog.masters: return FacultyCatalog.masters
            default: return FacultyCatalog.fae
            }
        }()
        let c = d.integer(forKey: Key.course)
        let g = d.string(forKey: Key.group)
        let s = d.string(forKey: Key.subgroup)
        let done = d.bool(forKey: Key.onboarding)
        let maxCourse = FacultyCatalog.courses(for: resolvedFaculty).last ?? 4
        faculty = resolvedFaculty
        course = min(max(c >= 1 ? c : 1, 1), maxCourse)
        group = g
        subgroup = s
        onboardingDone = done || !(g ?? "").isEmpty
    }

    func save(faculty: String, course: Int, group: String?, subgroup: String?) {
        self.faculty = faculty
        self.course = course
        self.group = group
        self.subgroup = subgroup
        // Never clear onboardingDone here — otherwise Schedule → Home flashes Onboarding
        // while groups are loading after faculty/course change.
        if let group, !group.isEmpty {
            onboardingDone = true
        }
    }

    /// Backward-compatible save without faculty change.
    func save(course: Int, group: String?, subgroup: String?) {
        save(faculty: faculty, course: course, group: group, subgroup: subgroup)
    }

    /// Reset selection to re-run first-launch onboarding deliberately.
    func clearGroup() {
        group = nil
        subgroup = nil
        onboardingDone = false
    }
}
