package security

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"fmt"
	"math/big"
	"strings"
	"sync"
	"time"

	"github.com/go-jose/go-jose/v4"
	"github.com/go-jose/go-jose/v4/jwt"
	"github.com/google/uuid"
)

type KeyManager struct {
	db  *sql.DB
	mu  sync.Mutex
	key *jose.JSONWebKey
	kid string
}

func NewKeyManager(db *sql.DB) *KeyManager {
	return &KeyManager{db: db}
}

func (m *KeyManager) LoadOrCreate() (*jose.JSONWebKey, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.key != nil {
		k := *m.key
		return &k, nil
	}
	key, err := m.loadFirstKey()
	if err != nil {
		return nil, err
	}
	if key != nil {
		m.key = key
		m.kid = key.KeyID
		k := *key
		return &k, nil
	}
	generated, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, err
	}
	kid := uuid.NewString()
	pubDER, err := x509.MarshalPKIXPublicKey(&generated.PublicKey)
	if err != nil {
		return nil, err
	}
	privDER, err := x509.MarshalPKCS8PrivateKey(generated)
	if err != nil {
		return nil, err
	}
	_, err = m.db.Exec(
		`INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES (?, ?, ?, ?)`,
		kid,
		base64.StdEncoding.EncodeToString(pubDER),
		base64.StdEncoding.EncodeToString(privDER),
		time.Now().UTC(),
	)
	if err != nil {
		return nil, err
	}
	jwk := &jose.JSONWebKey{Key: generated, KeyID: kid, Algorithm: string(jose.RS256), Use: "sig"}
	m.key = jwk
	m.kid = kid
	copyKey := *jwk
	return &copyKey, nil
}

func (m *KeyManager) loadFirstKey() (*jose.JSONWebKey, error) {
	row := m.db.QueryRow(`SELECT KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1`)
	var kid, pubB64, privB64 string
	if err := row.Scan(&kid, &pubB64, &privB64); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	privDER, err := base64.StdEncoding.DecodeString(privB64)
	if err != nil {
		return nil, err
	}
	parsed, err := x509.ParsePKCS8PrivateKey(privDER)
	if err != nil {
		return nil, err
	}
	priv, ok := parsed.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("stored private key is not RSA")
	}
	return &jose.JSONWebKey{Key: priv, KeyID: kid, Algorithm: string(jose.RS256), Use: "sig"}, nil
}

func (m *KeyManager) PublicJWKSet() (map[string]any, error) {
	key, err := m.LoadOrCreate()
	if err != nil {
		return nil, err
	}
	pub := key.Public()
	set := jose.JSONWebKeySet{Keys: []jose.JSONWebKey{pub}}
	return map[string]any{"keys": set.Keys}, nil
}

func (m *KeyManager) PublicKeyPEMFromJWK(e, n string) (string, error) {
	if strings.TrimSpace(e) == "" || strings.TrimSpace(n) == "" {
		return "", fmt.Errorf("invalid jwk parameters")
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(e)
	if err != nil {
		return "", fmt.Errorf("invalid jwk parameters")
	}
	nBytes, err := base64.RawURLEncoding.DecodeString(n)
	if err != nil {
		return "", fmt.Errorf("invalid jwk parameters")
	}
	exponent := new(big.Int).SetBytes(eBytes)
	modulus := new(big.Int).SetBytes(nBytes)
	if exponent.Sign() <= 0 || modulus.Sign() <= 0 {
		return "", fmt.Errorf("invalid jwk parameters")
	}
	pub := &rsa.PublicKey{N: modulus, E: int(exponent.Int64())}
	der, err := x509.MarshalPKIXPublicKey(pub)
	if err != nil {
		return "", fmt.Errorf("failed to generate public key")
	}
	block := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der})
	return strings.TrimSpace(string(block)), nil
}

func GenerateEphemeralRSAKeyPair() (publicPEM, privatePEM string, err error) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return "", "", err
	}
	pubDER, err := x509.MarshalPKIXPublicKey(&priv.PublicKey)
	if err != nil {
		return "", "", err
	}
	privDER, err := x509.MarshalPKCS8PrivateKey(priv)
	if err != nil {
		return "", "", err
	}
	pubBlock := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: pubDER})
	privBlock := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privDER})
	return strings.TrimSpace(string(pubBlock)), strings.TrimSpace(string(privBlock)), nil
}

func SHA256Hex(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func SignJWT(key *jose.JSONWebKey, claims map[string]any) (string, error) {
	opts := (&jose.SignerOptions{}).WithType("JWT").WithHeader("kid", key.KeyID)
	signer, err := jose.NewSigner(jose.SigningKey{Algorithm: jose.RS256, Key: key.Key}, opts)
	if err != nil {
		return "", err
	}
	return jwt.Signed(signer).Claims(claims).Serialize()
}

func ParseAndVerifyJWT(token string, key *jose.JSONWebKey, out any) error {
	parsed, err := jwt.ParseSigned(token, []jose.SignatureAlgorithm{jose.RS256})
	if err != nil {
		return err
	}
	return parsed.Claims(key.Public(), out)
}
