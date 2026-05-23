package programcli

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"zenmind-app-server/backend/internal/db"
)

func TestSetupPublicKeyAndIssueTokens(t *testing.T) {
	root := t.TempDir()
	dbPath := filepath.Join(root, "auth.db")
	keyDir := filepath.Join(root, "keys")
	publicOut := filepath.Join(keyDir, "publicKey.pem")

	var stdout, stderr bytes.Buffer
	handled, err := Run([]string{
		"setup-public-key",
		"--db", dbPath,
		"--out", keyDir,
		"--public-out", publicOut,
		"--key-id", "test-kid-1",
	}, &stdout, &stderr)
	if err != nil {
		t.Fatalf("setup-public-key failed: %v stderr=%s", err, stderr.String())
	}
	if !handled {
		t.Fatal("setup-public-key was not handled")
	}
	if !strings.Contains(stdout.String(), "kid=test-kid-1") {
		t.Fatalf("unexpected setup output: %s", stdout.String())
	}
	for _, name := range []string{"jwk-public.pem", "jwk-private.pem", "publicKey.pem"} {
		if !fileExists(filepath.Join(keyDir, name)) {
			t.Fatalf("expected key file %s", name)
		}
	}

	stdout.Reset()
	stderr.Reset()
	_, err = Run([]string{
		"setup-public-key",
		"--db", dbPath,
		"--out", keyDir,
		"--key-id", "ignored-kid",
	}, &stdout, &stderr)
	if err != nil {
		t.Fatalf("repeat setup-public-key failed: %v stderr=%s", err, stderr.String())
	}
	if !strings.Contains(stdout.String(), "exported existing key pair (kid=test-kid-1)") {
		t.Fatalf("expected existing key export, got: %s", stdout.String())
	}

	stdout.Reset()
	stderr.Reset()
	_, err = Run([]string{
		"issue-bridge-access-token",
		"--db", dbPath,
		"--issuer", "http://localhost:18080",
		"--username", "app",
		"--device-name", "WeChat Bridge",
	}, &stdout, &stderr)
	if err != nil {
		t.Fatalf("issue access token failed: %v stderr=%s", err, stderr.String())
	}
	if strings.Count(strings.TrimSpace(stdout.String()), ".") != 2 {
		t.Fatalf("expected JWT output, got: %s", stdout.String())
	}

	stdout.Reset()
	stderr.Reset()
	_, err = Run([]string{
		"issue-bridge-runner-token",
		"--db", dbPath,
		"--issuer", "http://localhost:18080",
		"--username", "app",
		"--device-name", "WeChat Bridge",
		"--ttl-seconds", "600",
	}, &stdout, &stderr)
	if err != nil {
		t.Fatalf("issue runner token failed: %v stderr=%s", err, stderr.String())
	}
	if !strings.Contains(stdout.String(), "RUNNER_BEARER_TOKEN=") || !strings.Contains(stdout.String(), "RUNNER_BEARER_EXPIRES_AT=") {
		t.Fatalf("unexpected runner output: %s", stdout.String())
	}

	conn, err := db.Open(dbPath)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	var keyCount, deviceCount, auditCount int
	if err := conn.QueryRow(`SELECT (SELECT COUNT(*) FROM JWK_KEY_), (SELECT COUNT(*) FROM DEVICE_), (SELECT COUNT(*) FROM TOKEN_AUDIT_)`).Scan(&keyCount, &deviceCount, &auditCount); err != nil {
		t.Fatal(err)
	}
	if keyCount != 1 || deviceCount != 1 || auditCount != 2 {
		t.Fatalf("unexpected db counts: keys=%d devices=%d audits=%d", keyCount, deviceCount, auditCount)
	}
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}
