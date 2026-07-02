import SwiftUI

/// Brief launch screen shown on cold start (mirrors Android splash).
struct SplashScreen: View {
    @State private var visible = false

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 8) {
                Text("P1")
                    .font(.system(size: 72, weight: .black))
                    .tracking(-2)
                    .foregroundColor(.black)

                Text("PRE-APPOINTMENT")
                    .font(.system(size: 11, weight: .medium))
                    .tracking(3)
                    .foregroundColor(Color(UIColor.systemGray))
            }
            .opacity(visible ? 1 : 0)
            .animation(.easeIn(duration: 0.6), value: visible)

            VStack {
                Spacer()
                Text("v\(appVersion)")
                    .font(.system(size: 11))
                    .foregroundColor(Color(UIColor.systemGray))
                    .padding(.bottom, 28)
            }
        }
        .onAppear { visible = true }
    }
}

#Preview {
    SplashScreen()
}
