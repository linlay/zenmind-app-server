FROM node:20-alpine AS build
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
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
