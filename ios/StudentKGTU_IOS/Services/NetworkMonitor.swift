import Foundation
import Network

/// Tracks connectivity for offline-first data loading.
final class NetworkMonitor: @unchecked Sendable {
    static let shared = NetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "StudentKGTU.NetworkMonitor")
    private let lock = NSLock()
    private var _isOnline = true

    var isOnline: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _isOnline
    }

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let online = path.status == .satisfied
            self.lock.lock()
            self._isOnline = online
            self.lock.unlock()
        }
        monitor.start(queue: queue)
    }
}
