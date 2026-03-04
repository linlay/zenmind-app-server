FROM node:20-alpine AS ui-build
WORKDIR /app
ARG VITE_BASE_PATH=/admin/
ARG VITE_DEV_PORT=11950
ARG VITE_DEV_STRICT_PORT=true
ARG VITE_API_PROXY_PATH=/admin/api
ARG VITE_API_PROXY_TARGET=http://backend:8080
ENV VITE_BASE_PATH=$VITE_BASE_PATH \
    VITE_DEV_PORT=$VITE_DEV_PORT \
    VITE_DEV_STRICT_PORT=$VITE_DEV_STRICT_PORT \
    VITE_API_PROXY_PATH=$VITE_API_PROXY_PATH \
    VITE_API_PROXY_TARGET=$VITE_API_PROXY_TARGET
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM golang:1.23-alpine AS proxy-build
WORKDIR /src
COPY proxy ./proxy
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w -buildid=' -o /out/frontend-gateway ./proxy/main.go

FROM scratch
WORKDIR /app
COPY --from=proxy-build /out/frontend-gateway /app/frontend-gateway
COPY --from=ui-build /app/dist /app/dist
EXPOSE 80
ENV BACKEND_TARGET=http://backend:8080
ENV STATIC_DIR=/app/dist
ENTRYPOINT ["/app/frontend-gateway"]
