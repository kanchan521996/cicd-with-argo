# ---- build stage ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
# Leave empty so the browser calls the same origin (/api), proxied by nginx or your ingress.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- runtime stage ----
# Unprivileged nginx listens on 8080 and runs as uid 101 (works with runAsNonRoot).
FROM nginxinc/nginx-unprivileged:1.27-alpine
# Service DNS name of the backend inside the cluster. Override per environment.
ENV BACKEND_URL=http://backend:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
