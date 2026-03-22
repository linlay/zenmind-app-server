ARG NODE_BUILD_IMAGE=node:20-alpine
ARG GO_BUILD_IMAGE=golang:1.23-alpine

FROM ${NODE_BUILD_IMAGE} AS ui-build
WORKDIR /app
ARG NPM_REGISTRY=https://registry.npmjs.org
ARG VITE_BASE_PATH=/admin/
ARG VITE_DEV_PORT=11950
ARG VITE_DEV_STRICT_PORT=true
ARG VITE_API_PROXY_PATH=/admin/api
ARG VITE_API_PROXY_TARGET=http://app-server-backend:8080
ENV VITE_BASE_PATH=$VITE_BASE_PATH \
    VITE_DEV_PORT=$VITE_DEV_PORT \
    VITE_DEV_STRICT_PORT=$VITE_DEV_STRICT_PORT \
    VITE_API_PROXY_PATH=$VITE_API_PROXY_PATH \
    VITE_API_PROXY_TARGET=$VITE_API_PROXY_TARGET
COPY package.json package-lock.json ./
RUN npm config set registry "$NPM_REGISTRY" && npm ci
COPY . .
RUN npm run build

FROM ${GO_BUILD_IMAGE} AS proxy-build
ARG TARGETOS=linux
ARG TARGETARCH=amd64
ARG GOPROXY=https://proxy.golang.org,direct
ENV GOPROXY=$GOPROXY
WORKDIR /src
COPY proxy ./proxy
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -trimpath -ldflags='-s -w -buildid=' -o /out/frontend-gateway ./proxy/main.go

FROM scratch
WORKDIR /app
COPY --from=proxy-build /out/frontend-gateway /app/frontend-gateway
COPY --from=ui-build /app/dist /app/dist
EXPOSE 80
ENV BACKEND_TARGET=http://app-server-backend:8080
ENV STATIC_DIR=/app/dist
ENTRYPOINT ["/app/frontend-gateway"]
