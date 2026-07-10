import SwiftUI

struct ProfileScreen: View {
    let onOpenDrawer: () -> Void
    let onLogout: () -> Void
    
    @State private var watchConnected = false
    @State private var thermometerConnected = true
    @State private var bpConnected = false
    @State private var scaleConnected = false
    @State private var userName = SessionManager.shared.getUserName() ?? ""
    
    var body: some View {
        VStack(spacing: 0) {
            // Top Bar
            HStack {
                Button(action: onOpenDrawer) {
                    Image(systemName: "line.3.horizontal")
                        .font(.title2)
                        .foregroundColor(.black)
                }
                
                Text("Profile")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.leading, 8)
                
                Spacer()
            }
            .padding()
            .background(Color.white)
            
            ScrollView {
                VStack(spacing: 0) {
                    // Header Profile
                    VStack {
                        Circle()
                            .fill(Color.black)
                            .frame(width: 80, height: 80)
                            .overlay(
                                Text(userName.isEmpty ? "?" : String(userName.prefix(1)).uppercased())
                                    .font(.system(size: 32, weight: .heavy))
                                    .foregroundColor(.white)
                            )
                            .padding(.bottom, 16)
                        
                        TextField("Enter your full name", text: $userName)
                            .multilineTextAlignment(.center)
                            .padding()
                            .background(Color.white)
                            .cornerRadius(8)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(UIColor.systemGray5), lineWidth: 1))
                            .padding(.horizontal, 40)
                            .onSubmit {
                                SessionManager.shared.saveUserName(userName)
                            }
                    }
                    .padding(.top, 24)
                    .padding(.bottom, 32)
                    .background(Color(UIColor.systemGray6))
                    
                    // Connected Devices
                    VStack(alignment: .leading, spacing: 0) {
                        SectionTitle(title: "CONNECTED DEVICES")
                        DeviceRow(name: "Smartwatch", desc: "Steps, heart rate, sleep", isOn: $watchConnected)
                        DeviceRow(name: "Digital thermometer", desc: "Manual temperature entries", isOn: $thermometerConnected)
                        DeviceRow(name: "Blood pressure monitor", desc: "Bluetooth BP device", isOn: $bpConnected)
                        DeviceRow(name: "Connected scale", desc: "Weight tracking", isOn: $scaleConnected)
                    }
                    
                    // Preferences
                    VStack(alignment: .leading, spacing: 0) {
                        SectionTitle(title: "PREFERENCES")
                        MenuRow(label: "Temperature unit", value: "°C")
                        MenuRow(label: "Reminder times", value: "9am, 7pm")
                    }
                    
                    // Account
                    VStack(alignment: .leading, spacing: 0) {
                        SectionTitle(title: "ACCOUNT")
                        MenuRow(label: "Terms of Use & Privacy Policy") {
                            if let url = URL(string: "https://p1-privacy-policy.pages.dev/") {
                                UIApplication.shared.open(url)
                            }
                        }
                        MenuRow(label: "Export my data")
                        MenuRow(label: "Sign out") {
                            SessionManager.shared.clearSession()
                            onLogout()
                        }
                        MenuRow(label: "Delete account and all data", isDestructive: true) {
                            Task {
                                try? await ApiService.shared.deleteAccount()
                                SessionManager.shared.clearSession()
                                await MainActor.run { onLogout() }
                            }
                        }
                    }
                    
                    Spacer().frame(height: 80)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.white)
        .onDisappear {
            SessionManager.shared.saveUserName(userName)
        }
    }
}

private struct SectionTitle: View {
    let title: String
    
    var body: some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .kerning(1.2)
            .foregroundColor(Color(UIColor.systemGray))
            .padding(.horizontal, 24)
            .padding(.top, 24)
            .padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct DeviceRow: View {
    let name: String
    let desc: String
    @Binding var isOn: Bool
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.system(size: 15, weight: .semibold))
                Text(desc)
                    .font(.system(size: 13))
                    .foregroundColor(Color(UIColor.systemGray))
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .tint(.black)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
        .background(Color.white)
    }
}

private struct MenuRow: View {
    let label: String
    var value: String? = nil
    var isDestructive = false
    var action: (() -> Void)? = nil
    
    var body: some View {
        Button(action: { action?() }) {
            HStack {
                Text(label)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(isDestructive ? .red : .black)
                Spacer()
                if let val = value {
                    Text(val)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(UIColor.systemGray))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(Color(UIColor.systemGray5))
                        .cornerRadius(12)
                }
                Image(systemName: "chevron.right")
                    .foregroundColor(Color(UIColor.systemGray4))
                    .font(.system(size: 14, weight: .bold))
                    .padding(.leading, 8)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            .background(Color.white)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
