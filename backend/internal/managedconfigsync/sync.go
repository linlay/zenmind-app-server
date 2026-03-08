package managedconfigsync

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"zenmind-app-server/backend/internal/managedconfigregistry"
)

const (
	runtimeRegistryContainerPath = "/app/config/config-files.runtime.yml"
	configsDir                   = "configs"

	volumeStartMarker = "      # BEGIN GENERATED CONFIG FILES VOLUMES"
	volumeEndMarker   = "      # END GENERATED CONFIG FILES VOLUMES"
)

func Sync(repoRoot string) error {
	root, err := filepath.Abs(strings.TrimSpace(repoRoot))
	if err != nil {
		return fmt.Errorf("resolve repo root failed: %w", err)
	}

	sourceRegistryPath := filepath.Join(root, configsDir, "config-files.yml")
	runtimeRegistryPath := filepath.Join(root, configsDir, "config-files.runtime.yml")
	composePath := filepath.Join(root, "docker-compose.yml")

	sourceRegistry, err := managedconfigregistry.LoadSource(sourceRegistryPath)
	if err != nil {
		return err
	}
	runtimeRegistry, err := managedconfigregistry.BuildRuntime(sourceRegistry, root)
	if err != nil {
		return err
	}
	runtimeRaw, err := managedconfigregistry.MarshalRuntime(runtimeRegistry)
	if err != nil {
		return fmt.Errorf("marshal runtime registry failed: %w", err)
	}
	if err := os.WriteFile(runtimeRegistryPath, runtimeRaw, 0o644); err != nil {
		return fmt.Errorf("write runtime registry failed: %w", err)
	}

	composeRaw, err := os.ReadFile(composePath)
	if err != nil {
		return fmt.Errorf("read compose file failed: %w", err)
	}
	updatedCompose, err := replaceGeneratedBlock(string(composeRaw), volumeStartMarker, volumeEndMarker, renderVolumeBlock(runtimeRegistry, root))
	if err != nil {
		return err
	}
	if err := os.WriteFile(composePath, []byte(updatedCompose), 0o644); err != nil {
		return fmt.Errorf("write compose file failed: %w", err)
	}
	return nil
}

func renderVolumeBlock(registry *managedconfigregistry.RuntimeRegistry, repoRoot string) []string {
	lines := []string{
		"      - type: bind",
		"        source: ./configs/config-files.runtime.yml",
		"        target: " + runtimeRegistryContainerPath,
	}
	for _, file := range registry.Files {
		lines = append(lines,
			"      - type: bind",
			"        source: "+toComposePath(repoRoot, file.HostPath),
			"        target: "+file.ContainerPath,
		)
	}
	return lines
}

func replaceGeneratedBlock(content, startMarker, endMarker string, generated []string) (string, error) {
	lines := strings.Split(content, "\n")
	startIndex := -1
	endIndex := -1
	for i, line := range lines {
		if line == startMarker {
			startIndex = i
		}
		if line == endMarker {
			endIndex = i
			break
		}
	}
	if startIndex < 0 || endIndex < 0 || endIndex <= startIndex {
		return "", fmt.Errorf("generated block markers not found: %s ... %s", startMarker, endMarker)
	}
	out := make([]string, 0, len(lines)-((endIndex-startIndex)-1)+len(generated))
	out = append(out, lines[:startIndex+1]...)
	out = append(out, generated...)
	out = append(out, lines[endIndex:]...)
	return strings.Join(out, "\n"), nil
}

func toComposePath(repoRoot, hostPath string) string {
	cleaned := filepath.Clean(hostPath)
	if rel, err := filepath.Rel(repoRoot, cleaned); err == nil && rel != "" && !strings.HasPrefix(rel, ".."+string(filepath.Separator)) && rel != ".." {
		if rel == ".env" {
			return "./.env"
		}
		if strings.HasPrefix(rel, "."+string(filepath.Separator)) {
			return rel
		}
		return "./" + filepath.ToSlash(rel)
	}
	if rel, err := filepath.Rel(repoRoot, cleaned); err == nil {
		return filepath.ToSlash(rel)
	}
	return filepath.ToSlash(cleaned)
}
