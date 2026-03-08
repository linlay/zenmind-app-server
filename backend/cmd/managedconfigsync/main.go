package main

import (
	"log"
	"os"
	"path/filepath"

	"zenmind-app-server/backend/internal/managedconfigsync"
)

func main() {
	cwd, err := os.Getwd()
	if err != nil {
		log.Fatal(err)
	}
	repoRoot := filepath.Clean(filepath.Join(cwd, ".."))
	if len(os.Args) > 1 {
		repoRoot = os.Args[1]
	}
	if err := managedconfigsync.Sync(repoRoot); err != nil {
		log.Fatal(err)
	}
}
