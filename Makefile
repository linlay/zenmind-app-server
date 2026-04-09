VERSION := $(shell cat VERSION 2>/dev/null)
ARCH := $(shell uname -m | sed 's/^x86_64$$/amd64/' | sed 's/^aarch64$$/arm64/' | sed 's/^arm64$$/arm64/' | sed 's/^amd64$$/amd64/')

.PHONY: backend-build backend-test frontend-build docker-build docker-up docker-down size-check config-sync release release-program clean

backend-build:
	cd backend && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o app ./cmd/server

backend-test:
	cd backend && go test ./...

config-sync:
	cd backend && go run ./cmd/managedconfigsync

frontend-build:
	cd frontend && npm ci && npm run build

docker-build:
	docker compose build

docker-up:
	docker compose up -d --build

docker-down:
	docker compose down

size-check:
	@echo "backend image size bytes:" && docker image inspect app-server-backend --format '{{.Size}}'
	@echo "frontend image size bytes:" && docker image inspect app-server-frontend --format '{{.Size}}'

release:
	VERSION=$(VERSION) ARCH=$(ARCH) bash scripts/release.sh

release-program:
	VERSION=$(VERSION) ARCH=$(ARCH) bash scripts/release-program.sh

clean:
	rm -f backend/app
	rm -f frontend/frontend-gateway
