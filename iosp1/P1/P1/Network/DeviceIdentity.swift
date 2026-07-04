import Foundation
import CryptoKit
import UIKit

enum DeviceIdentity {
    static func hardwareBindingId() -> String {
        let key = "hardware_binding_id"
        if let stored = UserDefaults.standard.string(forKey: key) {
            return stored
        }
        #if targetEnvironment(simulator)
        let prefix = "fb_"
        let seed = UUID().uuidString
        #else
        let prefix = "ios_"
        let seed = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        #endif
        let digest = SHA256.hash(data: Data("\(seed):com.preappointment1.app".utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let binding = "\(prefix)\(hex)"
        UserDefaults.standard.set(binding, forKey: key)
        return binding
    }

    static var platform: String {
        #if targetEnvironment(simulator)
        return "fallback"
        #else
        return "ios"
        #endif
    }
}
