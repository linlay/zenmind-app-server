package config

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
	"gopkg.in/yaml.v3"
)

var bcryptPattern = regexp.MustCompile(`^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$`)

type Config struct {
	ServerPort int
	DBPath     string
	Issuer     string

	ApplicationYAMLPath   string
	ExternalEditableFiles []EditableFile

	AdminUsername       string
	AdminPasswordBcrypt string

	AppUsername             string
	AppMasterPasswordBcrypt string
	AppAccessTTL            time.Duration
	AppMaxAccessTTL         time.Duration
	AppRotateDeviceToken    bool

	TokenAccessTTL     time.Duration
	TokenRefreshTTL    time.Duration
	TokenRotateRefresh bool

	CleanupRetention time.Duration
	CleanupCron      string
}

type EditableFile struct {
	Path         string
	ResolvedPath string
}

func Load() (*Config, error) {
	_ = godotenv.Load("../.env", ".env")
	applicationYAMLPath, editableFiles, err := loadEditableFiles(env("AUTH_APPLICATION_YML_PATH", "application.yml"))
	if err != nil {
		return nil, err
	}

	port := envInt("SERVER_PORT", envInt("BACKEND_PORT", 8080))
	cfg := &Config{
		ServerPort:              port,
		DBPath:                  env("AUTH_DB_PATH", "../data/auth.db"),
		Issuer:                  env("AUTH_ISSUER", "http://localhost:8080"),
		ApplicationYAMLPath:     applicationYAMLPath,
		ExternalEditableFiles:   editableFiles,
		AdminUsername:           env("AUTH_ADMIN_USERNAME", "admin"),
		AdminPasswordBcrypt:     normalizeQuotedValue(env("AUTH_ADMIN_PASSWORD_BCRYPT", "")),
		AppUsername:             env("AUTH_APP_USERNAME", "app"),
		AppMasterPasswordBcrypt: normalizeQuotedValue(env("AUTH_APP_MASTER_PASSWORD_BCRYPT", "")),
		AppRotateDeviceToken:    envBool("AUTH_APP_ROTATE_DEVICE_TOKEN", true),
		TokenRotateRefresh:      envBool("AUTH_TOKEN_ROTATE_REFRESH_TOKEN", true),
		CleanupCron:             env("AUTH_CLEANUP_CRON", "0 0 * * * *"),
	}
	cfg.AppAccessTTL, err = parseFlexibleDuration(env("AUTH_APP_ACCESS_TTL", "PT10M"))
	if err != nil {
		return nil, fmt.Errorf("invalid AUTH_APP_ACCESS_TTL: %w", err)
	}
	cfg.AppMaxAccessTTL, err = parseFlexibleDuration(env("AUTH_APP_MAX_ACCESS_TTL", "P30D"))
	if err != nil {
		return nil, fmt.Errorf("invalid AUTH_APP_MAX_ACCESS_TTL: %w", err)
	}
	cfg.TokenAccessTTL, err = parseFlexibleDuration(env("AUTH_TOKEN_ACCESS_TTL", "PT15M"))
	if err != nil {
		return nil, fmt.Errorf("invalid AUTH_TOKEN_ACCESS_TTL: %w", err)
	}
	cfg.TokenRefreshTTL, err = parseFlexibleDuration(env("AUTH_TOKEN_REFRESH_TTL", "P30D"))
	if err != nil {
		return nil, fmt.Errorf("invalid AUTH_TOKEN_REFRESH_TTL: %w", err)
	}
	cfg.CleanupRetention, err = parseFlexibleDuration(env("AUTH_CLEANUP_RETENTION", "PT24H"))
	if err != nil {
		return nil, fmt.Errorf("invalid AUTH_CLEANUP_RETENTION: %w", err)
	}

	if err := validate(cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}

func loadEditableFiles(applicationYAMLPath string) (string, []EditableFile, error) {
	absYAMLPath, err := filepath.Abs(strings.TrimSpace(applicationYAMLPath))
	if err != nil {
		return "", nil, fmt.Errorf("resolve application.yml path failed: %w", err)
	}
	baseDir := filepath.Dir(absYAMLPath)
	if envOverride := env("AUTH_EXTERNAL_EDITABLE_FILES", ""); envOverride != "" {
		out, err := resolveEditableFiles(baseDir, strings.Split(envOverride, ","))
		if err != nil {
			return "", nil, err
		}
		return absYAMLPath, out, nil
	}

	raw, err := os.ReadFile(absYAMLPath)
	if err != nil {
		return "", nil, fmt.Errorf("read application.yml failed: %w", err)
	}
	var parsed struct {
		External struct {
			EditableFiles []string `yaml:"editable-files"`
		} `yaml:"external"`
	}
	if err := yaml.Unmarshal(raw, &parsed); err != nil {
		return "", nil, fmt.Errorf("parse application.yml failed: %w", err)
	}
	out, err := resolveEditableFiles(baseDir, parsed.External.EditableFiles)
	if err != nil {
		return "", nil, err
	}
	return absYAMLPath, out, nil
}

func resolveEditableFiles(baseDir string, values []string) ([]EditableFile, error) {
	out := make([]EditableFile, 0, len(values))
	seen := make(map[string]struct{}, len(values))
	for _, value := range values {
		configuredPath := strings.TrimSpace(value)
		if configuredPath == "" {
			continue
		}
		resolvedPath := configuredPath
		if !filepath.IsAbs(resolvedPath) {
			resolvedPath = filepath.Join(baseDir, resolvedPath)
		}
		resolvedPath = filepath.Clean(resolvedPath)
		absResolvedPath, err := filepath.Abs(resolvedPath)
		if err != nil {
			return nil, fmt.Errorf("resolve editable file path failed: %w", err)
		}
		if _, ok := seen[absResolvedPath]; ok {
			continue
		}
		seen[absResolvedPath] = struct{}{}
		out = append(out, EditableFile{
			Path:         configuredPath,
			ResolvedPath: absResolvedPath,
		})
	}
	return out, nil
}

func validate(cfg *Config) error {
	if !bcryptPattern.MatchString(cfg.AdminPasswordBcrypt) {
		return fmt.Errorf("AUTH_ADMIN_PASSWORD_BCRYPT must be a valid bcrypt hash")
	}
	if !bcryptPattern.MatchString(cfg.AppMasterPasswordBcrypt) {
		return fmt.Errorf("AUTH_APP_MASTER_PASSWORD_BCRYPT must be a valid bcrypt hash")
	}
	if cfg.AppMaxAccessTTL <= 0 {
		return fmt.Errorf("AUTH_APP_MAX_ACCESS_TTL must be positive")
	}
	if cfg.AppAccessTTL <= 0 {
		return fmt.Errorf("AUTH_APP_ACCESS_TTL must be positive")
	}
	if cfg.TokenAccessTTL <= 0 || cfg.TokenRefreshTTL <= 0 {
		return fmt.Errorf("token TTL must be positive")
	}
	return nil
}

func env(k, fallback string) string {
	v := strings.TrimSpace(os.Getenv(k))
	if v == "" {
		return fallback
	}
	return v
}

func envInt(k string, fallback int) int {
	v := strings.TrimSpace(os.Getenv(k))
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func envBool(k string, fallback bool) bool {
	v := strings.TrimSpace(strings.ToLower(os.Getenv(k)))
	if v == "" {
		return fallback
	}
	b, err := strconv.ParseBool(v)
	if err != nil {
		return fallback
	}
	return b
}

func normalizeQuotedValue(value string) string {
	trimmed := strings.TrimSpace(value)
	if len(trimmed) >= 2 {
		first := trimmed[0]
		last := trimmed[len(trimmed)-1]
		if (first == '\'' && last == '\'') || (first == '"' && last == '"') {
			return trimmed[1 : len(trimmed)-1]
		}
	}
	return trimmed
}

func parseFlexibleDuration(raw string) (time.Duration, error) {
	v := strings.TrimSpace(strings.ToUpper(raw))
	if v == "" {
		return 0, fmt.Errorf("duration is empty")
	}
	if strings.HasPrefix(v, "P") {
		if strings.HasPrefix(v, "PT") {
			normalized := strings.TrimPrefix(v, "PT")
			normalized = strings.ReplaceAll(normalized, "D", "24H")
			return time.ParseDuration(strings.ToLower(normalized))
		}
		if strings.HasSuffix(v, "D") {
			n, err := strconv.Atoi(strings.TrimSuffix(strings.TrimPrefix(v, "P"), "D"))
			if err != nil {
				return 0, err
			}
			return time.Duration(n) * 24 * time.Hour, nil
		}
	}
	if d, err := time.ParseDuration(strings.ToLower(v)); err == nil {
		return d, nil
	}
	return 0, fmt.Errorf("unsupported duration format: %s", raw)
}
