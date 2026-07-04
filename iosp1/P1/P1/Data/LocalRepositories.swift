import Foundation

enum FollowUpRepository {
    static func followUpUi(from sub: SubscriptionResponse, agents: [AgentResponse]) -> FollowUpUi {
        let agent = agents.first(where: { $0.id == sub.agent_id })
        let title = agent?.name ?? "Tracking from \(sub.starts_at.prefix(10))"
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let start = formatter.date(from: sub.starts_at) ?? Date()
        let end = formatter.date(from: sub.expires_at)
            ?? Calendar.current.date(byAdding: .day, value: 14, to: start)!
        let totalSeconds = end.timeIntervalSince(start)
        let remainingSeconds = end.timeIntervalSince(Date())
        let elapsedSeconds = Date().timeIntervalSince(start)
        return FollowUpUi(
            id: sub.id,
            title: title,
            daysRemaining: max(0, Int(remainingSeconds / 86400)),
            totalDays: max(1, Int(totalSeconds / 86400)),
            progress: max(0, min(1, Float(elapsedSeconds / totalSeconds))),
            isActive: Date() < end,
            startsAt: sub.starts_at,
            expiresAt: sub.expires_at,
            rules: sub.parameters?.rules,
            schedule: sub.parameters?.schedule
        )
    }

    static func saveFromRemote(_ subscription: SubscriptionResponse) {
        try? LocalStore.shared.saveFollowUp(subscription)
    }

    static func getLocalFollowUps(agents: [AgentResponse] = []) -> [FollowUpUi] {
        let subs = (try? LocalStore.shared.loadFollowUps()) ?? []
        return subs.map { followUpUi(from: $0, agents: agents) }
    }

    static func loadFollowUpsWithSync() async -> (followUps: [FollowUpUi], synced: Bool) {
        do {
            let subscriptions = try await ApiService.shared.getSubscriptions()
            try LocalStore.shared.saveFollowUps(subscriptions)
            let agents = try await ApiService.shared.getAgents()
            await SyncManager.shared.syncAll()
            let ui = subscriptions.map { followUpUi(from: $0, agents: agents) }
            return (ui, true)
        } catch {
            return (getLocalFollowUps(agents: []), false)
        }
    }

    static func deleteLocal(id: String) {
        try? LocalStore.shared.deleteFollowUp(id: id)
    }
}

enum TimelineRepository {
    static func getEvents(subscriptionId: String) -> [TimelineEventResponse] {
        let stored = (try? LocalStore.shared.loadTimeline(subscriptionId: subscriptionId)) ?? []
        return stored.map { $0.toResponse() }
    }

    @discardableResult
    static func addEvent(subscriptionId: String, request: TimelineEventRequest) -> TimelineEventResponse {
        let stored = StoredTimelineEvent.pending(subscriptionId: subscriptionId, request: request)
        try? LocalStore.shared.upsertTimelineEvent(stored)
        Task { await SyncManager.shared.syncAll() }
        return stored.toResponse()
    }

    static func deleteEvent(subscriptionId: String, eventId: String) async {
        let stored = (try? LocalStore.shared.loadTimeline(subscriptionId: subscriptionId)) ?? []
        guard let target = stored.first(where: { $0.localId == eventId || $0.remoteId == eventId }) else { return }
        if target.syncStatus == .pending {
            try? LocalStore.shared.deleteTimelineEvent(subscriptionId: subscriptionId, eventId: eventId)
            return
        }
        guard let remoteId = target.remoteId else { return }
        do {
            try await ApiService.shared.deleteTimelineEvent(subscriptionId: subscriptionId, eventId: remoteId)
            try? LocalStore.shared.deleteTimelineEvent(subscriptionId: subscriptionId, eventId: remoteId)
            _ = await refreshFromRemote(subscriptionId: subscriptionId)
        } catch {
            // Keep local if offline
        }
    }

    @discardableResult
    static func refreshFromRemote(subscriptionId: String) async -> Bool {
        do {
            let remote = try await ApiService.shared.getTimeline(subscriptionId: subscriptionId)
            try LocalStore.shared.replaceSyncedTimeline(subscriptionId: subscriptionId, remoteEvents: remote)
            return true
        } catch {
            return false
        }
    }

    static func pushPending(for subscriptionId: String) async -> Int {
        let pending = ((try? LocalStore.shared.loadTimeline(subscriptionId: subscriptionId)) ?? [])
            .filter { $0.syncStatus == .pending && $0.type == "user" }
        var pushed = 0
        for event in pending {
            do {
                _ = try await ApiService.shared.postTimelineEvent(
                    subscriptionId: subscriptionId,
                    request: TimelineEventRequest(
                        content: event.content,
                        date_label: event.dateLabel,
                        effective_date: event.effectiveAt.map { String($0.prefix(10)) }
                    )
                )
                try? LocalStore.shared.deleteTimelineEvent(subscriptionId: subscriptionId, eventId: event.localId)
                pushed += 1
            } catch {
                continue
            }
        }
        if pushed > 0 {
            _ = await refreshFromRemote(subscriptionId: subscriptionId)
        }
        return pushed
    }
}

@MainActor
final class SyncManager {
    static let shared = SyncManager()
    private init() {}

    func syncAll() async {
        let followUps = (try? LocalStore.shared.loadFollowUps()) ?? []
        for sub in followUps {
            _ = await TimelineRepository.pushPending(for: sub.id)
            _ = await TimelineRepository.refreshFromRemote(subscriptionId: sub.id)
        }
        do {
            let remote = try await ApiService.shared.getSubscriptions()
            try LocalStore.shared.saveFollowUps(remote)
        } catch {
            // Keep local follow-ups
        }
    }
}

enum ReportRepository {
    static func generateOfflineReport(followUp: FollowUpUi, patientName: String) async -> URL? {
        let events = TimelineRepository.getEvents(subscriptionId: followUp.id)
        let temp = await Task.detached(priority: .userInitiated) {
            PdfReportGenerator.generate(followUp: followUp, events: events, patientName: patientName)
        }.value
        guard let temp else { return nil }
        return try? LocalStore.shared.saveReport(subscriptionId: followUp.id, from: temp)
    }
}
