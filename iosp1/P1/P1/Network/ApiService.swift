import Foundation

class ApiService {
    static let shared = ApiService()
    
    private let client = ApiClient.shared
    
    private init() {}
    
    // MARK: - Auth
    func register(request: AuthRequest, allowAuthRetry: Bool = true) async throws -> AuthResponse {
        return try await client.performRequest(
            path: "api/v1/auth/register",
            method: "POST",
            body: request,
            allowAuthRetry: allowAuthRetry
        )
    }

    func login(request: AuthRequest, allowAuthRetry: Bool = true) async throws -> AuthResponse {
        return try await client.performRequest(
            path: "api/v1/auth/login",
            method: "POST",
            body: request,
            allowAuthRetry: allowAuthRetry
        )
    }
    
    func deleteAccount() async throws {
        try await client.performRequest(path: "api/v1/auth/delete", method: "DELETE")
    }
    
    // MARK: - Agents
    func recommendAgent(request: RecommendRequest) async throws -> AgentResponse {
        return try await client.performRequest(path: "api/v1/agents/recommend", method: "POST", body: request)
    }
    
    func getAgents() async throws -> [AgentResponse] {
        return try await client.performRequest(path: "api/v1/agents", method: "GET")
    }
    
    // MARK: - Profiles
    func getProfiles() async throws -> [ProfileResponse] {
        return try await client.performRequest(path: "api/v1/profiles", method: "GET")
    }
    
    func createProfile(request: ProfileRequest) async throws -> ProfileResponse {
        return try await client.performRequest(path: "api/v1/profiles", method: "POST", body: request)
    }
    
    // MARK: - Subscriptions
    func getSubscriptions() async throws -> [SubscriptionResponse] {
        return try await client.performRequest(path: "api/v1/subscriptions", method: "GET")
    }
    
    func createSubscription(request: SubscriptionRequest) async throws -> SubscriptionResponse {
        return try await client.performRequest(path: "api/v1/subscriptions", method: "POST", body: request)
    }
    
    func patchSubscription(id: String, request: UpdateSubscriptionRequest) async throws -> SubscriptionResponse {
        return try await client.performRequest(path: "api/v1/subscriptions/\(id)", method: "PATCH", body: request)
    }
    
    func deleteSubscription(id: String) async throws {
        try await client.performRequest(path: "api/v1/subscriptions/\(id)", method: "DELETE")
    }
    
    // MARK: - Timeline
    func getTimeline(subscriptionId: String) async throws -> [TimelineEventResponse] {
        return try await client.performRequest(path: "api/v1/subscriptions/\(subscriptionId)/timeline", method: "GET")
    }
    
    func postTimelineEvent(subscriptionId: String, request: TimelineEventRequest) async throws -> TimelineEventResponse {
        return try await client.performRequest(path: "api/v1/subscriptions/\(subscriptionId)/timeline", method: "POST", body: request)
    }
    
    func deleteTimelineEvent(subscriptionId: String, eventId: String) async throws {
        try await client.performRequest(path: "api/v1/subscriptions/\(subscriptionId)/timeline/\(eventId)", method: "DELETE")
    }
}
