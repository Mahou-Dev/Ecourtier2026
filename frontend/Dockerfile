# ==========================================
# Multi-stage Production Dockerfile for Frontend (React + Vite + Nginx non-root)
# ==========================================

# --- Stage 1: Build Stage ---
FROM node:20-alpine AS builder

WORKDIR /app

ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Stage 2: Runtime Stage (Nginx non-root port 8080) ---
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
