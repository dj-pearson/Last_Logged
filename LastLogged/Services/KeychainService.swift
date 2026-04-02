import Foundation
import Security

/// Lightweight wrapper around the iOS Keychain for storing sensitive values.
enum KeychainService {
    private static let serviceName = "com.pearsonmedia.lastlogged"

    // MARK: - String Operations

    static func setString(_ value: String, forKey key: String) {
        guard let data = value.data(using: .utf8) else { return }
        setData(data, forKey: key)
    }

    static func getString(forKey key: String) -> String? {
        guard let data = getData(forKey: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Bool Operations

    static func setBool(_ value: Bool, forKey key: String) {
        setString(value ? "1" : "0", forKey: key)
    }

    static func getBool(forKey key: String) -> Bool? {
        guard let str = getString(forKey: key) else { return nil }
        return str == "1"
    }

    // MARK: - Date Operations

    static func setDate(_ value: Date, forKey key: String) {
        setString(String(value.timeIntervalSince1970), forKey: key)
    }

    static func getDate(forKey key: String) -> Date? {
        guard let str = getString(forKey: key),
              let interval = Double(str) else { return nil }
        return Date(timeIntervalSince1970: interval)
    }

    // MARK: - Delete

    static func delete(forKey key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Raw Data Operations

    private static func setData(_ data: Data, forKey key: String) {
        // Delete existing item first
        delete(forKey: key)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]

        SecItemAdd(query as CFDictionary, nil)
    }

    private static func getData(forKey key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    // MARK: - Migration

    /// Migrate a value from UserDefaults to Keychain (one-time).
    /// The UserDefaults key is removed after migration.
    static func migrateStringFromUserDefaults(key: String) {
        let migrationFlag = "keychain_migrated_\(key)"
        guard !UserDefaults.standard.bool(forKey: migrationFlag) else { return }

        if let value = UserDefaults.standard.string(forKey: key) {
            setString(value, forKey: key)
        }
        UserDefaults.standard.set(true, forKey: migrationFlag)
    }

    static func migrateBoolFromUserDefaults(key: String) {
        let migrationFlag = "keychain_migrated_\(key)"
        guard !UserDefaults.standard.bool(forKey: migrationFlag) else { return }

        if UserDefaults.standard.object(forKey: key) != nil {
            setBool(UserDefaults.standard.bool(forKey: key), forKey: key)
        }
        UserDefaults.standard.set(true, forKey: migrationFlag)
    }

    static func migrateDateFromUserDefaults(key: String) {
        let migrationFlag = "keychain_migrated_\(key)"
        guard !UserDefaults.standard.bool(forKey: migrationFlag) else { return }

        if let date = UserDefaults.standard.object(forKey: key) as? Date {
            setDate(date, forKey: key)
        }
        UserDefaults.standard.set(true, forKey: migrationFlag)
    }
}
