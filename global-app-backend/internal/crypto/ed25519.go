package crypto

import (
	"crypto/ed25519"
	"encoding/base64"
	"strconv"
	"strings"
)

func decodeB64URL(s string) ([]byte, error) {
	padded := s + strings.Repeat("=", (4-len(s)%4)%4)
	padded = strings.ReplaceAll(padded, "-", "+")
	padded = strings.ReplaceAll(padded, "_", "/")
	return base64.StdEncoding.DecodeString(padded)
}

// VerifyEd25519 checks a base64url-encoded signature over message.
func VerifyEd25519(publicKeyB64URL, message, signatureB64URL string) bool {
	pubBytes, err := decodeB64URL(publicKeyB64URL)
	if err != nil || len(pubBytes) != ed25519.PublicKeySize {
		return false
	}
	sigBytes, err := decodeB64URL(signatureB64URL)
	if err != nil || len(sigBytes) != ed25519.SignatureSize {
		return false
	}
	return ed25519.Verify(ed25519.PublicKey(pubBytes), []byte(message), sigBytes)
}

func ChallengePayload(deviceID, nonce string) string {
	return "challenge:" + deviceID + ":" + nonce
}

func RegisterPayload(deviceID, publicKey string, timestamp int64, nonce string) string {
	return "register:" + deviceID + ":" + publicKey + ":" + strconv.FormatInt(timestamp, 10) + ":" + nonce
}

func RecoverPayload(deviceID, publicKey string, timestamp int64, nonce string) string {
	return "recover:" + deviceID + ":" + publicKey + ":" + strconv.FormatInt(timestamp, 10) + ":" + nonce
}
