package handlers

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/Group-Hackathon/p1/global-app-backend/internal/auth"
	"github.com/Group-Hackathon/p1/global-app-backend/internal/crypto"
)

const (
	nonceTTL         = 5 * time.Minute
	maxClockSkew     = 5 * time.Minute
	refreshSessionTTL = 30 * 24 * time.Hour
)

type deviceChallengeRequest struct {
	DeviceID string `json:"deviceId"`
	Intent   string `json:"intent"`
}

type deviceChallengeResponse struct {
	Nonce    string `json:"nonce"`
	DeviceID string `json:"deviceId"`
}

type deviceRegisterRequest struct {
	DeviceID          string `json:"deviceId"`
	PublicKey         string `json:"publicKey"`
	Timestamp         int64  `json:"timestamp"`
	Nonce             string `json:"nonce"`
	Signature         string `json:"signature"`
	HardwareBindingID string `json:"hardwareBindingId"`
	HardwarePlatform  string `json:"hardwarePlatform"`
}

type deviceVerifyRequest struct {
	DeviceID  string `json:"deviceId"`
	Nonce     string `json:"nonce"`
	Signature string `json:"signature"`
}

type deviceAuthResponse struct {
	AccessToken  string       `json:"accessToken"`
	RefreshToken string       `json:"refreshToken"`
	DeviceID     string       `json:"deviceId"`
	User         userResponse `json:"user"`
	IsNew        bool         `json:"isNew,omitempty"`
	Recovered    bool         `json:"recovered,omitempty"`
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type refreshResponse struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
}

func randomNonce() (string, error) {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func randomSessionID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeDeviceError(w http.ResponseWriter, code, message string, status int) {
	writeJSON(w, status, map[string]string{"error": code, "message": message})
}

func validTimestamp(ts int64) bool {
	now := time.Now().UnixMilli()
	diff := now - ts
	if diff < 0 {
		diff = -diff
	}
	return diff <= maxClockSkew.Milliseconds()
}

func (h *Handler) storeNonce(ctx context.Context, deviceID, intent, nonce string) error {
	_, err := h.db.Exec(ctx,
		`INSERT INTO auth_nonces (device_id, intent, nonce, expires_at)
		 VALUES ($1, $2, $3, $4)
		 ON CONFLICT (device_id) DO UPDATE SET intent = EXCLUDED.intent, nonce = EXCLUDED.nonce, expires_at = EXCLUDED.expires_at`,
		deviceID, intent, nonce, time.Now().Add(nonceTTL),
	)
	return err
}

func (h *Handler) consumeNonce(ctx context.Context, deviceID, nonce string) bool {
	var stored string
	err := h.db.QueryRow(ctx,
		`DELETE FROM auth_nonces WHERE device_id = $1 AND nonce = $2 AND expires_at > NOW() RETURNING nonce`,
		deviceID, nonce,
	).Scan(&stored)
	return err == nil
}

func (h *Handler) issueDeviceTokens(ctx context.Context, userID string) (auth.TokenPair, error) {
	sessionID, err := randomSessionID()
	if err != nil {
		return auth.TokenPair{}, err
	}
	pair, err := h.auth.IssueTokenPair(userID, sessionID)
	if err != nil {
		return auth.TokenPair{}, err
	}
	_, err = h.db.Exec(ctx,
		`INSERT INTO auth_sessions (id, user_id, refresh_token_hash, expires_at)
		 VALUES ($1, $2, $3, $4)`,
		sessionID, userID, auth.HashRefreshToken(pair.RefreshToken), time.Now().Add(refreshSessionTTL),
	)
	if err != nil {
		return auth.TokenPair{}, err
	}
	return pair, nil
}

func (h *Handler) userResponseForID(ctx context.Context, userID string) (userResponse, error) {
	var email string
	var createdAt time.Time
	err := h.db.QueryRow(ctx,
		`SELECT email, created_at FROM users WHERE id = $1`, userID,
	).Scan(&email, &createdAt)
	if err != nil {
		return userResponse{}, err
	}
	return userResponse{ID: userID, Email: email, CreatedAt: createdAt.Format(time.RFC3339)}, nil
}

// DeviceChallenge issues a one-time nonce for register/verify/recover.
func (h *Handler) DeviceChallenge(w http.ResponseWriter, r *http.Request) {
	var req deviceChallengeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.DeviceID == "" {
		writeDeviceError(w, "INVALID_BODY", "deviceId required", http.StatusBadRequest)
		return
	}
	intent := req.Intent
	if intent == "" {
		intent = "verify"
	}

	ctx := r.Context()
	var exists bool
	_ = h.db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM devices WHERE id = $1)`, req.DeviceID).Scan(&exists)

	switch intent {
	case "verify", "recover":
		if !exists {
			writeDeviceError(w, "DEVICE_NOT_FOUND", "Unknown device", http.StatusNotFound)
			return
		}
	case "register":
		if exists {
			writeDeviceError(w, "DEVICE_EXISTS", "Device already registered", http.StatusConflict)
			return
		}
	default:
		writeDeviceError(w, "INVALID_BODY", "Invalid intent", http.StatusBadRequest)
		return
	}

	nonce, err := randomNonce()
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to create nonce", http.StatusInternalServerError)
		return
	}
	if err := h.storeNonce(ctx, req.DeviceID, intent, nonce); err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to store nonce", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, deviceChallengeResponse{Nonce: nonce, DeviceID: req.DeviceID})
}

// DeviceRegister creates device + user with Ed25519 proof.
func (h *Handler) DeviceRegister(w http.ResponseWriter, r *http.Request) {
	var req deviceRegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeDeviceError(w, "INVALID_BODY", "Invalid JSON", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" || req.PublicKey == "" || req.Nonce == "" || req.Signature == "" || req.HardwareBindingID == "" {
		writeDeviceError(w, "INVALID_BODY", "Missing required fields", http.StatusBadRequest)
		return
	}
	if req.DeviceID != req.HardwareBindingID {
		writeDeviceError(w, "INVALID_BINDING", "deviceId must match hardwareBindingId", http.StatusBadRequest)
		return
	}
	if !validTimestamp(req.Timestamp) {
		writeDeviceError(w, "TIMESTAMP_INVALID", "Clock skew too large", http.StatusBadRequest)
		return
	}

	ctx := r.Context()
	if !h.consumeNonce(ctx, req.DeviceID, req.Nonce) {
		writeDeviceError(w, "NONCE_INVALID", "Invalid or expired nonce", http.StatusUnauthorized)
		return
	}

	payload := crypto.RegisterPayload(req.DeviceID, req.PublicKey, req.Timestamp, req.Nonce)
	if !crypto.VerifyEd25519(req.PublicKey, payload, req.Signature) {
		writeDeviceError(w, "SIGNATURE_INVALID", "Invalid registration signature", http.StatusUnauthorized)
		return
	}

	var bindingTaken bool
	_ = h.db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM devices WHERE hardware_binding_id = $1)`, req.HardwareBindingID).Scan(&bindingTaken)
	if bindingTaken {
		writeDeviceError(w, "DEVICE_ALREADY_BOUND", "Hardware already registered", http.StatusConflict)
		return
	}

	email := req.DeviceID + "@device.p1"
	var userID string
	var createdAt time.Time
	tx, err := h.db.Begin(ctx)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Transaction failed", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)

	err = tx.QueryRow(ctx,
		`INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id, created_at`,
		email, "device-auth",
	).Scan(&userID, &createdAt)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to create user", http.StatusInternalServerError)
		return
	}

	_, err = tx.Exec(ctx,
		`INSERT INTO devices (id, user_id, public_key, hardware_binding_id, hardware_platform)
		 VALUES ($1, $2, $3, $4, $5)`,
		req.DeviceID, userID, req.PublicKey, req.HardwareBindingID, req.HardwarePlatform,
	)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to create device", http.StatusInternalServerError)
		return
	}

	if err := tx.Commit(ctx); err != nil {
		writeDeviceError(w, "INTERNAL", "Commit failed", http.StatusInternalServerError)
		return
	}

	pair, err := h.issueDeviceTokens(ctx, userID)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to issue tokens", http.StatusInternalServerError)
		return
	}

	user, _ := h.userResponseForID(ctx, userID)
	writeJSON(w, http.StatusCreated, deviceAuthResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
		DeviceID:     req.DeviceID,
		User:         user,
		IsNew:        true,
	})
}

// DeviceVerify logs in an existing device via signed challenge.
func (h *Handler) DeviceVerify(w http.ResponseWriter, r *http.Request) {
	var req deviceVerifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeDeviceError(w, "INVALID_BODY", "Invalid JSON", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" || req.Nonce == "" || req.Signature == "" {
		writeDeviceError(w, "INVALID_BODY", "Missing required fields", http.StatusBadRequest)
		return
	}

	ctx := r.Context()
	var userID, publicKey string
	err := h.db.QueryRow(ctx,
		`SELECT user_id, public_key FROM devices WHERE id = $1`, req.DeviceID,
	).Scan(&userID, &publicKey)
	if err == pgx.ErrNoRows {
		writeDeviceError(w, "DEVICE_NOT_FOUND", "Unknown device", http.StatusNotFound)
		return
	}
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Lookup failed", http.StatusInternalServerError)
		return
	}

	if !h.consumeNonce(ctx, req.DeviceID, req.Nonce) {
		writeDeviceError(w, "NONCE_INVALID", "Invalid or expired nonce", http.StatusUnauthorized)
		return
	}

	payload := crypto.ChallengePayload(req.DeviceID, req.Nonce)
	if !crypto.VerifyEd25519(publicKey, payload, req.Signature) {
		writeDeviceError(w, "SIGNATURE_INVALID", "Invalid signature", http.StatusUnauthorized)
		return
	}

	pair, err := h.issueDeviceTokens(ctx, userID)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to issue tokens", http.StatusInternalServerError)
		return
	}

	user, _ := h.userResponseForID(ctx, userID)
	writeJSON(w, http.StatusOK, deviceAuthResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
		DeviceID:     req.DeviceID,
		User:         user,
	})
}

// DeviceRecover rotates Ed25519 public key for a known hardware binding.
func (h *Handler) DeviceRecover(w http.ResponseWriter, r *http.Request) {
	var req deviceRegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeDeviceError(w, "INVALID_BODY", "Invalid JSON", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" || req.PublicKey == "" || req.Nonce == "" || req.Signature == "" || req.HardwareBindingID == "" {
		writeDeviceError(w, "INVALID_BODY", "Missing required fields", http.StatusBadRequest)
		return
	}
	if !validTimestamp(req.Timestamp) {
		writeDeviceError(w, "TIMESTAMP_INVALID", "Clock skew too large", http.StatusBadRequest)
		return
	}

	ctx := r.Context()
	var userID string
	err := h.db.QueryRow(ctx,
		`SELECT user_id FROM devices WHERE hardware_binding_id = $1`, req.HardwareBindingID,
	).Scan(&userID)
	if err == pgx.ErrNoRows {
		writeDeviceError(w, "DEVICE_NOT_FOUND", "No account for this device", http.StatusNotFound)
		return
	}
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Lookup failed", http.StatusInternalServerError)
		return
	}

	if !h.consumeNonce(ctx, req.DeviceID, req.Nonce) {
		writeDeviceError(w, "NONCE_INVALID", "Invalid or expired nonce", http.StatusUnauthorized)
		return
	}

	payload := crypto.RecoverPayload(req.DeviceID, req.PublicKey, req.Timestamp, req.Nonce)
	if !crypto.VerifyEd25519(req.PublicKey, payload, req.Signature) {
		writeDeviceError(w, "SIGNATURE_INVALID", "Invalid recovery signature", http.StatusUnauthorized)
		return
	}

	_, err = h.db.Exec(ctx,
		`UPDATE devices SET id = $1, public_key = $2, updated_at = NOW() WHERE hardware_binding_id = $3`,
		req.DeviceID, req.PublicKey, req.HardwareBindingID,
	)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to update device", http.StatusInternalServerError)
		return
	}

	pair, err := h.issueDeviceTokens(ctx, userID)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to issue tokens", http.StatusInternalServerError)
		return
	}

	user, _ := h.userResponseForID(ctx, userID)
	writeJSON(w, http.StatusOK, deviceAuthResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
		DeviceID:     req.DeviceID,
		User:         user,
		Recovered:    true,
	})
}

// RefreshTokens rotates refresh token and issues a new access token.
func (h *Handler) RefreshTokens(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.RefreshToken == "" {
		writeDeviceError(w, "INVALID_BODY", "refreshToken required", http.StatusBadRequest)
		return
	}

	claims, err := h.auth.ParseRefreshToken(req.RefreshToken)
	if err != nil {
		writeDeviceError(w, "UNAUTHORIZED", "Invalid refresh token", http.StatusUnauthorized)
		return
	}

	ctx := r.Context()
	var storedHash string
	err = h.db.QueryRow(ctx,
		`SELECT refresh_token_hash FROM auth_sessions WHERE id = $1 AND user_id = $2 AND expires_at > NOW()`,
		claims.SessionID, claims.UserID,
	).Scan(&storedHash)
	if err != nil {
		writeDeviceError(w, "UNAUTHORIZED", "Session revoked", http.StatusUnauthorized)
		return
	}
	if storedHash != auth.HashRefreshToken(req.RefreshToken) {
		writeDeviceError(w, "UNAUTHORIZED", "Session revoked", http.StatusUnauthorized)
		return
	}

	_, _ = h.db.Exec(ctx, `DELETE FROM auth_sessions WHERE id = $1`, claims.SessionID)

	pair, err := h.issueDeviceTokens(ctx, claims.UserID)
	if err != nil {
		writeDeviceError(w, "INTERNAL", "Failed to issue tokens", http.StatusInternalServerError)
		return
	}

	writeJSON(w, http.StatusOK, refreshResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
	})
}
