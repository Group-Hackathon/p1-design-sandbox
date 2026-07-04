package auth

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	defaultAccessTTL  = 15 * time.Minute
	defaultRefreshTTL = 30 * 24 * time.Hour
)

type TokenPair struct {
	AccessToken  string
	RefreshToken string
	SessionID    string
}

// IssueTokenPair creates short-lived access + refresh tokens (RankMyAura-style).
func (s *Service) IssueTokenPair(userID string, sessionID string) (TokenPair, error) {
	accessClaims := jwt.MapClaims{
		"sub": userID,
		"typ": "access",
		"iat": time.Now().Unix(),
		"exp": time.Now().Add(defaultAccessTTL).Unix(),
	}
	accessToken := jwt.NewWithClaims(jwt.SigningMethodHS256, accessClaims)
	accessSigned, err := accessToken.SignedString(s.secret)
	if err != nil {
		return TokenPair{}, err
	}

	refreshClaims := jwt.MapClaims{
		"sub": userID,
		"typ": "refresh",
		"sid": sessionID,
		"iat": time.Now().Unix(),
		"exp": time.Now().Add(defaultRefreshTTL).Unix(),
	}
	refreshToken := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims)
	refreshSigned, err := refreshToken.SignedString(s.secret)
	if err != nil {
		return TokenPair{}, err
	}

	return TokenPair{
		AccessToken:  accessSigned,
		RefreshToken: refreshSigned,
		SessionID:    sessionID,
	}, nil
}

type RefreshClaims struct {
	UserID    string
	SessionID string
}

// ParseRefreshToken validates a refresh JWT and returns user + session ids.
func (s *Service) ParseRefreshToken(tokenString string) (*RefreshClaims, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return s.secret, nil
	})
	if err != nil || !token.Valid {
		return nil, fmt.Errorf("invalid refresh token")
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, fmt.Errorf("invalid claims")
	}
	if typ, _ := claims["typ"].(string); typ != "refresh" {
		return nil, fmt.Errorf("not a refresh token")
	}
	userID, _ := claims["sub"].(string)
	sessionID, _ := claims["sid"].(string)
	if userID == "" || sessionID == "" {
		return nil, fmt.Errorf("missing sub or sid")
	}
	return &RefreshClaims{UserID: userID, SessionID: sessionID}, nil
}

// HashRefreshToken stores a one-way hash of refresh tokens in Postgres.
func HashRefreshToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
