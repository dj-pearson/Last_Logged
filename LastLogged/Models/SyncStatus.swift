import Foundation

enum SyncStatus: Int, Codable {
    case synced
    case pending
    case conflict
}
