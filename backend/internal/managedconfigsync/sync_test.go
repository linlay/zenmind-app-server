package managedconfigsync

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSyncGeneratesRuntimeRegistryAndComposeBlock(t *testing.T) {
	t.Parallel()

	repoRoot := t.TempDir()
	if err := os.MkdirAll(filepath.Join(repoRoot, "configs"), 0o755); err != nil {
		t.Fatalf("mkdir configs: %v", err)
	}

	registry := `files:
  - id: app-env
    name: App env
    type: env
    sourcePath: ./.env
    containerPath: /app/config/app.env
  - id: worker-yaml
    name: Worker application.yml
    type: application-yaml
    sourcePath: ../worker/application.yml
    containerPath: /app/config/worker.application.yml
`
	if err := os.WriteFile(filepath.Join(repoRoot, "configs", "config-files.yml"), []byte(registry), 0o644); err != nil {
		t.Fatalf("write registry: %v", err)
	}

	compose := `services:
  backend:
    env_file:
      - ./.env
    volumes:
      - type: bind
        source: ./data
        target: /data
      # BEGIN GENERATED CONFIG FILES VOLUMES
      # END GENERATED CONFIG FILES VOLUMES
`
	if err := os.WriteFile(filepath.Join(repoRoot, "compose.yml"), []byte(compose), 0o644); err != nil {
		t.Fatalf("write compose: %v", err)
	}

	if err := Sync(repoRoot); err != nil {
		t.Fatalf("sync: %v", err)
	}

	runtimeRaw, err := os.ReadFile(filepath.Join(repoRoot, "configs", "config-files.runtime.yml"))
	if err != nil {
		t.Fatalf("read runtime registry: %v", err)
	}
	runtimeText := string(runtimeRaw)
	if !strings.Contains(runtimeText, "hostPath: "+filepath.Join(repoRoot, ".env")) {
		t.Fatalf("runtime registry missing resolved app env host path: %s", runtimeText)
	}
	if !strings.Contains(runtimeText, "hostPath: "+filepath.Join(filepath.Dir(repoRoot), "worker", "application.yml")) {
		t.Fatalf("runtime registry missing resolved worker host path: %s", runtimeText)
	}

	composeRaw, err := os.ReadFile(filepath.Join(repoRoot, "compose.yml"))
	if err != nil {
		t.Fatalf("read updated compose: %v", err)
	}
	composeText := string(composeRaw)
	if !strings.Contains(composeText, "env_file:") || !strings.Contains(composeText, "- ./.env") {
		t.Fatalf("compose missing env_file: %s", composeText)
	}
	if !strings.Contains(composeText, "source: ./configs/config-files.runtime.yml") {
		t.Fatalf("compose missing runtime registry mount: %s", composeText)
	}
	if !strings.Contains(composeText, "source: ./.env") {
		t.Fatalf("compose missing app env mount: %s", composeText)
	}
	if !strings.Contains(composeText, "source: ../worker/application.yml") {
		t.Fatalf("compose missing worker mount: %s", composeText)
	}
}

func TestSyncRejectsDuplicateConfigIDs(t *testing.T) {
	t.Parallel()

	repoRoot := t.TempDir()
	if err := os.MkdirAll(filepath.Join(repoRoot, "configs"), 0o755); err != nil {
		t.Fatalf("mkdir configs: %v", err)
	}
	if err := os.WriteFile(filepath.Join(repoRoot, "configs", "config-files.yml"), []byte(`files:
  - id: dup
    name: One
    type: env
    sourcePath: ./.env
    containerPath: /app/config/a.env
  - id: dup
    name: Two
    type: env
    sourcePath: ../other/.env
    containerPath: /app/config/b.env
`), 0o644); err != nil {
		t.Fatalf("write registry: %v", err)
	}
	if err := os.WriteFile(filepath.Join(repoRoot, "compose.yml"), []byte(`services:
  backend:
    env_file:
      - ./.env
    volumes:
      # BEGIN GENERATED CONFIG FILES VOLUMES
      # END GENERATED CONFIG FILES VOLUMES
`), 0o644); err != nil {
		t.Fatalf("write compose: %v", err)
	}

	if err := Sync(repoRoot); err == nil || !strings.Contains(err.Error(), `duplicate config file id "dup"`) {
		t.Fatalf("expected duplicate id error, got: %v", err)
	}
}
