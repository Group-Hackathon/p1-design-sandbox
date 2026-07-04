import Foundation

enum SyncStatus: String, Codable {
    case pending
    case synced
}

struct StoredTimelineEvent: Codable, Identifiable {
    let localId: String
    var remoteId: String?
    let subscriptionId: String
    let type: String
    let dateLabel: String
    let content: String
    let createdAt: String
    let effectiveAt: String?
    var syncStatus: SyncStatus

    var id: String { remoteId ?? localId }

    func toResponse() -> TimelineEventResponse {
        TimelineEventResponse(
            id: remoteId ?? localId,
            subscription_id: subscriptionId,
            type: type,
            date_label: dateLabel,
            content: content,
            created_at: createdAt,
            effective_at: effectiveAt
        )
    }

    static func pending(
        subscriptionId: String,
        request: TimelineEventRequest,
        localId: String = UUID().uuidString
    ) -> StoredTimelineEvent {
        let now = ISO8601DateFormatter().string(from: Date())
        return StoredTimelineEvent(
            localId: localId,
            remoteId: nil,
            subscriptionId: subscriptionId,
            type: "user",
            dateLabel: request.date_label,
            content: request.content,
            createdAt: now,
            effectiveAt: request.effective_date.map { "\($0)T12:00:00Z" },
            syncStatus: .pending
        )
    }

    static func fromRemote(_ event: TimelineEventResponse) -> StoredTimelineEvent {
        StoredTimelineEvent(
            localId: event.id,
            remoteId: event.id,
            subscriptionId: event.subscription_id,
            type: event.type,
            dateLabel: event.date_label,
            content: event.content,
            createdAt: event.created_at,
            effectiveAt: event.effective_at,
            syncStatus: .synced
        )
    }
}

final class LocalStore {
    static let shared = LocalStore()

    private let fileManager = FileManager.default
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private var baseURL: URL {
        let dir = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("p1_local", isDirectory: true)
        if !fileManager.fileExists(atPath: dir.path) {
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    private func followUpsURL() -> URL { baseURL.appendingPathComponent("follow_ups.json") }
    private func timelineURL(subscriptionId: String) -> URL {
        baseURL.appendingPathComponent("timeline_\(subscriptionId).json")
    }
    private func reportURL(subscriptionId: String) -> URL {
        baseURL.appendingPathComponent("report_\(subscriptionId).pdf")
    }

    // MARK: - Follow-ups

    func saveFollowUps(_ subscriptions: [SubscriptionResponse]) throws {
        let data = try encoder.encode(subscriptions)
        try data.write(to: followUpsURL(), options: .atomic)
    }

    func saveFollowUp(_ subscription: SubscriptionResponse) throws {
        var all = (try? loadFollowUps()) ?? []
        if let idx = all.firstIndex(where: { $0.id == subscription.id }) {
            all[idx] = subscription
        } else {
            all.append(subscription)
        }
        try saveFollowUps(all)
    }

    func loadFollowUps() throws -> [SubscriptionResponse] {
        let url = followUpsURL()
        guard fileManager.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        return try decoder.decode([SubscriptionResponse].self, from: data)
    }

    func deleteFollowUp(id: String) throws {
        var all = (try? loadFollowUps()) ?? []
        all.removeAll { $0.id == id }
        try saveFollowUps(all)
        let timelineFile = timelineURL(subscriptionId: id)
        if fileManager.fileExists(atPath: timelineFile.path) {
            try fileManager.removeItem(at: timelineFile)
        }
    }

    // MARK: - Timeline

    func loadTimeline(subscriptionId: String) throws -> [StoredTimelineEvent] {
        let url = timelineURL(subscriptionId: subscriptionId)
        guard fileManager.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        return try decoder.decode([StoredTimelineEvent].self, from: data)
    }

    private func saveTimeline(subscriptionId: String, events: [StoredTimelineEvent]) throws {
        let data = try encoder.encode(events)
        try data.write(to: timelineURL(subscriptionId: subscriptionId), options: .atomic)
    }

    func upsertTimelineEvent(_ event: StoredTimelineEvent) throws {
        var events = (try? loadTimeline(subscriptionId: event.subscriptionId)) ?? []
        if let idx = events.firstIndex(where: { $0.localId == event.localId }) {
            events[idx] = event
        } else {
            events.append(event)
        }
        events.sort { ($0.effectiveAt ?? $0.createdAt) < ($1.effectiveAt ?? $1.createdAt) }
        try saveTimeline(subscriptionId: event.subscriptionId, events: events)
    }

    func replaceSyncedTimeline(subscriptionId: String, remoteEvents: [TimelineEventResponse]) throws {
        let pending = (try? loadTimeline(subscriptionId: subscriptionId))?.filter { $0.syncStatus == .pending } ?? []
        let synced = remoteEvents.map { StoredTimelineEvent.fromRemote($0) }
        let merged = (synced + pending).sorted { ($0.effectiveAt ?? $0.createdAt) < ($1.effectiveAt ?? $1.createdAt) }
        try saveTimeline(subscriptionId: subscriptionId, events: merged)
    }

    func deleteTimelineEvent(subscriptionId: String, eventId: String) throws {
        var events = (try? loadTimeline(subscriptionId: subscriptionId)) ?? []
        events.removeAll { $0.localId == eventId || $0.remoteId == eventId }
        try saveTimeline(subscriptionId: subscriptionId, events: events)
    }

    func pendingEvents() throws -> [StoredTimelineEvent] {
        let followUps = (try? loadFollowUps()) ?? []
        return try followUps.flatMap { sub in
            try loadTimeline(subscriptionId: sub.id).filter { $0.syncStatus == .pending }
        }
    }

    func pendingCount() -> Int {
        (try? pendingEvents().count) ?? 0
    }

    // MARK: - Reports

    func cachedReportURL(subscriptionId: String) -> URL? {
        let url = reportURL(subscriptionId: subscriptionId)
        return fileManager.fileExists(atPath: url.path) ? url : nil
    }

    func saveReport(subscriptionId: String, from source: URL) throws -> URL {
        let dest = reportURL(subscriptionId: subscriptionId)
        if fileManager.fileExists(atPath: dest.path) {
            try fileManager.removeItem(at: dest)
        }
        try fileManager.copyItem(at: source, to: dest)
        return dest
    }
}
