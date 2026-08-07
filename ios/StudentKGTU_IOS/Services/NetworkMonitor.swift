import Foundation
import Network

/// Tracks connectivity. Starts as **offline** until the first real path update
/// (critical: default-online caused long hangs in airplane mode).
final class NetworkMonitor: @unchecked Sendable {
    static let shared = NetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "StudentKGTU.NetworkMonitor")
    private let lock = NSLock()
    /// Assume offline until proven otherwise — offline-first apps must not hang.
    private var _isOnline = false
    private var _resolved = false

    var isOnline: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _isOnline
    }

    /// True after first path callback (or sync read).
    var hasResolved: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _resolved
    }

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let online = path.status == .satisfied
            self.lock.lock()
            self._isOnline = online
            self._resolved = true
            self.lock.unlock()
        }
        monitor.start(queue: queue)
        // Synchronous initial read on monitor queue
        queue.sync {
            let online = self.monitor.currentPath.status == .satisfied
            self.lock.lock()
            self._isOnline = online
            self._resolved = true
            self.lock.unlock()
        }
    }
}
