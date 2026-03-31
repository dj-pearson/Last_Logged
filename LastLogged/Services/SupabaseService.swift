import Foundation
import SwiftData
import Supabase

@Observable
final class SupabaseService {
    static let shared = SupabaseService()

    // MARK: - Configuration (read from AppSecrets)

    private static let supabaseURL = URL(string: AppSecrets.supabaseURL)!
    private static let supabaseAnonKey = AppSecrets.supabaseAnonKey

    let client: SupabaseClient

    // MARK: - State

    private(set) var isSyncing = false
    private(set) var lastSyncedAt: Date?
    private(set) var isSignedIn = false
    private(set) var currentUserEmail: String?
    private var syncDebounceTask: Task<Void, Never>?
    private let syncDebounceInterval: TimeInterval = 2.0

    // UserDefaults key for last sync timestamp
    private static let lastSyncTimestampKey = "com.pearsonmedia.lastlogged.lastSyncTimestamp"

    private var lastSyncTimestamp: Date? {
        get { UserDefaults.standard.object(forKey: Self.lastSyncTimestampKey) as? Date }
        set { UserDefaults.standard.set(newValue, forKey: Self.lastSyncTimestampKey) }
    }

    // MARK: - Initialization

    private init() {
        client = SupabaseClient(
            supabaseURL: Self.supabaseURL,
            supabaseKey: Self.supabaseAnonKey
        )
    }

    // MARK: - Session Restore

    func restoreSession() async {
        do {
            let session = try await client.auth.session
            isSignedIn = true
            currentUserEmail = session.user.email
        } catch {
            isSignedIn = false
            currentUserEmail = nil
        }
    }

    // MARK: - Apple Sign In

    func signInWithApple(idToken: String, nonce: String) async throws {
        let session = try await client.auth.signInWithIdToken(
            credentials: .init(
                provider: .apple,
                idToken: idToken,
                nonce: nonce
            )
        )
        isSignedIn = true
        currentUserEmail = session.user.email
        await createOrUpdateUserProfile(session: session)
    }

    // MARK: - Email/Password Auth

    func signUpEmail(email: String, password: String) async throws {
        let session = try await client.auth.signUp(
            email: email,
            password: password
        ).session
        if let session {
            isSignedIn = true
            currentUserEmail = session.user.email
            await createOrUpdateUserProfile(session: session)
        }
    }

    func signInEmail(email: String, password: String) async throws {
        let session = try await client.auth.signIn(
            email: email,
            password: password
        )
        isSignedIn = true
        currentUserEmail = session.user.email
        await createOrUpdateUserProfile(session: session)
    }

    // MARK: - Sign Out

    func signOut() async throws {
        try await client.auth.signOut()
        isSignedIn = false
        currentUserEmail = nil
    }

    // MARK: - User Profile

    private func createOrUpdateUserProfile(session: Session) async {
        let authId = session.user.id
        let displayName = session.user.userMetadata["full_name"]?.stringValue
            ?? session.user.email
            ?? "User"
        let tier = RevenueCatService.shared.subscriptionTier.rawValue

        let row = UserProfileRow(
            authId: authId,
            displayName: displayName,
            subscriptionTier: tier
        )
        do {
            try await client.from("users")
                .upsert(row, onConflict: "auth_id")
                .execute()
        } catch {
            // Profile creation failed — sync will still work via auth_id
        }
    }

    // MARK: - Auth State

    var isAuthenticated: Bool {
        get async {
            do {
                _ = try await client.auth.session
                return true
            } catch {
                return false
            }
        }
    }

    var currentUserId: UUID? {
        get async {
            do {
                let session = try await client.auth.session
                let authId = session.user.id
                // Look up internal user ID from users table
                let rows: [UserRow] = try await client.from("users")
                    .select("id")
                    .eq("auth_id", value: authId.uuidString)
                    .execute()
                    .value
                return rows.first?.id
            } catch {
                return nil
            }
        }
    }

    // MARK: - Sync Trigger (Debounced)

    func scheduleSyncAfterWrite(modelContext: ModelContext) {
        syncDebounceTask?.cancel()
        let context = modelContext
        syncDebounceTask = Task {
            try? await Task.sleep(for: .seconds(syncDebounceInterval))
            guard !Task.isCancelled else { return }
            await sync(modelContext: context)
        }
    }

    func syncOnForeground(modelContext: ModelContext) {
        Task {
            await sync(modelContext: modelContext)
        }
    }

    // MARK: - Full Sync (Push then Pull)

    @MainActor
    func sync(modelContext: ModelContext) async {
        guard await isAuthenticated else { return }
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        guard let userId = await currentUserId else { return }

        await pushCategories(modelContext: modelContext, userId: userId)
        await pushTrackerItems(modelContext: modelContext, userId: userId)
        await pushCompletionLogs(modelContext: modelContext, userId: userId)

        await pullCategories(modelContext: modelContext, userId: userId)
        await pullTrackerItems(modelContext: modelContext, userId: userId)
        await pullCompletionLogs(modelContext: modelContext, userId: userId)

        lastSyncedAt = Date()
        lastSyncTimestamp = Date()

        try? modelContext.save()
    }

    // MARK: - Push Categories

    private func pushCategories(modelContext: ModelContext, userId: UUID) async {
        let descriptor = FetchDescriptor<TrackerCategory>()
        guard let categories = try? modelContext.fetch(descriptor) else { return }

        // Push all categories (upsert on server side)
        for category in categories {
            let row = TrackerCategoryRow(
                id: category.id,
                userId: userId,
                name: category.name,
                iconName: category.iconName,
                colorHex: category.colorHex,
                sortOrder: category.sortOrder,
                isDefault: category.isDefault
            )
            do {
                try await client.from("tracker_categories")
                    .upsert(row, onConflict: "id")
                    .execute()
            } catch {
                // Continue with next item on failure
            }
        }
    }

    // MARK: - Push Tracker Items

    private func pushTrackerItems(modelContext: ModelContext, userId: UUID) async {
        let descriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { $0.syncStatus != .synced }
        )
        guard let items = try? modelContext.fetch(descriptor) else { return }

        for item in items {
            let row = TrackerItemRow(
                id: item.id,
                userId: userId,
                name: item.name,
                categoryId: item.categoryId,
                reminderIntervalDays: item.reminderIntervalDays,
                lastCompletedAt: item.lastCompletedAt,
                createdAt: item.createdAt,
                sortOrder: item.sortOrder,
                iconName: item.iconName,
                isArchived: item.isArchived,
                syncStatus: "synced"
            )
            do {
                try await client.from("tracker_items")
                    .upsert(row, onConflict: "id")
                    .execute()
                item.syncStatus = .synced
            } catch {
                // Keep as pending on failure
            }
        }
    }

    // MARK: - Push Completion Logs

    private func pushCompletionLogs(modelContext: ModelContext, userId: UUID) async {
        let descriptor = FetchDescriptor<CompletionLog>()
        guard let logs = try? modelContext.fetch(descriptor) else { return }

        for log in logs {
            let row = CompletionLogRow(
                id: log.id,
                userId: userId,
                trackerItemId: log.trackerItemId,
                completedAt: log.completedAt,
                notes: log.notes
            )
            do {
                try await client.from("completion_logs")
                    .upsert(row, onConflict: "id")
                    .execute()
            } catch {
                // Continue with next log on failure
            }
        }
    }

    // MARK: - Pull Categories

    private func pullCategories(modelContext: ModelContext, userId: UUID) async {
        do {
            var query = client.from("tracker_categories")
                .select()
                .eq("user_id", value: userId.uuidString)

            if let since = lastSyncTimestamp {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                query = query.gte("updated_at", value: formatter.string(from: since))
            }

            let rows: [TrackerCategoryRow] = try await query.execute().value

            for row in rows {
                let descriptor = FetchDescriptor<TrackerCategory>(
                    predicate: #Predicate { $0.id == row.id }
                )
                let existing = try? modelContext.fetch(descriptor).first

                if let existing {
                    // Last-write-wins: remote is newer (we just pulled)
                    existing.name = row.name
                    existing.iconName = row.iconName
                    existing.colorHex = row.colorHex
                    existing.sortOrder = row.sortOrder
                    existing.isDefault = row.isDefault
                } else {
                    let category = TrackerCategory(
                        id: row.id,
                        name: row.name,
                        iconName: row.iconName,
                        colorHex: row.colorHex,
                        sortOrder: row.sortOrder,
                        isDefault: row.isDefault
                    )
                    modelContext.insert(category)
                }
            }
        } catch {
            // Pull failed — local data remains intact
        }
    }

    // MARK: - Pull Tracker Items

    private func pullTrackerItems(modelContext: ModelContext, userId: UUID) async {
        do {
            var query = client.from("tracker_items")
                .select()
                .eq("user_id", value: userId.uuidString)

            if let since = lastSyncTimestamp {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                query = query.gte("updated_at", value: formatter.string(from: since))
            }

            let rows: [TrackerItemRow] = try await query.execute().value

            for row in rows {
                let descriptor = FetchDescriptor<TrackerItem>(
                    predicate: #Predicate { $0.id == row.id }
                )
                let existing = try? modelContext.fetch(descriptor).first

                if let existing {
                    // Last-write-wins using updated_at from server
                    existing.name = row.name
                    existing.categoryId = row.categoryId
                    existing.reminderIntervalDays = row.reminderIntervalDays
                    existing.lastCompletedAt = row.lastCompletedAt
                    existing.sortOrder = row.sortOrder
                    existing.iconName = row.iconName
                    existing.isArchived = row.isArchived
                    existing.syncStatus = .synced
                } else {
                    let item = TrackerItem(
                        id: row.id,
                        name: row.name,
                        categoryId: row.categoryId,
                        reminderIntervalDays: row.reminderIntervalDays,
                        lastCompletedAt: row.lastCompletedAt,
                        createdAt: row.createdAt,
                        sortOrder: row.sortOrder,
                        iconName: row.iconName,
                        isArchived: row.isArchived,
                        syncStatus: .synced
                    )
                    modelContext.insert(item)
                }
            }
        } catch {
            // Pull failed — local data remains intact
        }
    }

    // MARK: - Pull Completion Logs

    private func pullCompletionLogs(modelContext: ModelContext, userId: UUID) async {
        do {
            var query = client.from("completion_logs")
                .select()
                .eq("user_id", value: userId.uuidString)

            if let since = lastSyncTimestamp {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                query = query.gte("created_at", value: formatter.string(from: since))
            }

            let rows: [CompletionLogRow] = try await query.execute().value

            for row in rows {
                let descriptor = FetchDescriptor<CompletionLog>(
                    predicate: #Predicate { $0.id == row.id }
                )
                let existing = try? modelContext.fetch(descriptor).first

                if existing == nil {
                    let log = CompletionLog(
                        id: row.id,
                        trackerItemId: row.trackerItemId,
                        completedAt: row.completedAt,
                        notes: row.notes
                    )
                    modelContext.insert(log)
                }
            }
        } catch {
            // Pull failed — local data remains intact
        }
    }
}

// MARK: - Supabase Row DTOs

private struct UserRow: Decodable {
    let id: UUID
}

struct UserProfileRow: Encodable {
    let authId: UUID
    let displayName: String
    let subscriptionTier: String

    enum CodingKeys: String, CodingKey {
        case authId = "auth_id"
        case displayName = "display_name"
        case subscriptionTier = "subscription_tier"
    }
}

struct TrackerCategoryRow: Codable {
    let id: UUID
    let userId: UUID
    let name: String
    let iconName: String
    let colorHex: String
    let sortOrder: Int
    let isDefault: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case name
        case iconName = "icon_name"
        case colorHex = "color_hex"
        case sortOrder = "sort_order"
        case isDefault = "is_default"
    }
}

struct TrackerItemRow: Codable {
    let id: UUID
    let userId: UUID
    let name: String
    let categoryId: UUID
    let reminderIntervalDays: Int?
    let lastCompletedAt: Date?
    let createdAt: Date
    let sortOrder: Int
    let iconName: String
    let isArchived: Bool
    let syncStatus: String

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case name
        case categoryId = "category_id"
        case reminderIntervalDays = "reminder_interval_days"
        case lastCompletedAt = "last_completed_at"
        case createdAt = "created_at"
        case sortOrder = "sort_order"
        case iconName = "icon_name"
        case isArchived = "is_archived"
        case syncStatus = "sync_status"
    }
}

struct CompletionLogRow: Codable {
    let id: UUID
    let userId: UUID
    let trackerItemId: UUID
    let completedAt: Date
    let notes: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case trackerItemId = "tracker_item_id"
        case completedAt = "completed_at"
        case notes
    }
}
