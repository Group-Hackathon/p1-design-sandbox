package com.preappointment1.app.data.model

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: User
)

data class DeviceChallengeRequest(
    val deviceId: String,
    val intent: String = "verify"
)

data class DeviceChallengeResponse(
    val nonce: String,
    val deviceId: String
)

data class DeviceRegisterRequest(
    val deviceId: String,
    val publicKey: String,
    val timestamp: Long,
    val nonce: String,
    val signature: String,
    val hardwareBindingId: String,
    val hardwarePlatform: String
)

data class DeviceVerifyRequest(
    val deviceId: String,
    val nonce: String,
    val signature: String
)

data class DeviceAuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
    val user: User,
    val isNew: Boolean? = null,
    val recovered: Boolean? = null
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String
)

data class RecommendRequest(
    val symptoms: String,
    val appointment_date: String? = null,
    val rules: TrackingRulesDto? = null,
    val local_time: String? = null,
    val timezone: String? = null
)

data class User(
    val id: String,
    val email: String,
    val created_at: String
)

data class ProfileResponse(
    val id: String,
    val first_name: String,
    val last_name: String,
    val relation: String,
    val created_at: String
)

data class ProfileRequest(
    val first_name: String,
    val last_name: String,
    val relation: String
)

data class AgentResponse(
    val id: String,
    val name: String,
    val version: String,
    val category: String,
    val description: String,
    val price_cents: Int,
    val duration_days_min: Int,
    val duration_days_max: Int,
    val gemini_model: String,
    val schedule: Map<String, List<String>>? = null
)

data class SubscriptionRequest(
    val profile_id: String,
    val agent_id: String,
    val duration_days: Int = 0,
    val private_backend_url: String? = null,
    val parameters: Map<String, Any>? = null
)

data class SubscriptionResponse(
    val id: String,
    val profile_id: String,
    val agent_id: String,
    val status: String,
    val private_backend_url: String,
    val starts_at: String,
    val expires_at: String,
    val parameters: Map<String, Any>? = null
)

// ── New models for the refonte ──

data class TrackingRulesDto(
    val temperature: Boolean = false,
    val pain: Boolean = true,
    val photos: Boolean = true,
    val smartwatch: Boolean = false,
    val blood_pressure: Boolean = false,
    val custom: String = ""
)

/** Local-only model representing tracking rules on the device */
data class TrackingRules(
    val temperature: Boolean = true,
    val pain: Boolean = true,
    val photos: Boolean = true,
    val smartwatch: Boolean = false,
    val bloodPressure: Boolean = false,
    val custom: String = ""
)

data class FollowUpRules(
    val temperature: Boolean,
    val pain: Boolean,
    val photos: Boolean,
    val smartwatch: Boolean,
    val bloodPressure: Boolean
)

/** Local-only model representing an AI-generated plan item */
data class PlanItem(
    val icon: String,
    val title: String,
    val description: String
)

data class TimelineEventRequest(
    val content: String,
    val date_label: String,
    val effective_date: String? = null
)

data class TimelineEventResponse(
    val id: String,
    val subscription_id: String,
    val type: String,
    val date_label: String,
    val content: String,
    val created_at: String,
    val effective_at: String? = null
)

data class UpdateSubscriptionRequest(
    val expires_at: String? = null,
    val parameters: Map<String, Any>? = null
)
