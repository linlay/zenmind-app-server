package main

import (
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

func main() {
	backendTarget := getenv("BACKEND_TARGET", "http://app-server-backend:8080")
	listenAddr := getenv("LISTEN_ADDR", ":80")
	staticDir := getenv("STATIC_DIR", "/app/dist")

	targetURL, err := url.Parse(backendTarget)
	if err != nil {
		log.Fatalf("invalid BACKEND_TARGET: %v", err)
	}
	proxy := httputil.NewSingleHostReverseProxy(targetURL)
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		http.Error(w, "upstream unavailable", http.StatusBadGateway)
	}
	proxy.ModifyResponse = func(resp *http.Response) error {
		resp.Header.Set("X-Proxy-By", "frontend-gateway")
		return nil
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		switch {
		case strings.HasPrefix(path, "/admin/api/") || strings.HasPrefix(path, "/oauth2/") || strings.HasPrefix(path, "/openid/"):
			proxy.ServeHTTP(w, r)
			return
		case path == "/":
			http.Redirect(w, r, "/admin/", http.StatusFound)
			return
		case path == "/admin":
			http.Redirect(w, r, "/admin/", http.StatusMovedPermanently)
			return
		case strings.HasPrefix(path, "/admin/"):
			relPath := strings.TrimPrefix(path, "/admin/")
			relPath = strings.TrimPrefix(relPath, "/")
			serveSPAFile(w, r, staticDir, relPath)
			return
		default:
			http.NotFound(w, r)
			return
		}
	})

	log.Printf("frontend gateway listening on %s, backend=%s", listenAddr, backendTarget)
	if err := http.ListenAndServe(listenAddr, loggingMiddleware(mux)); err != nil {
		log.Fatalf("listen failed: %v", err)
	}
}

func serveSPAFile(w http.ResponseWriter, r *http.Request, staticDir, relPath string) {
	if relPath == "" {
		relPath = "index.html"
	}
	cleanPath := filepath.Clean(relPath)
	if strings.HasPrefix(cleanPath, "..") {
		http.NotFound(w, r)
		return
	}
	fullPath := filepath.Join(staticDir, cleanPath)
	info, err := os.Stat(fullPath)
	if err == nil && !info.IsDir() {
		http.ServeFile(w, r, fullPath)
		return
	}
	http.ServeFile(w, r, filepath.Join(staticDir, "index.html"))
}

func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		log.Printf("%s %s", r.Method, r.URL.Path)
		next.ServeHTTP(w, r)
	})
}

func getenv(key, fallback string) string {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	return v
}
