import Foundation

class AuthHelper {
    static let shared = AuthHelper()

    private init() {}

    func ensureAuthenticated() async -> Bool {
        if SessionManager.shared.getAccessToken() != nil {
            return true
        }
        return await refreshToken()
    }

    func refreshToken() async -> Bool {
        if let refresh = SessionManager.shared.getRefreshToken() {
            do {
                let response = try await ApiService.shared.refreshTokens(
                    request: RefreshTokenRequest(refreshToken: refresh),
                    allowAuthRetry: false
                )
                SessionManager.shared.saveTokens(access: response.accessToken, refresh: response.refreshToken)
                print("LPM_AUTH: Token refreshed (refresh token)")
                return true
            } catch {
                print("LPM_AUTH: Refresh token invalid, device auth fallback")
            }
        }

        SessionManager.shared.clearToken()
        return await DeviceAuth.signIn()
    }

    func ensureProfile() async -> String? {
        if let profileId = SessionManager.shared.getProfileId() {
            return profileId
        }

        do {
            let profile = try await ApiService.shared.createProfile(
                request: ProfileRequest(first_name: "Patient", last_name: "Local", relation: "Self")
            )
            SessionManager.shared.saveProfileId(profile.id)
            return profile.id
        } catch {
            print("LPM_AUTH: Failed to create profile - \(error)")
            return nil
        }
    }
}
