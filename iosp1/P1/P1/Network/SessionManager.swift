import Foundation

class SessionManager {
    static let shared = SessionManager()
    
    private let defaults = UserDefaults.standard
    
    private let keyToken = "auth_token"
    private let keyDeviceId = "device_id"
    private let keyProfileId = "profile_id"
    private let keyUserName = "user_name"
    
    private init() {}
    
    func saveToken(_ token: String) {
        saveTokens(access: token, refresh: nil)
    }

    func saveTokens(access: String, refresh: String?) {
        defaults.set(access, forKey: keyToken)
        defaults.set(access, forKey: "access_token")
        if let refresh { defaults.set(refresh, forKey: "refresh_token") }
    }

    func getRefreshToken() -> String? {
        defaults.string(forKey: "refresh_token")
    }

    func getAccessToken() -> String? {
        defaults.string(forKey: "access_token") ?? defaults.string(forKey: keyToken)
    }

    func getToken() -> String? {
        getAccessToken()
    }

    func clearToken() {
        defaults.removeObject(forKey: keyToken)
        defaults.removeObject(forKey: "access_token")
        defaults.removeObject(forKey: "refresh_token")
    }
    
    func saveProfileId(_ profileId: String) {
        defaults.set(profileId, forKey: keyProfileId)
    }
    
    func getProfileId() -> String? {
        return defaults.string(forKey: keyProfileId)
    }
    
    func saveUserName(_ name: String) {
        defaults.set(name, forKey: keyUserName)
    }
    
    func getUserName() -> String? {
        return defaults.string(forKey: keyUserName)
    }
    
    func getOrCreateDeviceId() -> String {
        if let id = defaults.string(forKey: keyDeviceId) {
            return id
        } else {
            let newId = "device_\(UUID().uuidString.prefix(8))"
            defaults.set(newId, forKey: keyDeviceId)
            return newId
        }
    }
    
    func clearSession() {
        defaults.removeObject(forKey: keyToken)
        defaults.removeObject(forKey: keyProfileId)
        defaults.removeObject(forKey: keyUserName)
    }
}
