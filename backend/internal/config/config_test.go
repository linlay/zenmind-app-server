package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const documentedDevBcrypt = "$2a$10$R9SBw8NUY53nl9mg4L206eM0gFmQFqxSIg5ieLKILAiNbbc2ZSVbu"

func TestLoadUsesBuiltInDefaults(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("SERVER_PORT", "")
	t.Setenv("BACKEND_PORT", "")
	t.Setenv("AUTH_DB_PATH", "")
	t.Setenv("AUTH_ISSUER", "")
	t.Setenv("AUTH_ADMIN_USERNAME", "")
	t.Setenv("AUTH_APP_USERNAME", "")
	t.Setenv("AUTH_APP_ACCESS_TTL", "")
	t.Setenv("AUTH_APP_MAX_ACCESS_TTL", "")
	t.Setenv("AUTH_APP_ROTATE_DEVICE_TOKEN", "")
	t.Setenv("AUTH_TOKEN_ACCESS_TTL", "")
	t.Setenv("AUTH_TOKEN_REFRESH_TTL", "")
	t.Setenv("AUTH_TOKEN_ROTATE_REFRESH_TOKEN", "")
	t.Setenv("AUTH_CLEANUP_RETENTION", "")
	t.Setenv("AUTH_CLEANUP_CRON", "")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}

	if cfg.ServerPort != builtInDefaults.ServerPort {
		t.Fatalf("unexpected server port: %d", cfg.ServerPort)
	}
	if cfg.DBPath != builtInDefaults.DBPath || cfg.Issuer != builtInDefaults.Issuer {
		t.Fatalf("unexpected defaults: db=%s issuer=%s", cfg.DBPath, cfg.Issuer)
	}
	if cfg.AdminUsername != builtInDefaults.AdminUsername || cfg.AppUsername != builtInDefaults.AppUsername {
		t.Fatalf("unexpected usernames: admin=%s app=%s", cfg.AdminUsername, cfg.AppUsername)
	}
	if cfg.AppAccessTTL != 10*time.Minute || cfg.TokenAccessTTL != 15*time.Minute {
		t.Fatalf("unexpected access TTLs: app=%s token=%s", cfg.AppAccessTTL, cfg.TokenAccessTTL)
	}
	if cfg.AppMaxAccessTTL != 30*24*time.Hour || cfg.TokenRefreshTTL != 30*24*time.Hour {
		t.Fatalf("unexpected max/refresh TTLs: appMax=%s tokenRefresh=%s", cfg.AppMaxAccessTTL, cfg.TokenRefreshTTL)
	}
	if !cfg.AppRotateDeviceToken || !cfg.TokenRotateRefresh {
		t.Fatalf("expected rotate flags true: app=%v token=%v", cfg.AppRotateDeviceToken, cfg.TokenRotateRefresh)
	}
	if cfg.CleanupRetention != 24*time.Hour || cfg.CleanupCron != builtInDefaults.CleanupCron {
		t.Fatalf("unexpected cleanup defaults: retention=%s cron=%s", cfg.CleanupRetention, cfg.CleanupCron)
	}
	if cfg.EditableFilesBaseDir != tempDir {
		t.Fatalf("unexpected base dir: %s", cfg.EditableFilesBaseDir)
	}
	if len(cfg.ExternalEditableFiles) != 0 {
		t.Fatalf("expected no editable files by default, got: %d", len(cfg.ExternalEditableFiles))
	}
}

func TestLoadEnvOverridesBuiltInDefaults(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("SERVER_PORT", "28080")
	t.Setenv("BACKEND_PORT", "")
	t.Setenv("AUTH_DB_PATH", "/tmp/auth.db")
	t.Setenv("AUTH_ISSUER", "http://env.example:28080")
	t.Setenv("AUTH_ADMIN_USERNAME", "env-admin")
	t.Setenv("AUTH_APP_USERNAME", "env-app")
	t.Setenv("AUTH_APP_ACCESS_TTL", "PT5M")
	t.Setenv("AUTH_APP_MAX_ACCESS_TTL", "P2D")
	t.Setenv("AUTH_APP_ROTATE_DEVICE_TOKEN", "false")
	t.Setenv("AUTH_TOKEN_ACCESS_TTL", "PT8M")
	t.Setenv("AUTH_TOKEN_REFRESH_TTL", "P5D")
	t.Setenv("AUTH_TOKEN_ROTATE_REFRESH_TOKEN", "false")
	t.Setenv("AUTH_CLEANUP_RETENTION", "PT6H")
	t.Setenv("AUTH_CLEANUP_CRON", "0 */10 * * * *")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}

	if cfg.ServerPort != 28080 || cfg.DBPath != "/tmp/auth.db" || cfg.Issuer != "http://env.example:28080" {
		t.Fatalf("env overrides not applied: port=%d db=%s issuer=%s", cfg.ServerPort, cfg.DBPath, cfg.Issuer)
	}
	if cfg.AdminUsername != "env-admin" || cfg.AppUsername != "env-app" {
		t.Fatalf("unexpected usernames: admin=%s app=%s", cfg.AdminUsername, cfg.AppUsername)
	}
	if cfg.AppAccessTTL != 5*time.Minute || cfg.TokenAccessTTL != 8*time.Minute {
		t.Fatalf("unexpected access TTLs: app=%s token=%s", cfg.AppAccessTTL, cfg.TokenAccessTTL)
	}
	if cfg.AppMaxAccessTTL != 2*24*time.Hour || cfg.TokenRefreshTTL != 5*24*time.Hour {
		t.Fatalf("unexpected max/refresh TTLs: appMax=%s tokenRefresh=%s", cfg.AppMaxAccessTTL, cfg.TokenRefreshTTL)
	}
	if cfg.AppRotateDeviceToken || cfg.TokenRotateRefresh {
		t.Fatalf("expected rotate flags false: app=%v token=%v", cfg.AppRotateDeviceToken, cfg.TokenRotateRefresh)
	}
	if cfg.CleanupRetention != 6*time.Hour || cfg.CleanupCron != "0 */10 * * * *" {
		t.Fatalf("unexpected cleanup overrides: retention=%s cron=%s", cfg.CleanupRetention, cfg.CleanupCron)
	}
}

func TestLoadIgnoresBackendPortForServerPort(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("SERVER_PORT", "")
	t.Setenv("BACKEND_PORT", "11952")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}

	if cfg.ServerPort != builtInDefaults.ServerPort {
		t.Fatalf("expected backend port env to be ignored, got: %d", cfg.ServerPort)
	}
}

func TestLoadReadsEditableFilesFromRuntimeRegistry(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	registryPath := filepath.Join(tempDir, "config-files.runtime.yml")
	runtimeRegistry := `files:
  - id: mcp-env
    name: MCP Server Mock env
    type: env
    hostPath: /Users/test/Project/mcp-server-mock/.env
    containerPath: /app/config/mcp-server-mock.env
  - id: webclient-yaml
    name: Term Webclient application.yml
    type: application-yaml
    hostPath: /Users/test/Project/term-webclient/release/application.yml
    containerPath: /app/config/term-webclient-release.application.yml
`
	if err := os.WriteFile(registryPath, []byte(runtimeRegistry), 0o600); err != nil {
		t.Fatalf("write runtime registry: %v", err)
	}

	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", registryPath)
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if len(cfg.ExternalEditableFiles) != 2 {
		t.Fatalf("expected 2 editable files, got: %d", len(cfg.ExternalEditableFiles))
	}

	first := cfg.ExternalEditableFiles[0]
	if first.ID != "mcp-env" || first.Name != "MCP Server Mock env" || first.Type != "env" {
		t.Fatalf("unexpected first runtime file metadata: %+v", first)
	}
	if first.HostPath != "/Users/test/Project/mcp-server-mock/.env" {
		t.Fatalf("unexpected first host path: %s", first.HostPath)
	}
	if first.Path != "/app/config/mcp-server-mock.env" || first.ContainerPath != "/app/config/mcp-server-mock.env" {
		t.Fatalf("unexpected first container path fields: %+v", first)
	}
	if first.ResolvedPath != "/app/config/mcp-server-mock.env" {
		t.Fatalf("unexpected first resolved path: %s", first.ResolvedPath)
	}
}

func TestLoadFallsBackToLegacyEditableFiles(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "./runtime.env,./runtime.env,../shared/common.env")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if len(cfg.ExternalEditableFiles) != 2 {
		t.Fatalf("expected 2 editable files, got: %d", len(cfg.ExternalEditableFiles))
	}

	expectedFirst, err := filepath.Abs(filepath.Join(tempDir, "runtime.env"))
	if err != nil {
		t.Fatalf("resolve expected first path: %v", err)
	}
	expectedSecond, err := filepath.Abs(filepath.Join(tempDir, "../shared/common.env"))
	if err != nil {
		t.Fatalf("resolve expected second path: %v", err)
	}

	if cfg.ExternalEditableFiles[0].Path != "./runtime.env" || cfg.ExternalEditableFiles[0].ResolvedPath != expectedFirst {
		t.Fatalf("unexpected first editable file: %+v", cfg.ExternalEditableFiles[0])
	}
	if cfg.ExternalEditableFiles[0].HostPath != expectedFirst || cfg.ExternalEditableFiles[0].ID != "legacy-1" {
		t.Fatalf("unexpected first legacy metadata: %+v", cfg.ExternalEditableFiles[0])
	}
	if cfg.ExternalEditableFiles[1].Path != "../shared/common.env" || cfg.ExternalEditableFiles[1].ResolvedPath != expectedSecond {
		t.Fatalf("unexpected second editable file: %+v", cfg.ExternalEditableFiles[1])
	}
}

func TestLoadRejectsInvalidRuntimeRegistry(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	registryPath := filepath.Join(tempDir, "config-files.runtime.yml")
	invalidRuntimeRegistry := `files:
  - id: duplicate
    name: One
    type: env
    hostPath: /a.env
    containerPath: /app/config/a.env
  - id: duplicate
    name: Two
    type: env
    hostPath: /b.env
    containerPath: /app/config/b.env
`
	if err := os.WriteFile(registryPath, []byte(invalidRuntimeRegistry), 0o600); err != nil {
		t.Fatalf("write runtime registry: %v", err)
	}

	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", registryPath)
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	_, err := Load()
	if err == nil || !strings.Contains(err.Error(), `duplicate runtime config file id "duplicate"`) {
		t.Fatalf("expected duplicate runtime id error, got: %v", err)
	}
}

func TestLoadUsesDefaultRuntimeRegistryPathWhenPresent(t *testing.T) {
	tempDir := t.TempDir()
	t.Chdir(tempDir)
	registryPath := filepath.Join(tempDir, "config-files.runtime.yml")
	runtimeRegistry := `files:
  - id: runtime-registry-file
    name: Runtime registry file
    type: env
    hostPath: /Users/test/Project/runtime.env
    containerPath: /app/config/runtime.env
`
	if err := os.WriteFile(registryPath, []byte(runtimeRegistry), 0o600); err != nil {
		t.Fatalf("write runtime registry: %v", err)
	}

	originalDefaultRegistryPath := builtInDefaults.DefaultRuntimeRegistryPath
	builtInDefaults.DefaultRuntimeRegistryPath = registryPath
	defer func() { builtInDefaults.DefaultRuntimeRegistryPath = originalDefaultRegistryPath }()

	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if len(cfg.ExternalEditableFiles) != 1 || cfg.ExternalEditableFiles[0].ID != "runtime-registry-file" {
		t.Fatalf("expected default runtime registry to be used, got: %+v", cfg.ExternalEditableFiles)
	}
}

func TestDocumentedDevBcryptPassesValidation(t *testing.T) {
	if !bcryptPattern.MatchString(documentedDevBcrypt) {
		t.Fatalf("documented dev bcrypt is invalid: %s", documentedDevBcrypt)
	}

	tempDir := t.TempDir()
	t.Chdir(tempDir)
	t.Setenv("AUTH_CONFIG_FILES_REGISTRY_PATH", "")
	t.Setenv("AUTH_EXTERNAL_EDITABLE_FILES", "")
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", documentedDevBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", documentedDevBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.AdminPasswordBcrypt != documentedDevBcrypt {
		t.Fatalf("unexpected admin bcrypt: %s", cfg.AdminPasswordBcrypt)
	}
	if cfg.AppMasterPasswordBcrypt != documentedDevBcrypt {
		t.Fatalf("unexpected app master bcrypt: %s", cfg.AppMasterPasswordBcrypt)
	}
}
