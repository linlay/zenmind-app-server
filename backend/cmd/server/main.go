package main

import (
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"syscall"
	"time"

	"zenmind-app-server/backend/internal/app"
	"zenmind-app-server/backend/internal/config"
	"zenmind-app-server/backend/internal/db"
	"zenmind-app-server/backend/internal/security"
	"zenmind-app-server/backend/internal/store"
)

func main() {
	logger := log.New(os.Stdout, "[backend] ", log.LstdFlags|log.Lmicroseconds)
	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("load config failed: %v", err)
	}
	conn, err := db.Open(cfg.DBPath)
	if err != nil {
		logger.Fatalf("open db failed: %v", err)
	}
	defer conn.Close()

	schemaPath := os.Getenv("AUTH_SCHEMA_PATH")
	if schemaPath == "" {
		schemaPath = filepath.Join(".", "schema.sql")
	}
	if err := db.InitSchema(conn, schemaPath); err != nil {
		logger.Fatalf("init schema failed: %v", err)
	}

	st := store.New(conn)
	keys := security.NewKeyManager(conn)
	server, err := app.New(cfg, st, keys, logger)
	if err != nil {
		logger.Fatalf("init app failed: %v", err)
	}
	defer server.Close()

	httpServer := &http.Server{
		Addr:              ":" + strconv.Itoa(cfg.ServerPort),
		Handler:           app.NewProgramHandler(cfg.FrontendDistDir, server.Handler()),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	go func() {
		logger.Printf("listening on %s", httpServer.Addr)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatalf("listen failed: %v", err)
		}
	}()

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	_ = httpServer.Close()
}
