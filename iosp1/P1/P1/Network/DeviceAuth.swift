import Foundation
import CryptoKit

enum DeviceAuth {
    private static let prefsSuite = "lpm_device_keys"

    private struct KeyPairMaterial {
        let publicKey: String
        let privateKey: Curve25519.Signing.PrivateKey
    }

    private static func privateKeyKey(_ deviceId: String) -> String {
        let safe = deviceId.replacingOccurrences(of: "[^a-zA-Z0-9._-]", with: "_", options: .regularExpression)
        return "private_key_\(safe)"
    }

    private static func bytesToB64Url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func getOrCreateKeyPair(deviceId: String) throws -> KeyPairMaterial {
        let defaults = UserDefaults.standard
        if let stored = defaults.string(forKey: privateKeyKey(deviceId)),
           let privData = Data(base64Encoded: stored.base64URLToBase64()),
           let privateKey = try? Curve25519.Signing.PrivateKey(rawRepresentation: privData) {
            let pub = privateKey.publicKey.rawRepresentation
            return KeyPairMaterial(publicKey: bytesToB64Url(pub), privateKey: privateKey)
        }
        let privateKey = Curve25519.Signing.PrivateKey()
        defaults.set(bytesToB64Url(privateKey.rawRepresentation), forKey: privateKeyKey(deviceId))
        let pub = privateKey.publicKey.rawRepresentation
        return KeyPairMaterial(publicKey: bytesToB64Url(pub), privateKey: privateKey)
    }

    private static func sign(_ privateKey: Curve25519.Signing.PrivateKey, message: String) throws -> String {
        let sig = try privateKey.signature(for: Data(message.utf8))
        return bytesToB64Url(sig)
    }

    static func signIn() async -> Bool {
        let binding = DeviceIdentity.hardwareBindingId()
        let deviceId = binding
        do {
            let keys = try getOrCreateKeyPair(deviceId: deviceId)
            return try await verifyExisting(deviceId: deviceId, binding: binding, keys: keys)
        } catch let error as ApiError {
            if case .serverError(404) = error {
                do {
                    let keys = try getOrCreateKeyPair(deviceId: deviceId)
                    return try await register(deviceId: deviceId, binding: binding, keys: keys)
                } catch {
                    print("LPM_DEVICE_AUTH: register failed \(error)")
                    return false
                }
            }
            print("LPM_DEVICE_AUTH: signIn failed \(error)")
            return false
        } catch {
            print("LPM_DEVICE_AUTH: signIn failed \(error)")
            return false
        }
    }

    private static func verifyExisting(deviceId: String, binding: String, keys: KeyPairMaterial) async throws -> Bool {
        let challenge = try await ApiService.shared.deviceChallenge(
            request: DeviceChallengeRequest(deviceId: deviceId, intent: "verify")
        )
        let signature = try sign(keys.privateKey, message: challengePayload(deviceId: deviceId, nonce: challenge.nonce))
        do {
            let response = try await ApiService.shared.deviceVerify(
                request: DeviceVerifyRequest(deviceId: deviceId, nonce: challenge.nonce, signature: signature)
            )
            SessionManager.shared.saveTokens(access: response.accessToken, refresh: response.refreshToken)
            print("LPM_DEVICE_AUTH: verified")
            return true
        } catch let error as ApiError {
            if case .serverError(401) = error {
                return try await recover(deviceId: deviceId, binding: binding, keys: keys)
            }
            throw error
        }
    }

    private static func register(deviceId: String, binding: String, keys: KeyPairMaterial) async throws -> Bool {
        let challenge = try await ApiService.shared.deviceChallenge(
            request: DeviceChallengeRequest(deviceId: deviceId, intent: "register")
        )
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        let signature = try sign(
            keys.privateKey,
            message: registerPayload(deviceId: deviceId, publicKey: keys.publicKey, timestamp: timestamp, nonce: challenge.nonce)
        )
        let response = try await ApiService.shared.deviceRegister(
            request: DeviceRegisterRequest(
                deviceId: deviceId,
                publicKey: keys.publicKey,
                timestamp: timestamp,
                nonce: challenge.nonce,
                signature: signature,
                hardwareBindingId: binding,
                hardwarePlatform: DeviceIdentity.platform
            )
        )
        SessionManager.shared.saveTokens(access: response.accessToken, refresh: response.refreshToken)
        print("LPM_DEVICE_AUTH: registered")
        return true
    }

    private static func recover(deviceId: String, binding: String, keys: KeyPairMaterial) async throws -> Bool {
        let challenge = try await ApiService.shared.deviceChallenge(
            request: DeviceChallengeRequest(deviceId: deviceId, intent: "recover")
        )
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        let signature = try sign(
            keys.privateKey,
            message: recoverPayload(deviceId: deviceId, publicKey: keys.publicKey, timestamp: timestamp, nonce: challenge.nonce)
        )
        let response = try await ApiService.shared.deviceRecover(
            request: DeviceRegisterRequest(
                deviceId: deviceId,
                publicKey: keys.publicKey,
                timestamp: timestamp,
                nonce: challenge.nonce,
                signature: signature,
                hardwareBindingId: binding,
                hardwarePlatform: DeviceIdentity.platform
            )
        )
        SessionManager.shared.saveTokens(access: response.accessToken, refresh: response.refreshToken)
        print("LPM_DEVICE_AUTH: recovered")
        return true
    }

    private static func challengePayload(deviceId: String, nonce: String) -> String {
        "challenge:\(deviceId):\(nonce)"
    }

    private static func registerPayload(deviceId: String, publicKey: String, timestamp: Int64, nonce: String) -> String {
        "register:\(deviceId):\(publicKey):\(timestamp):\(nonce)"
    }

    private static func recoverPayload(deviceId: String, publicKey: String, timestamp: Int64, nonce: String) -> String {
        "recover:\(deviceId):\(publicKey):\(timestamp):\(nonce)"
    }
}

private extension String {
    func base64URLToBase64() -> String {
        var base = replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let pad = 4 - base.count % 4
        if pad < 4 { base += String(repeating: "=", count: pad) }
        return base
    }
}
