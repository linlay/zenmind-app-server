package managedconfig

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const DefaultMaxBytes int64 = 1 << 20

type AllowedFile struct {
	ID            string
	Name          string
	Type          string
	HostPath      string
	ContainerPath string
	Path          string
	ResolvedPath  string
}

type FileMeta struct {
	ID            string     `json:"id"`
	Name          string     `json:"name"`
	Type          string     `json:"type"`
	HostPath      string     `json:"hostPath"`
	ContainerPath string     `json:"containerPath"`
	Path          string     `json:"path"`
	ResolvedPath  string     `json:"resolvedPath"`
	Exists        bool       `json:"exists"`
	Size          int64      `json:"size"`
	UpdateAt      *time.Time `json:"updateAt"`
}

type ReadResult struct {
	ID            string     `json:"id"`
	Name          string     `json:"name"`
	Type          string     `json:"type"`
	HostPath      string     `json:"hostPath"`
	ContainerPath string     `json:"containerPath"`
	Path          string     `json:"path"`
	ResolvedPath  string     `json:"resolvedPath"`
	Content       string     `json:"content"`
	UpdateAt      *time.Time `json:"updateAt"`
}

type ErrorCode string

const (
	CodeInvalidPath ErrorCode = "INVALID_PATH"
	CodeNotAllowed  ErrorCode = "NOT_ALLOWED"
	CodeNotFound    ErrorCode = "NOT_FOUND"
	CodeNotFile     ErrorCode = "NOT_FILE"
	CodeTooLarge    ErrorCode = "TOO_LARGE"
)

type Error struct {
	Code    ErrorCode
	Message string
}

func (e *Error) Error() string { return e.Message }

func IsCode(err error, code ErrorCode) bool {
	var serviceError *Error
	if !errors.As(err, &serviceError) {
		return false
	}
	return serviceError.Code == code
}

type Service struct {
	baseDir    string
	maxBytes   int64
	ordered    []AllowedFile
	byID       map[string]AllowedFile
	byPath     map[string]AllowedFile
	byResolved map[string]AllowedFile
}

func New(allowed []AllowedFile, baseDir string, maxBytes int64) (*Service, error) {
	baseDir = strings.TrimSpace(baseDir)
	if baseDir == "" {
		baseDir = "."
	}
	if maxBytes <= 0 {
		maxBytes = DefaultMaxBytes
	}
	s := &Service{
		baseDir:    baseDir,
		maxBytes:   maxBytes,
		ordered:    make([]AllowedFile, 0, len(allowed)),
		byID:       make(map[string]AllowedFile, len(allowed)),
		byPath:     make(map[string]AllowedFile, len(allowed)),
		byResolved: make(map[string]AllowedFile, len(allowed)),
	}
	for _, file := range allowed {
		id := strings.TrimSpace(file.ID)
		configuredPath := strings.TrimSpace(file.Path)
		if configuredPath == "" {
			continue
		}
		resolvedPath := strings.TrimSpace(file.ResolvedPath)
		if resolvedPath == "" {
			continue
		}
		resolvedPath = filepath.Clean(resolvedPath)
		resolvedPath, err := filepath.Abs(resolvedPath)
		if err != nil {
			return nil, fmt.Errorf("resolve editable file path failed: %w", err)
		}
		entry := AllowedFile{
			ID:            id,
			Name:          strings.TrimSpace(file.Name),
			Type:          strings.TrimSpace(file.Type),
			HostPath:      strings.TrimSpace(file.HostPath),
			ContainerPath: strings.TrimSpace(file.ContainerPath),
			Path:          configuredPath,
			ResolvedPath:  resolvedPath,
		}
		if _, ok := s.byResolved[resolvedPath]; ok {
			continue
		}
		s.ordered = append(s.ordered, entry)
		if id != "" {
			s.byID[id] = entry
		}
		s.byPath[configuredPath] = entry
		s.byResolved[resolvedPath] = entry
	}
	return s, nil
}

func (s *Service) List() ([]FileMeta, error) {
	out := make([]FileMeta, 0, len(s.ordered))
	for _, file := range s.ordered {
		meta := FileMeta{
			ID:            file.ID,
			Name:          file.Name,
			Type:          file.Type,
			HostPath:      file.HostPath,
			ContainerPath: file.ContainerPath,
			Path:          file.Path,
			ResolvedPath:  file.ResolvedPath,
			Exists:        false,
			Size:          0,
			UpdateAt:      nil,
		}
		info, err := os.Stat(file.ResolvedPath)
		if err != nil {
			if !os.IsNotExist(err) {
				return nil, err
			}
			out = append(out, meta)
			continue
		}
		if info.Mode().IsRegular() {
			meta.Exists = true
			meta.Size = info.Size()
			modifiedAt := info.ModTime().UTC()
			meta.UpdateAt = &modifiedAt
		}
		out = append(out, meta)
	}
	return out, nil
}

func (s *Service) Read(inputPath string) (*ReadResult, error) {
	file, err := s.resolveAllowed(inputPath)
	if err != nil {
		return nil, err
	}
	info, err := os.Stat(file.ResolvedPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, &Error{Code: CodeNotFound, Message: "file does not exist"}
		}
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, &Error{Code: CodeNotFile, Message: "path is not a regular file"}
	}
	if info.Size() > s.maxBytes {
		return nil, &Error{Code: CodeTooLarge, Message: "file is too large"}
	}
	raw, err := os.ReadFile(file.ResolvedPath)
	if err != nil {
		return nil, err
	}
	modifiedAt := info.ModTime().UTC()
	return &ReadResult{
		ID:            file.ID,
		Name:          file.Name,
		Type:          file.Type,
		HostPath:      file.HostPath,
		ContainerPath: file.ContainerPath,
		Path:          file.Path,
		ResolvedPath:  file.ResolvedPath,
		Content:       string(raw),
		UpdateAt:      &modifiedAt,
	}, nil
}

func (s *Service) Save(inputPath, content string) error {
	file, err := s.resolveAllowed(inputPath)
	if err != nil {
		return err
	}
	info, err := os.Stat(file.ResolvedPath)
	if err != nil {
		if os.IsNotExist(err) {
			return &Error{Code: CodeNotFound, Message: "file does not exist"}
		}
		return err
	}
	if !info.Mode().IsRegular() {
		return &Error{Code: CodeNotFile, Message: "path is not a regular file"}
	}
	if int64(len(content)) > s.maxBytes {
		return &Error{Code: CodeTooLarge, Message: "content is too large"}
	}
	parentDir := filepath.Dir(file.ResolvedPath)
	tempFile, err := os.CreateTemp(parentDir, ".editable-config-*")
	if err != nil {
		return err
	}
	tempPath := tempFile.Name()
	defer func() {
		_ = os.Remove(tempPath)
	}()

	if _, err := tempFile.WriteString(content); err != nil {
		_ = tempFile.Close()
		return err
	}
	if err := tempFile.Chmod(info.Mode().Perm()); err != nil {
		_ = tempFile.Close()
		return err
	}
	if err := tempFile.Close(); err != nil {
		return err
	}
	return os.Rename(tempPath, file.ResolvedPath)
}

func (s *Service) resolveAllowed(inputPath string) (AllowedFile, error) {
	candidate := strings.TrimSpace(inputPath)
	if candidate == "" {
		return AllowedFile{}, &Error{Code: CodeInvalidPath, Message: "path is required"}
	}
	if direct, ok := s.byID[candidate]; ok {
		return direct, nil
	}
	if direct, ok := s.byPath[candidate]; ok {
		return direct, nil
	}
	resolvedPath := candidate
	if !filepath.IsAbs(resolvedPath) {
		resolvedPath = filepath.Join(s.baseDir, resolvedPath)
	}
	resolvedPath = filepath.Clean(resolvedPath)
	resolvedPath, err := filepath.Abs(resolvedPath)
	if err != nil {
		return AllowedFile{}, &Error{Code: CodeInvalidPath, Message: "path is invalid"}
	}
	if resolved, ok := s.byResolved[resolvedPath]; ok {
		return resolved, nil
	}
	return AllowedFile{}, &Error{Code: CodeNotAllowed, Message: "path is not in editable file allowlist"}
}
