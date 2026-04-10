package db

import (
	"database/sql"
	"embed"
	"fmt"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite"
)

//go:embed schema.sql
var embeddedSchemaFS embed.FS

func Open(dbPath string) (*sql.DB, error) {
	if err := os.MkdirAll(filepath.Dir(dbPath), 0o755); err != nil {
		return nil, fmt.Errorf("create db dir: %w", err)
	}
	dsn := fmt.Sprintf("file:%s?_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)", dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	if _, err := db.Exec("PRAGMA foreign_keys = ON"); err != nil {
		return nil, err
	}
	if _, err := db.Exec("PRAGMA journal_mode = WAL"); err != nil {
		return nil, err
	}
	return db, nil
}

func InitSchema(db *sql.DB, schemaPath string) error {
	raw, err := os.ReadFile(schemaPath)
	if err != nil {
		return err
	}
	return initSchemaSQL(db, raw)
}

func InitEmbeddedSchema(db *sql.DB) error {
	raw, err := embeddedSchemaFS.ReadFile("schema.sql")
	if err != nil {
		return err
	}
	return initSchemaSQL(db, raw)
}

func initSchemaSQL(db *sql.DB, raw []byte) error {
	_, err := db.Exec(string(raw))
	return err
}
