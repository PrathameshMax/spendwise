// Baked in at build time (Dockerfile build args / .env.local for `npm run dev`).
const trimSlash = (url) => url.replace(/\/+$/, '');

export const GATEWAY_URL = trimSlash(import.meta.env.VITE_GATEWAY_URL ?? 'http://localhost:8080');

export const KEYCLOAK_ACCOUNT_URL = trimSlash(
  import.meta.env.VITE_KEYCLOAK_ACCOUNT_URL ?? 'http://localhost:8180/realms/spendwise/account',
);

// Must match spendwise.security.keycloak.registration-id in config-repo/api-gateway.yml.
export const LOGIN_PATH = '/oauth2/authorization/keycloak';
