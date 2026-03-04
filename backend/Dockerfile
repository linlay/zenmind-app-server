FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o /out/app ./cmd/server

FROM scratch
WORKDIR /app
COPY --from=build /out/app /app/app
COPY schema.sql /app/schema.sql
COPY application.yml /app/application.yml
EXPOSE 8080
ENV AUTH_SCHEMA_PATH=/app/schema.sql
ENV AUTH_APPLICATION_YML_PATH=/app/application.yml
ENV AUTH_DB_PATH=/data/auth.db
ENTRYPOINT ["/app/app"]
