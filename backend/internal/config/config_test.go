package config

import (
	"os"
	"path/filepath"
	"testing"
)

const validBcrypt = "$2a$10$XkbhEhxiT/4JC.zkZD9uxuEFf7za4AxEBPbIFmUR41qY0.spPk41q"

func TestLoadReadsEditableFilesFromApplicationYAML(t *testing.T) {
	tempDir := t.TempDir()
	appYAMLPath := filepath.Join(tempDir, "application.yml")
	yamlContent := `external:
  editable-files:
    - ./runtime.env
    - ./runtime.env
    - ../shared/common.env
`
	if err := os.WriteFile(appYAMLPath, []byte(yamlContent), 0o600); err != nil {
		t.Fatalf("write application.yml: %v", err)
	}

	t.Setenv("AUTH_APPLICATION_YML_PATH", appYAMLPath)
	t.Setenv("AUTH_ADMIN_PASSWORD_BCRYPT", validBcrypt)
	t.Setenv("AUTH_APP_MASTER_PASSWORD_BCRYPT", validBcrypt)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("load config: %v", err)
	}

	if cfg.ApplicationYAMLPath != appYAMLPath {
		t.Fatalf("unexpected application.yml path: %s", cfg.ApplicationYAMLPath)
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
	if cfg.ExternalEditableFiles[1].Path != "../shared/common.env" || cfg.ExternalEditableFiles[1].ResolvedPath != expectedSecond {
		t.Fatalf("unexpected second editable file: %+v", cfg.ExternalEditableFiles[1])
	}
}
