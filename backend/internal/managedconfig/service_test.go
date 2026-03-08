package managedconfig

import (
	"os"
	"path/filepath"
	"testing"
)

func TestServiceReadAndSaveAllowedFileByID(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	targetFile := filepath.Join(tempDir, "runtime.env")
	if err := os.WriteFile(targetFile, []byte("A=1\n"), 0o600); err != nil {
		t.Fatalf("write seed file: %v", err)
	}

	service, err := New([]AllowedFile{
		{
			ID:            "runtime-env",
			Name:          "Runtime env",
			Type:          "env",
			HostPath:      "/Users/test/Project/runtime.env",
			ContainerPath: "/app/config/runtime.env",
			Path:          "/app/config/runtime.env",
			ResolvedPath:  targetFile,
		},
	}, tempDir, 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	readResult, err := service.Read("runtime-env")
	if err != nil {
		t.Fatalf("read file: %v", err)
	}
	if readResult.Content != "A=1\n" {
		t.Fatalf("unexpected content: %q", readResult.Content)
	}
	if readResult.Name != "Runtime env" || readResult.HostPath != "/Users/test/Project/runtime.env" {
		t.Fatalf("unexpected metadata: %+v", readResult)
	}

	if err := service.Save("runtime-env", "A=2\n"); err != nil {
		t.Fatalf("save file: %v", err)
	}
	updated, err := os.ReadFile(targetFile)
	if err != nil {
		t.Fatalf("read updated file: %v", err)
	}
	if string(updated) != "A=2\n" {
		t.Fatalf("unexpected updated content: %q", string(updated))
	}
}

func TestServiceSupportsLegacyPathLookup(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	targetFile := filepath.Join(tempDir, "runtime.env")
	if err := os.WriteFile(targetFile, []byte("A=1\n"), 0o600); err != nil {
		t.Fatalf("write seed file: %v", err)
	}

	service, err := New([]AllowedFile{
		{
			ID:            "runtime-env",
			Name:          "Runtime env",
			Type:          "env",
			HostPath:      "/Users/test/Project/runtime.env",
			ContainerPath: "/app/config/runtime.env",
			Path:          "./runtime.env",
			ResolvedPath:  targetFile,
		},
	}, tempDir, 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	readResult, err := service.Read("./runtime.env")
	if err != nil {
		t.Fatalf("read file by path: %v", err)
	}
	if readResult.ID != "runtime-env" {
		t.Fatalf("unexpected result for legacy path lookup: %+v", readResult)
	}
}

func TestServiceRejectsPathOutsideAllowlist(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	allowedFile := filepath.Join(tempDir, "allowed.env")
	if err := os.WriteFile(allowedFile, []byte("A=1\n"), 0o600); err != nil {
		t.Fatalf("write allowed file: %v", err)
	}

	service, err := New([]AllowedFile{
		{ID: "allowed", Path: "./allowed.env", ResolvedPath: allowedFile},
	}, tempDir, 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	_, err = service.Read("./blocked.env")
	if !IsCode(err, CodeNotAllowed) {
		t.Fatalf("expected CodeNotAllowed, got: %v", err)
	}
}

func TestServiceRejectsMissingFile(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	missingFile := filepath.Join(tempDir, "missing.env")

	service, err := New([]AllowedFile{
		{ID: "missing", Path: "./missing.env", ResolvedPath: missingFile},
	}, tempDir, 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	if err := service.Save("missing", "A=1\n"); !IsCode(err, CodeNotFound) {
		t.Fatalf("expected CodeNotFound from save, got: %v", err)
	}
}
