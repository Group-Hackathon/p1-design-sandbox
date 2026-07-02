import SwiftUI

struct WelcomeScreen: View {
    let onStartTracking: () -> Void
    let onGoToHome: () -> Void
    
    @State private var currentSlide = 0
    
    private let slides = [
        ("Prepare your medical appointments", "Track symptoms, vitals, and photos daily. Your doctor gets a complete picture, ready at appointment time."),
        ("Evidence-based tracking rules", "Choose what to track (vitals, photos). We prepare the best personalized protocol based on thousands of medical records.")
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
            
            // Slide Content
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
            .id(currentSlide) // Trigger animation on change
            .transition(.asymmetric(
                insertion: .opacity.combined(with: .move(edge: .trailing)),
                removal: .opacity.combined(with: .move(edge: .leading))
            ))
            
            Spacer()
            
            // Dots indicator
            HStack(spacing: 8) {
                ForEach(0..<slides.count, id: \.self) { index in
                    Circle()
                        .fill(currentSlide == index ? Color.black : Color(UIColor.systemGray5))
                        .frame(width: currentSlide == index ? 10 : 8, height: currentSlide == index ? 10 : 8)
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: currentSlide)
                }
            }
            .padding(.vertical, 32)
            
            // Buttons
            VStack(spacing: 16) {
                if currentSlide < slides.count - 1 {
                    LpmPrimaryButton(text: "Next") {
                        withAnimation {
                            currentSlide += 1
                        }
                    }
                    
                    Button(action: onGoToHome) {
                        Text("Skip — go to dashboard")
                            .fontWeight(.medium)
                            .foregroundColor(Color(UIColor.systemGray))
                    }
                    .padding(.vertical, 8)
                } else {
                    LpmPrimaryButton(text: "Start a new tracking", action: onStartTracking)
                    
                    Button(action: onGoToHome) {
                        Text("Go to Dashboard")
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
