import SwiftUI

struct WelcomeScreen: View {
    let onStartTracking: () -> Void
    let onGoToHome: () -> Void
    
    @State private var currentSlide = 0
    
    private let slides: [(String, String)] = [
        (String(localized: "welcome_slide_1_title"), String(localized: "welcome_slide_1_body")),
        (String(localized: "welcome_slide_2_title"), String(localized: "welcome_slide_2_body")),
        (String(localized: "welcome_slide_3_title"), String(localized: "welcome_slide_3_body"))
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 60)
            
            Image("logo")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            
            Spacer()
            
            VStack(spacing: 16) {
                Text(slides[currentSlide].0)
                    .font(.system(size: 32, weight: .heavy, design: .default))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                
                Text(slides[currentSlide].1)
                    .font(.body)
                    .foregroundColor(Color(UIColor.systemGray))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .lineSpacing(6)
            }
            .id(currentSlide)
            .transition(.asymmetric(
                insertion: .opacity.combined(with: .move(edge: .trailing)),
                removal: .opacity.combined(with: .move(edge: .leading))
            ))
            
            Spacer()
            
            HStack(spacing: 8) {
                ForEach(0..<slides.count, id: \.self) { index in
                    Circle()
                        .fill(currentSlide == index ? Color.black : Color(UIColor.systemGray5))
                        .frame(width: currentSlide == index ? 10 : 8, height: currentSlide == index ? 10 : 8)
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: currentSlide)
                }
            }
            .padding(.vertical, 32)
            
            VStack(spacing: 16) {
                Link("By continuing, you accept our Terms of Use & Privacy Policy", destination: URL(string: "https://p1-privacy-policy.pages.dev/")!)
                    .font(.system(size: 11))
                    .foregroundColor(Color(UIColor.systemGray))
                    .underline()
                    .padding(.bottom, 8)
                    
                if currentSlide < slides.count - 1 {
                    LpmPrimaryButton(text: String(localized: "welcome_next")) {
                        withAnimation {
                            currentSlide += 1
                        }
                    }
                    
                    Button(action: onGoToHome) {
                        Text(String(localized: "welcome_skip"))
                            .fontWeight(.medium)
                            .foregroundColor(Color(UIColor.systemGray))
                    }
                    .padding(.vertical, 8)
                } else {
                    LpmPrimaryButton(text: String(localized: "welcome_start"), action: onStartTracking)
                    
                    Button(action: onGoToHome) {
                        Text(String(localized: "welcome_go_home"))
                            .fontWeight(.medium)
                            .foregroundColor(Color(UIColor.systemGray))
                    }
                    .padding(.vertical, 8)
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 12)

            Text("v\(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")")
                .font(.system(size: 11))
                .foregroundColor(Color(UIColor.systemGray3))
                .padding(.bottom, 12)
        }
        .background(Color.white)
    }
}

#Preview {
    WelcomeScreen(onStartTracking: {}, onGoToHome: {})
}
