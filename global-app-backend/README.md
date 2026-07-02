# Global App Backend (Central Server)

This directory represents the central, global application backend for **Pre-Appointment 1**.

**Deployed MVP instance (GCP Cloud Run):**  
`https://living-patient-memory-api-772480669824.us-central1.run.app`

Mobile clients (`androidp1/`, `iosp1/`) point to this URL via `ApiClient`.

While sensitive medical records and photos are kept isolated in each user's private backend instance (VPC), the central server handles the global orchestrations, user accounts, agent catalogs, and payments.

## Role of the Global Backend

1. **User Account & Profiles**:
   - Manages global user credentials, profile creation (e.g., parent account, patient profiles, doctor listings).
   - Serves as the authentication gateway for the mobile app (APK).
2. **Analysis Agent Catalog**:
   - Manages the listing of available Specialized Analysis Agents (Wound Monitoring, Dermatology, etc.).
   - Provides agent configurations, pricing tiers, and system prompt templates.
3. **Billing & Subscriptions (Stripe Integration)**:
   - Tracks active agent subscriptions and purchases per patient.
   - Manages license keys or access grants for cloud analysis runs.
4. **Agent Orchestration**:
   - Scheduled worker jobs (using Cloud Run or background queues) that query the user's private backend (using temporary tokens issued by the user's VPC) to execute Gemini multimodal analyses.

---

## Technology Stack

- **API Framework**: Go (or Node.js/TypeScript)
- **Database**: PostgreSQL (Global Database)
  - Stores: user credentials (hashed), billing history, active agent catalog, profile names, and links to user-specific private backend URLs.
  - **No medical records, symptoms, or patient photos are stored here.**
- **Payment Processor**: Stripe API
- **Model / Inference API**: Google Cloud Vertex AI SDK (Gemini 1.5 Pro / Flash)
- **Inference Runtime**: Cloud Run jobs

---

## Conceptual Global Database Schema (PostgreSQL)

```sql
-- Global users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Patient profiles (under a user account)
CREATE TABLE profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    relation VARCHAR(50), -- e.g., 'Self', 'Child', 'Parent'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Available agents catalog
CREATE TABLE agent_catalog (
    id VARCHAR(100) PRIMARY KEY, -- e.g., 'wound-monitoring'
    name VARCHAR(255) NOT NULL,
    version VARCHAR(20) NOT NULL,
    description TEXT,
    price_cents INT NOT NULL,
    duration_days INT NOT NULL
);

-- Active subscriptions/purchases
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    agent_id VARCHAR(100) REFERENCES agent_catalog(id),
    stripe_session_id VARCHAR(255),
    status VARCHAR(50), -- 'active', 'completed', 'expired'
    private_backend_url VARCHAR(555) NOT NULL, -- URL of user's VPC
    access_token TEXT NOT NULL, -- Scoped token to access user's VPC
    starts_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);
```
