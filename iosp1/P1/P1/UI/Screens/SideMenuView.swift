import SwiftUI

struct SideMenuView: View {
    @Binding var isOpen: Bool
    let onNavigate: (AppRoute) -> Void
    
    @State private var followUps: [FollowUpUi] = []
    
    var body: some View {
        ZStack(alignment: .leading) {
            // Background overlay
            if isOpen {
                Color.black.opacity(0.3)
                    .edgesIgnoringSafeArea(.all)
                    .transition(.opacity)
                    .animation(.easeInOut(duration: 0.3), value: isOpen)
                    .onTapGesture {
                        withAnimation { isOpen = false }
                    }
            }
            
            // Drawer
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 60)
                
                // Profile Header
                Button(action: {
                    withAnimation { isOpen = false }
                    onNavigate(.profile)
                }) {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color.black)
                            .frame(width: 40, height: 40)
                            .overlay(
                                Text(SessionManager.shared.getUserName()?.prefix(1).uppercased() ?? "P")
                                    .fontWeight(.bold)
                                    .foregroundColor(.white)
                            )
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(SessionManager.shared.getUserName() ?? "Patient")
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundColor(.black)
                            Text("View profile")
                                .font(.caption)
                                .foregroundColor(Color(UIColor.systemGray2))
                        }
                    }
                    .padding()
                    .background(Color(UIColor.systemGray6))
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                
                Spacer().frame(height: 24)
                
                Text(String(localized: "my_trackings"))
                    .font(.caption)
                    .fontWeight(.bold)
                    .kerning(1)
                    .foregroundColor(.gray)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 8)
                
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(followUps) { followUp in
                            Button(action: {
                                withAnimation { isOpen = false }
                                onNavigate(.journey(followUp))
                            }) {
                                HStack(spacing: 16) {
                                    Circle()
                                        .fill(followUp.isActive ? Color.black : Color(UIColor.systemGray5))
                                        .frame(width: 10, height: 10)
                                    
                                    Text(followUp.title)
                                        .font(.headline)
                                        .foregroundColor(.black)
                                    Spacer()
                                }
                                .padding(.horizontal, 24)
                                .padding(.vertical, 16)
                            }
                        }
                        
                        Button(action: {
                            withAnimation { isOpen = false }
                            onNavigate(.onboarding)
                        }) {
                            HStack(spacing: 12) {
                                Image(systemName: "plus.circle")
                                    .font(.title3)
                                    .foregroundColor(.gray)
                                
                                Text(String(localized: "start_new_tracking"))
                                    .font(.headline)
                                    .foregroundColor(.gray)
                                Spacer()
                            }
                            .padding(.horizontal, 24)
                            .padding(.vertical, 16)
                        }
                    }
                }
                
                }
                .frame(width: 300)
                .frame(maxHeight: .infinity)
                .background(Color.white)
                .edgesIgnoringSafeArea(.all)
                .offset(x: isOpen ? 0 : -300)
                .animation(.easeInOut(duration: 0.3), value: isOpen)
                
                Spacer()
            }
        }
        .allowsHitTesting(isOpen)
        .task {
            await loadData()
        }
    }
    
    private func loadData() async {
        guard SessionManager.shared.getToken() != nil else { return }
        let result = await FollowUpRepository.loadFollowUpsWithSync()
        self.followUps = result.followUps
    }
}
