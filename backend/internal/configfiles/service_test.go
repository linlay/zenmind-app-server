package configfiles

import (
	"os"
	"path/filepath"
	"testing"
)

func TestServiceReadAndSaveAllowedFile(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	targetFile := filepath.Join(tempDir, "runtime.env")
	if err := os.WriteFile(targetFile, []byte("A=1\n"), 0o600); err != nil {
		t.Fatalf("write seed file: %v", err)
	}

	service, err := New([]AllowedFile{
		{Path: "./runtime.env", ResolvedPath: targetFile},
	}, filepath.Join(tempDir, "application.yml"), 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	readResult, err := service.Read("./runtime.env")
	if err != nil {
		t.Fatalf("read file: %v", err)
	}
	if readResult.Content != "A=1\n" {
		t.Fatalf("unexpected content: %q", readResult.Content)
	}

	if err := service.Save("./runtime.env", "A=2\n"); err != nil {
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

func TestServiceRejectsPathOutsideAllowlist(t *testing.T) {
	t.Parallel()

	tempDir := t.TempDir()
	allowedFile := filepath.Join(tempDir, "allowed.env")
	if err := os.WriteFile(allowedFile, []byte("A=1\n"), 0o600); err != nil {
		t.Fatalf("write allowed file: %v", err)
	}

	service, err := New([]AllowedFile{
		{Path: "./allowed.env", ResolvedPath: allowedFile},
	}, filepath.Join(tempDir, "application.yml"), 1024)
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
		{Path: "./missing.env", ResolvedPath: missingFile},
	}, filepath.Join(tempDir, "application.yml"), 1024)
	if err != nil {
		t.Fatalf("new service: %v", err)
	}

	if err := service.Save("./missing.env", "A=1\n"); !IsCode(err, CodeNotFound) {
		t.Fatalf("expected CodeNotFound from save, got: %v", err)
	}
}
