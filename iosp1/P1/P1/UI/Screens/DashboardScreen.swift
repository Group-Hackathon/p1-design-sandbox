import SwiftUI

struct FollowUpUi: Identifiable, Equatable {
    let id: String
    let title: String
    let daysRemaining: Int
    let totalDays: Int
    let progress: Float
    let isActive: Bool
    let startsAt: String
    let expiresAt: String
    let rules: FollowUpRules?
    let schedule: [String: [String]]?
}

struct DashboardScreen: View {
    let onNewFollowUp: () -> Void
    let onOpenJourney: (FollowUpUi) -> Void
    let onOpenNotifications: () -> Void
    let onOpenDrawer: () -> Void

    @State private var followUps: [FollowUpUi] = []
    @State private var timelineByFollowUpId: [String: [TimelineEventResponse]] = [:]
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isOfflineMode = false
    @State private var pendingSyncCount = 0

    private var greeting: String { FileStats.greetingPrefix() }

    private var firstName: String? {
        SessionManager.shared.getUserName()?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ")
            .first
            .map(String.init)
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Button(action: onOpenDrawer) {
                        Image(systemName: "line.3.horizontal")
                            .font(.title2)
                            .foregroundColor(.black)
                    }

                    Text("P1")
                        .font(.title2)
                        .fontWeight(.bold)
                        .padding(.leading, 8)
                    
                    if isOfflineMode || pendingSyncCount > 0 {
                        Text(isOfflineMode ? "Offline" : "\(pendingSyncCount) pending")
                            .font(.caption2)
                            .foregroundColor(Color(UIColor.systemGray))
                    }
                    
                    Spacer()

                    Button(action: onOpenNotifications) {
                        Image(systemName: "bell")
                            .font(.title3)
                            .foregroundColor(.black)
                    }
                }
                .padding()

                content
            }
        }
        .task { await loadData() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            Spacer()
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .black))
                .scaleEffect(1.5)
            Spacer()
        } else if let error = errorMessage {
            Spacer()
            VStack(spacing: 16) {
                Text(error)
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                LpmPrimaryButton(text: "Retry") { Task { await loadData() } }
            }
            .padding()
            Spacer()
        } else if followUps.filter({ $0.isActive }).isEmpty {
            Spacer()
            VStack(spacing: 16) {
                Text(String(localized: "welcome_title"))
                    .font(.title2)
                    .fontWeight(.black)
                    .multilineTextAlignment(.center)

                Text(String(localized: "welcome_desc"))
                    .font(.body)
                    .foregroundColor(Color(UIColor.systemGray))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                LpmPrimaryButton(text: String(localized: "welcome_start"), action: onNewFollowUp)
            }
            .padding()
            Spacer()
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 4) {
                        if let name = firstName {
                            Text("\(greeting), \(name)")
                                .font(.title2)
                                .fontWeight(.black)
                        } else {
                            Text(greeting)
                                .font(.title2)
                                .fontWeight(.black)
                        }
                        Text(String(localized: "dashboard_subtitle"))
                            .font(.body)
                            .foregroundColor(Color(UIColor.systemGray))
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)

                    ForEach(followUps.filter { $0.isActive }) { followUp in
                        let events = timelineByFollowUpId[followUp.id] ?? []
                        FollowUpFileCard(
                            followUp: followUp,
                            readiness: FileStats.compute(followUp: followUp, events: events),
                            onTap: { onOpenJourney(followUp) }
                        )
                        .padding(.horizontal, 24)
                        .padding(.vertical, 8)
                    }
                }
                .padding(.bottom, 32)
            }
        }
    }

    private func loadData() async {
        isLoading = true
        errorMessage = nil
        
        guard SessionManager.shared.getToken() != nil else {
            errorMessage = "Unable to connect. Check your network."
            followUps = []
            isLoading = false
            return
        }
        
        let result = await FollowUpRepository.loadFollowUpsWithSync()
        followUps = result.followUps
        isOfflineMode = !result.synced
        
        var timelines: [String: [TimelineEventResponse]] = [:]
        for followUp in followUps.filter({ $0.isActive }) {
            timelines[followUp.id] = TimelineRepository.getEvents(subscriptionId: followUp.id)
        }
        timelineByFollowUpId = timelines
        pendingSyncCount = LocalStore.shared.pendingCount()
        
        isLoading = false
    }
}

private struct FollowUpFileCard: View {
    let followUp: FollowUpUi
    let readiness: FileReadiness
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(followUp.title)
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.black)
                        Text(
                            followUp.daysRemaining == 0
                                ? String(localized: "file_appt_today")
                                : String(format: String(localized: "file_appt_countdown"), followUp.daysRemaining)
                        )
                        .font(.caption)
                        .foregroundColor(Color(UIColor.systemGray))
                    }
                    Spacer()
                    Text(String(format: String(localized: "file_ready_percent"), readiness.readinessPercent))
                        .font(.headline)
                        .fontWeight(.black)
                        .foregroundColor(.black)
                }

                ProgressView(value: Double(readiness.readinessPercent), total: 100)
                    .tint(.black)

                Text(String(
                    format: String(localized: "summary_file_contents"),
                    readiness.measurementCount,
                    readiness.photoCount,
                    readiness.noteCount
                ))
                .font(.caption)
                .foregroundColor(Color(UIColor.systemGray))

                if readiness.dayStreak >= 2 {
                    Text(String(format: String(localized: "file_streak"), readiness.dayStreak))
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.black)
                }

                if let hint = readiness.missingHint {
                    Text(hint)
                        .font(.caption)
                        .foregroundColor(Color(UIColor.systemGray))
                }

                Text(readiness.tagline)
                    .font(.caption)
                    .foregroundColor(.black)
                    .fontWeight(.medium)

                Text(String(localized: "file_continue"))
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                    .padding(.top, 4)
            }
            .padding(20)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Color(UIColor.systemGray5), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}
