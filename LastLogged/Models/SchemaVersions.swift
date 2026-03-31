import Foundation
import SwiftData

// ============================================================
// SwiftData Versioned Schema & Migration Plan
//
// HOW TO ADD A MIGRATION:
// 1. Create a new enum (e.g., SchemaV2) conforming to VersionedSchema
// 2. Set versionIdentifier to Schema.Version(2, 0, 0)
// 3. Define the updated @Model classes inside the enum
// 4. Add a MigrationStage to LastLoggedMigrationPlan.stages
//    - Use .lightweight for additive changes (new fields with defaults)
//    - Use .custom for data transforms or renames
// 5. Update LastLoggedMigrationPlan.current to the new schema
// 6. Update models array in current to include all model types
// ============================================================

// MARK: - Schema V1 (Initial Release)

enum SchemaV1: VersionedSchema {
    static var versionIdentifier = Schema.Version(1, 0, 0)

    static var models: [any PersistentModel.Type] {
        [TrackerItem.self, CompletionLog.self, TrackerCategory.self]
    }
}

// MARK: - Migration Plan

enum LastLoggedMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] {
        [SchemaV1.self]
    }

    static var stages: [MigrationStage] {
        // No migrations yet — first release.
        // Future migrations go here, e.g.:
        // .lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)
        []
    }
}
