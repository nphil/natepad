import Foundation
import Security

/// Persists keys to the iOS Keychain as a single JSON blob.
/// Why one blob instead of one Keychain item per key: simpler, atomic writes, plays nicely with @Published.
@MainActor
final class KeyStore: ObservableObject {
    @Published private(set) var keys: [KeyRecord] = []

    private let service = "com.natepad.keystore"
    private let account = "v1"

    init() {
        load()
    }

    // MARK: - Public API

    func add(_ rec: KeyRecord) {
        // Deduplicate by fingerprint, merging public/private if needed
        if let idx = keys.firstIndex(where: { $0.fingerprint == rec.fingerprint }) {
            var existing = keys[idx]
            if rec.hasPrivate, !existing.hasPrivate {
                existing.hasPrivate = true
                existing.armoredPrivate = rec.armoredPrivate
            }
            if rec.hasPublic, !existing.hasPublic {
                existing.hasPublic = true
                existing.armoredPublic = rec.armoredPublic
            }
            keys[idx] = existing
        } else {
            keys.append(rec)
        }
        save()
    }

    func remove(id: String) {
        keys.removeAll { $0.id == id }
        save()
    }

    func key(id: String) -> KeyRecord? {
        keys.first(where: { $0.id == id })
    }

    var publicKeys: [KeyRecord] { keys.filter { $0.hasPublic } }
    var privateKeys: [KeyRecord] { keys.filter { $0.hasPrivate } }

    // MARK: - Persistence

    private func load() {
        guard let data = readKeychain() else { return }
        if let decoded = try? JSONDecoder().decode([KeyRecord].self, from: data) {
            keys = decoded
        }
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(keys) else { return }
        writeKeychain(data)
    }

    // MARK: - Keychain plumbing

    private func readKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        return status == errSecSuccess ? (result as? Data) : nil
    }

    private func writeKeychain(_ data: Data) {
        let baseQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        let status = SecItemUpdate(baseQuery as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            var insert = baseQuery.merging(attrs) { _, new in new }
            SecItemAdd(insert as CFDictionary, nil)
        }
    }
}
