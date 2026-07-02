import Foundation

// MARK: - Auth
struct AuthRequest: Codable {
    let email: String
    let password: String
}

struct AuthResponse: Codable {
    let token: String
    let user: User
}

struct User: Codable {
    let id: String
    let email: String
    let created_at: String
}

// MARK: - Agents
struct RecommendRequest: Codable {
    let symptoms: String
    let appointment_date: String?
    let rules: TrackingRulesDto?
    let local_time: String?
    let timezone: String?
}

struct AgentResponse: Codable, Identifiable {
    let id: String
    let name: String
    let version: String
    let category: String
    let description: String
    let price_cents: Int
    let duration_days_min: Int
    let duration_days_max: Int
    let gemini_model: String
    let schedule: [String: [String]]?
}

// MARK: - Profiles
struct ProfileRequest: Codable {
    let first_name: String
    let last_name: String
    let relation: String
}

struct ProfileResponse: Codable, Identifiable {
    let id: String
    let first_name: String
    let last_name: String
    let relation: String
    let created_at: String
}

// MARK: - Subscriptions
struct SubscriptionRequestParams: Codable {
    var title: String?
    var symptoms: String?
    var next_appointment: String?
    var plan: String?
    var schedule: [String: [String]]?
    var rules: TrackingRulesDto?
}

struct SubscriptionRequest: Codable {
    let profile_id: String
    let agent_id: String
    let duration_days: Int
    let private_backend_url: String?
    let parameters: SubscriptionRequestParams?
}

struct SubscriptionResponse: Codable, Identifiable {
    let id: String
    let profile_id: String
    let agent_id: String
    let status: String
    let private_backend_url: String
    let starts_at: String
    let expires_at: String
    let parameters: SubscriptionParameters?
}

struct SubscriptionParameters: Codable {
    let rules: FollowUpRules?
    let schedule: [String: [String]]?
}

struct UpdateSubscriptionRequest: Codable {
    let expires_at: String
}

// MARK: - Rules
struct TrackingRulesDto: Codable {
    var temperature: Bool = false
    var pain: Bool = true
    var photos: Bool = true
    var smartwatch: Bool = false
    var blood_pressure: Bool = false
    var custom: String = ""
}

struct TrackingRules: Codable {
    var temperature: Bool = true
    var pain: Bool = true
    var photos: Bool = true
    var smartwatch: Bool = false
    var bloodPressure: Bool = false
    var custom: String = ""
}

struct FollowUpRules: Codable, Equatable {
    var temperature: Bool? = true
    var pain: Bool? = true
    var photos: Bool? = true
    var smartwatch: Bool? = false
    var blood_pressure: Bool? = false
    var custom: String? = ""
}

struct PlanItem: Codable {
    let icon: String
    let title: String
    let description: String
}

// MARK: - Timeline
struct TimelineEventRequest: Codable {
    let content: String
    let date_label: String
    let effective_date: String?
}

struct TimelineEventResponse: Codable, Identifiable {
    let id: String
    let subscription_id: String
    let type: String
    let date_label: String
    let content: String
    let created_at: String
    let effective_at: String?
}
