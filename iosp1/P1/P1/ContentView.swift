//
//  ContentView.swift
//  P1
//
//  Created by Gary on 21/06/2026.
//

import SwiftUI

enum AppRoute: Equatable {
    case welcome
    case onboarding
    case dashboard
    case profile
    case notifications
    case journey(FollowUpUi)
    case report(FollowUpUi)
    case routine(FollowUpUi)
    
    static func == (lhs: AppRoute, rhs: AppRoute) -> Bool {
        switch (lhs, rhs) {
        case (.welcome, .welcome), (.onboarding, .onboarding), (.dashboard, .dashboard), (.profile, .profile), (.notifications, .notifications):
            return true
        case (.journey(let l), .journey(let r)):
            return l.id == r.id
        case (.routine(let l), .routine(let r)):
            return l.id == r.id
        default:
            return false
        }
    }
}

struct ContentView: View {
    @State private var currentRoute: AppRoute = .welcome
    @State private var isAuthenticated: Bool = false
    @State private var isDrawerOpen = false
    @State private var showSplash = true
    
    var body: some View {
        ZStack {
            if showSplash {
                SplashScreen()
                    .transition(.opacity)
            } else {
            mainContent
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            withAnimation { showSplash = false }
        }
    }

    @ViewBuilder
    private var mainContent: some View {
        ZStack {
            switch currentRoute {
            case .welcome:
                WelcomeScreen(
                    onStartTracking: { currentRoute = .onboarding },
                    onGoToHome: { currentRoute = .dashboard }
                )
            case .onboarding:
                OnboardingScreen(
                    onBack: { currentRoute = .welcome },
                    onFollowUpCreated: { subscriptionId in
                        // Short delay then navigate directly to the new journey
                        Task {
                            try? await Task.sleep(nanoseconds: 800_000_000)
                            let agents = (try? await ApiService.shared.getAgents()) ?? []
                            let local = FollowUpRepository.getLocalFollowUps(agents: agents)
                            if let followUp = local.first(where: { $0.id == subscriptionId }) {
                                await MainActor.run {
                                    currentRoute = .journey(followUp)
                                }
                            } else {
                                await MainActor.run { currentRoute = .dashboard }
                            }
                        }
                    }
                )
            case .dashboard:
                DashboardScreen(
                    onNewFollowUp: { currentRoute = .onboarding },
                    onOpenJourney: { followUp in
                        currentRoute = .journey(followUp)
                    },
                    onOpenNotifications: {
                        currentRoute = .notifications
                    },
                    onOpenDrawer: {
                        withAnimation { isDrawerOpen = true }
                    }
                )
            case .journey(let followUp):
                JourneyScreen(
                    followUp: followUp,
                    onOpenDrawer: { withAnimation { isDrawerOpen = true } },
                    onOpenReport: { currentRoute = .report(followUp) },
                    onStartRoutine: { currentRoute = .routine(followUp) }
                )
            case .report(let followUp):
                ReportScreen(
                    followUp: followUp,
                    onBack: { currentRoute = .journey(followUp) }
                )
            case .routine(let followUp):
                DailyRoutineScreen(
                    followUpId: followUp.id,
                    followUpTitle: followUp.title,
                    rules: followUp.rules,
                    onBack: { currentRoute = .journey(followUp) },
                    onComplete: { currentRoute = .journey(followUp) }
                )
            case .notifications:
                NotificationsScreen(onBack: { currentRoute = .dashboard })
            case .profile:
                ProfileScreen(
                    onOpenDrawer: { withAnimation { isDrawerOpen = true } },
                    onLogout: { currentRoute = .welcome }
                )
            }
            
            if currentRoute != .welcome && currentRoute != .onboarding {
                SideMenuView(isOpen: $isDrawerOpen, onNavigate: { route in
                    currentRoute = route
                })
            }
        }
        .animation(.easeInOut, value: currentRoute)
        .task {
            // Only auto-login if we already have a token
            if SessionManager.shared.getToken() != nil {
                isAuthenticated = await AuthHelper.shared.ensureAuthenticated()
                if isAuthenticated {
                    _ = await AuthHelper.shared.ensureProfile()
                    currentRoute = .dashboard
                }
            } else {
                // Authenticate silently in the background, but let the user see the Welcome Screen
                Task {
                    isAuthenticated = await AuthHelper.shared.ensureAuthenticated()
                    if isAuthenticated {
                        _ = await AuthHelper.shared.ensureProfile()
                    }
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
