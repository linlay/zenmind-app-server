package app

import (
	"net/http"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
)

var immutableAssetPath = regexp.MustCompile(`^.+\.[0-9a-f]{8}(?:\.chunk)?\.(?:js|css|woff2?|png|jpe?g|gif|svg|eot|ttf|otf)$`)

const noStoreCacheControl = "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0, s-maxage=0"

type programHandler struct {
	api       http.Handler
	distDir   string
	indexPath string
}

func NewProgramHandler(distDir string, api http.Handler) http.Handler {
	distDir = strings.TrimSpace(distDir)
	if distDir == "" {
		return api
	}
	indexPath := filepath.Join(distDir, "index.html")
	info, err := os.Stat(indexPath)
	if err != nil || info.IsDir() {
		return api
	}
	return &programHandler{api: api, distDir: distDir, indexPath: indexPath}
}

func (h *programHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Path
	switch {
	case strings.HasPrefix(p, "/admin/api/") || strings.HasPrefix(p, "/oauth2/") || strings.HasPrefix(p, "/openid/") || strings.HasPrefix(p, "/api/"):
		h.api.ServeHTTP(w, r)
	case p == "/":
		http.Redirect(w, r, "/admin/", http.StatusFound)
	case p == "/admin":
		http.Redirect(w, r, "/admin/", http.StatusMovedPermanently)
	case strings.HasPrefix(p, "/admin/"):
		h.serveUI(w, r, "/admin/")
	default:
		h.api.ServeHTTP(w, r)
	}
}

func (h *programHandler) serveUI(w http.ResponseWriter, r *http.Request, prefix string) {
	relativePath := strings.TrimPrefix(r.URL.Path, prefix)
	relativePath = strings.TrimPrefix(relativePath, "/")
	cleaned := cleanAssetPath(relativePath)
	if cleaned == "" {
		h.serveSPAShell(w, r)
		return
	}
	assetPath, ok := resolveAssetPath(h.distDir, cleaned)
	if !ok {
		http.NotFound(w, r)
		return
	}
	info, err := os.Stat(assetPath)
	if err == nil && !info.IsDir() {
		if immutableAssetPath.MatchString(cleaned) {
			w.Header().Set("Cache-Control", "public, immutable")
		} else {
			setNoStoreHeaders(w)
		}
		http.ServeFile(w, r, assetPath)
		return
	}
	if path.Ext(cleaned) == "" {
		h.serveSPAShell(w, r)
		return
	}
	http.NotFound(w, r)
}

func cleanAssetPath(relativePath string) string {
	trimmed := strings.TrimSpace(relativePath)
	if trimmed == "" {
		return ""
	}
	cleaned := path.Clean("/" + trimmed)
	if cleaned == "/" {
		return ""
	}
	return strings.TrimPrefix(cleaned, "/")
}

func resolveAssetPath(distDir, relativePath string) (string, bool) {
	fullPath := filepath.Join(distDir, filepath.FromSlash(relativePath))
	rel, err := filepath.Rel(distDir, fullPath)
	if err != nil {
		return "", false
	}
	if rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", false
	}
	return fullPath, true
}

func (h *programHandler) serveSPAShell(w http.ResponseWriter, r *http.Request) {
	setNoStoreHeaders(w)
	http.ServeFile(w, r, h.indexPath)
}

func setNoStoreHeaders(w http.ResponseWriter) {
	w.Header().Set("Cache-Control", noStoreCacheControl)
	w.Header().Set("Pragma", "no-cache")
	w.Header().Set("Expires", "0")
}
