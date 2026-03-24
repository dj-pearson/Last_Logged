import Foundation
import SwiftData

@Model
final class CompletionLog {
    @Attribute(.unique) var id: UUID
    var trackerItemId: UUID
    var completedAt: Date
    var notes: String?

    init(
        id: UUID = UUID(),
        trackerItemId: UUID,
        completedAt: Date = Date(),
        notes: String? = nil
    ) {
        self.id = id
        self.trackerItemId = trackerItemId
        self.completedAt = completedAt
        self.notes = notes
    }
}
