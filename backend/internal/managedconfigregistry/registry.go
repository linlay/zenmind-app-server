package managedconfigregistry

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

type SourceRegistry struct {
	Files []SourceFile `yaml:"files"`
}

type SourceFile struct {
	ID            string `yaml:"id"`
	Name          string `yaml:"name"`
	Type          string `yaml:"type"`
	SourcePath    string `yaml:"sourcePath"`
	ContainerPath string `yaml:"containerPath"`
}

type RuntimeRegistry struct {
	Files []RuntimeFile `yaml:"files"`
}

type RuntimeFile struct {
	ID            string `yaml:"id"`
	Name          string `yaml:"name"`
	Type          string `yaml:"type"`
	HostPath      string `yaml:"hostPath"`
	ContainerPath string `yaml:"containerPath"`
}

func LoadSource(path string) (*SourceRegistry, error) {
	var registry SourceRegistry
	if err := loadYAML(path, &registry); err != nil {
		return nil, err
	}
	return &registry, nil
}

func LoadRuntime(path string) (*RuntimeRegistry, error) {
	var registry RuntimeRegistry
	if err := loadYAML(path, &registry); err != nil {
		return nil, err
	}
	return &registry, validateRuntime(&registry)
}

func BuildRuntime(source *SourceRegistry, repoRoot string) (*RuntimeRegistry, error) {
	root, err := filepath.Abs(strings.TrimSpace(repoRoot))
	if err != nil {
		return nil, fmt.Errorf("resolve repo root failed: %w", err)
	}
	files := make([]RuntimeFile, 0, len(source.Files))
	seenIDs := make(map[string]struct{}, len(source.Files))
	seenHostPaths := make(map[string]struct{}, len(source.Files))
	seenContainerPaths := make(map[string]struct{}, len(source.Files))
	for _, file := range source.Files {
		id := strings.TrimSpace(file.ID)
		name := strings.TrimSpace(file.Name)
		fileType := strings.TrimSpace(file.Type)
		sourcePath := strings.TrimSpace(file.SourcePath)
		containerPath := cleanPath(strings.TrimSpace(file.ContainerPath))

		switch {
		case id == "":
			return nil, fmt.Errorf("config file id is required")
		case name == "":
			return nil, fmt.Errorf("config file name is required for id %q", id)
		case fileType == "":
			return nil, fmt.Errorf("config file type is required for id %q", id)
		case sourcePath == "":
			return nil, fmt.Errorf("config file sourcePath is required for id %q", id)
		case containerPath == "":
			return nil, fmt.Errorf("config file containerPath is required for id %q", id)
		case !filepath.IsAbs(containerPath):
			return nil, fmt.Errorf("config file containerPath must be absolute for id %q", id)
		}

		if _, ok := seenIDs[id]; ok {
			return nil, fmt.Errorf("duplicate config file id %q", id)
		}
		seenIDs[id] = struct{}{}

		hostPath := sourcePath
		if !filepath.IsAbs(hostPath) {
			hostPath = filepath.Join(root, hostPath)
		}
		hostPath = cleanPath(hostPath)
		absHostPath, err := filepath.Abs(hostPath)
		if err != nil {
			return nil, fmt.Errorf("resolve host path failed for id %q: %w", id, err)
		}

		if _, ok := seenHostPaths[absHostPath]; ok {
			return nil, fmt.Errorf("duplicate config file sourcePath %q", sourcePath)
		}
		seenHostPaths[absHostPath] = struct{}{}

		if _, ok := seenContainerPaths[containerPath]; ok {
			return nil, fmt.Errorf("duplicate config file containerPath %q", containerPath)
		}
		seenContainerPaths[containerPath] = struct{}{}

		files = append(files, RuntimeFile{
			ID:            id,
			Name:          name,
			Type:          fileType,
			HostPath:      absHostPath,
			ContainerPath: containerPath,
		})
	}
	return &RuntimeRegistry{Files: files}, nil
}

func MarshalRuntime(registry *RuntimeRegistry) ([]byte, error) {
	return yaml.Marshal(registry)
}

func validateRuntime(registry *RuntimeRegistry) error {
	seenIDs := make(map[string]struct{}, len(registry.Files))
	seenContainerPaths := make(map[string]struct{}, len(registry.Files))
	for _, file := range registry.Files {
		id := strings.TrimSpace(file.ID)
		hostPath := cleanPath(strings.TrimSpace(file.HostPath))
		containerPath := cleanPath(strings.TrimSpace(file.ContainerPath))
		switch {
		case id == "":
			return fmt.Errorf("runtime config file id is required")
		case strings.TrimSpace(file.Name) == "":
			return fmt.Errorf("runtime config file name is required for id %q", id)
		case strings.TrimSpace(file.Type) == "":
			return fmt.Errorf("runtime config file type is required for id %q", id)
		case hostPath == "":
			return fmt.Errorf("runtime config file hostPath is required for id %q", id)
		case containerPath == "":
			return fmt.Errorf("runtime config file containerPath is required for id %q", id)
		case !filepath.IsAbs(containerPath):
			return fmt.Errorf("runtime config file containerPath must be absolute for id %q", id)
		}
		if _, ok := seenIDs[id]; ok {
			return fmt.Errorf("duplicate runtime config file id %q", id)
		}
		seenIDs[id] = struct{}{}
		if _, ok := seenContainerPaths[containerPath]; ok {
			return fmt.Errorf("duplicate runtime config file containerPath %q", containerPath)
		}
		seenContainerPaths[containerPath] = struct{}{}
	}
	return nil
}

func loadYAML(path string, target any) error {
	absPath, err := filepath.Abs(strings.TrimSpace(path))
	if err != nil {
		return fmt.Errorf("resolve registry path failed: %w", err)
	}
	raw, err := os.ReadFile(absPath)
	if err != nil {
		return fmt.Errorf("read registry failed: %w", err)
	}
	if err := yaml.Unmarshal(raw, target); err != nil {
		return fmt.Errorf("parse registry failed: %w", err)
	}
	return nil
}

func cleanPath(value string) string {
	if value == "" {
		return ""
	}
	return filepath.Clean(value)
}
