.PHONY: backend-build backend-test frontend-build up down size-check

backend-build:
	cd backend && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o app ./cmd/server

backend-test:
	cd backend && go test ./...

frontend-build:
	cd frontend && npm ci && npm run build

up:
	docker compose up --build

down:
	docker compose down

size-check:
	@echo "backend image size bytes:" && docker image inspect app-auth-backend-go --format '{{.Size}}'
	@echo "frontend image size bytes:" && docker image inspect app-auth-frontend-go --format '{{.Size}}'
