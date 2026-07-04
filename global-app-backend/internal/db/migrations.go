package db

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

// RunMigrations creates the database schema if it doesn't exist.
func RunMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	migrations := []string{
		// Enable UUID extension
		`CREATE EXTENSION IF NOT EXISTS "pgcrypto"`,

		// Global users table
		`CREATE TABLE IF NOT EXISTS users (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			email VARCHAR(255) UNIQUE NOT NULL,
			password_hash VARCHAR(255) NOT NULL,
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		// Patient profiles (under a user account)
		`CREATE TABLE IF NOT EXISTS profiles (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			user_id UUID REFERENCES users(id) ON DELETE CASCADE,
			first_name VARCHAR(100) NOT NULL,
			last_name VARCHAR(100) NOT NULL,
			relation VARCHAR(50) DEFAULT 'Self',
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		// Analysis agent catalog
		`CREATE TABLE IF NOT EXISTS agent_catalog (
			id VARCHAR(100) PRIMARY KEY,
			name VARCHAR(255) NOT NULL,
			version VARCHAR(20) NOT NULL,
			category VARCHAR(100) NOT NULL,
			description TEXT,
			price_cents INT NOT NULL,
			duration_days_min INT NOT NULL DEFAULT 7,
			duration_days_max INT NOT NULL DEFAULT 21,
			duration_days_default INT NOT NULL DEFAULT 14,
			gemini_model VARCHAR(100) NOT NULL DEFAULT 'gemini-3.5-flash',
			system_prompt TEXT,
			collection_plan_json JSONB,
			briefing_schema_json JSONB,
			active BOOLEAN NOT NULL DEFAULT true
		)`,

		// Active subscriptions / follow-ups
		`CREATE TABLE IF NOT EXISTS subscriptions (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			profile_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
			agent_id VARCHAR(100) REFERENCES agent_catalog(id),
			status VARCHAR(50) NOT NULL DEFAULT 'active',
			private_backend_url VARCHAR(555),
			access_token TEXT,
			parameters_json JSONB,
			starts_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
			expires_at TIMESTAMP WITH TIME ZONE,
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		// Vertical timeline events
		`CREATE TABLE IF NOT EXISTS timeline_events (
			id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
			subscription_id UUID REFERENCES subscriptions(id) ON DELETE CASCADE,
			type VARCHAR(50) NOT NULL, -- 'user', 'ai', 'system'
			date_label VARCHAR(100), -- e.g. 'Day 1 - Evening'
			content TEXT NOT NULL,
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		// Add effective_at for retroactive measurements
		`ALTER TABLE timeline_events ADD COLUMN IF NOT EXISTS effective_at TIMESTAMP WITH TIME ZONE`,

		// Device identity auth (RankMyAura-style, Neon/Postgres)
		`CREATE TABLE IF NOT EXISTS devices (
			id VARCHAR(128) PRIMARY KEY,
			user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
			public_key TEXT NOT NULL,
			hardware_binding_id VARCHAR(128) UNIQUE NOT NULL,
			hardware_platform VARCHAR(32) NOT NULL DEFAULT 'unknown',
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		`CREATE TABLE IF NOT EXISTS auth_nonces (
			device_id VARCHAR(128) PRIMARY KEY,
			intent VARCHAR(20) NOT NULL,
			nonce VARCHAR(64) NOT NULL,
			expires_at TIMESTAMP WITH TIME ZONE NOT NULL
		)`,

		`CREATE TABLE IF NOT EXISTS auth_sessions (
			id VARCHAR(64) PRIMARY KEY,
			user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
			refresh_token_hash VARCHAR(64) NOT NULL,
			expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
			created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		)`,

		`CREATE INDEX IF NOT EXISTS idx_auth_sessions_user ON auth_sessions(user_id)`,
		`CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id)`,

		// Seed the wound-monitoring agent
		`INSERT INTO agent_catalog (id, name, version, category, description, price_cents, duration_days_min, duration_days_max, duration_days_default, gemini_model, system_prompt)
		VALUES (
			'wound-monitoring',
			'Wound & Injury Monitoring',
			'1.0.0',
			'wound-and-injury',
			'Daily photo capture, pain tracking, infection check-ins. Produces a physician briefing with healing trajectory and photo timeline.',
			900,
			7, 21, 14,
			'gemini-3.5-flash',
			'You are observing a healing wound over a follow-up period. From each daily photo, estimate wound surface, color distribution and edge state relative to previous days. Correlate with pain scores and infection check-ins. Describe and quantify only. Never name a diagnosis, never suggest treatment.'
		) ON CONFLICT (id) DO NOTHING`,

		// Seed the fever-episode agent
		`INSERT INTO agent_catalog (id, name, version, category, description, price_cents, duration_days_min, duration_days_max, duration_days_default, gemini_model, system_prompt)
		VALUES (
			'fever-episode',
			'Fever & Viral Infection Episode',
			'1.0.0',
			'fever-and-infection',
			'Daily temperature tracking, chills/sweats reporting, and medication logging. Generates a detailed fever curve mapped with symptoms.',
			500,
			5, 20, 10,
			'gemini-3.5-flash',
			'You are observing a fever episode over a follow-up period. Build the fever curve, detect periodicity and evening/morning patterns, correlate spikes with symptoms and medication intake times. Describe and quantify only. Never name a diagnosis, never suggest treatment.'
		) ON CONFLICT (id) DO NOTHING`,

		// Seed the post-op-recovery agent
		`INSERT INTO agent_catalog (id, name, version, category, description, price_cents, duration_days_min, duration_days_max, duration_days_default, gemini_model, system_prompt)
		VALUES (
			'post-op-recovery',
			'Post-Surgery Recovery',
			'1.1.0',
			'surgery',
			'Incision site photos, mobility tracking, pain management, and bleeding checks. Produces a comprehensive post-operative recovery timeline for the surgeon.',
			1500,
			14, 60, 30,
			'gemini-3.5-flash',
			'You are monitoring a patient recovering from surgery. Analyze daily incision photos for closure integrity and signs of infection. Track pain scale evolution and daily mobility improvements. Flag any reported bleeding or unusual swelling. Describe and quantify only.'
		) ON CONFLICT (id) DO NOTHING`,

		// Seed the skin-rash-tracker agent
		`INSERT INTO agent_catalog (id, name, version, category, description, price_cents, duration_days_min, duration_days_max, duration_days_default, gemini_model, system_prompt)
		VALUES (
			'skin-rash-tracker',
			'Skin Rash & Eczema Tracker',
			'1.0.0',
			'dermatology',
			'Daily photos with ghost overlay, itchiness scale, and trigger logging (food, stress, contact). Creates a dermatological photo strip showing spread and remission.',
			800,
			7, 30, 14,
			'gemini-3.5-flash',
			'You are observing a skin condition (rash, eczema, or allergy). Estimate the affected surface area from daily photos, track redness intensity, and correlate with reported itchiness and potential environmental triggers. Describe and quantify only.'
		) ON CONFLICT (id) DO NOTHING`,

		// Seed the dynamic-plan agent
		`INSERT INTO agent_catalog (id, name, version, category, description, price_cents, duration_days_min, duration_days_max, duration_days_default, gemini_model, system_prompt)
		VALUES (
			'dynamic-plan',
			'Personalized Protocol',
			'1.0.0',
			'dynamic',
			'A dynamically generated tracking protocol tailored by Gemini based on patient symptoms and rules.',
			0,
			1, 30, 14,
			'gemini-3.5-flash',
			'You are an AI generating personalized tracking rules.'
		) ON CONFLICT (id) DO NOTHING`,
	}

	for i, m := range migrations {
		if _, err := pool.Exec(ctx, m); err != nil {
			return fmt.Errorf("migration %d failed: %w", i, err)
		}
	}
	return nil
}
