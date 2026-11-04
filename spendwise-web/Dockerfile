# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Vite inlines VITE_* values into the bundle at build time.
ARG VITE_GATEWAY_URL=http://localhost:8080
ARG VITE_KEYCLOAK_ACCOUNT_URL=http://localhost:8180/realms/spendwise/account
ENV VITE_GATEWAY_URL=${VITE_GATEWAY_URL} \
    VITE_KEYCLOAK_ACCOUNT_URL=${VITE_KEYCLOAK_ACCOUNT_URL}
RUN npm run build

# Non-root nginx (uid 101), listens on 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --chown=101:101 nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build --chown=101:101 /app/dist /usr/share/nginx/html
ENV GATEWAY_URL=http://localhost:8080 \
    KEYCLOAK_URL=http://localhost:8180
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
