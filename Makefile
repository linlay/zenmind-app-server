.PHONY: backend-build backend-test frontend-build docker-build docker-up docker-down size-check config-sync

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
	@echo "backend image size bytes:" && docker image inspect app-auth-backend --format '{{.Size}}'
	@echo "frontend image size bytes:" && docker image inspect app-auth-frontend --format '{{.Size}}'
