import { GATEWAY_URL, LOGIN_PATH } from './config';

/**
 * The SPA never sees an access token. Every call carries only the gateway's
 * HttpOnly SPENDWISE_SESSION cookie (credentials: 'include'); the gateway looks
 * up the tokens in its Redis session and relays them to the services.
 */
export class GatewayUnreachableError extends Error {
  constructor(url, cause) {
    super(`Cannot reach the API gateway at ${url}`);
    this.name = 'GatewayUnreachableError';
    this.url = url;
    this.cause = cause;
  }
}

async function request(path, init = {}) {
  let response;
  try {
    response = await fetch(`${GATEWAY_URL}${path}`, {
      credentials: 'include',
      redirect: 'manual',
      headers: { Accept: 'application/json', ...(init.headers ?? {}) },
      ...init,
    });
  } catch (cause) {
    throw new GatewayUnreachableError(GATEWAY_URL, cause);
  }

  // A redirect (e.g. towards Keycloak) means the session cannot be used as-is.
  if (response.type === 'opaqueredirect') {
    return { path, status: 401, ok: false, body: null, correlationId: null, redirected: true };
  }

  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return {
    path,
    status: response.status,
    ok: response.ok,
    body,
    correlationId: response.headers.get('X-Correlation-ID'),
    redirected: false,
  };
}

export const fetchSessionUser = () => request('/bff/user');

export const fetchMyProfile = () => request('/api/v1/users/me');

export const loginUrl = () => `${GATEWAY_URL}${LOGIN_PATH}`;

export function startLogin() {
  window.location.assign(loginUrl());
}

// The gateway writes XSRF-TOKEN (readable, not HttpOnly) on every response.
// Cookies are not port-scoped, so a page on localhost:5173 can read the cookie
// set by localhost:8080.
export function readCsrfToken() {
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith('XSRF-TOKEN='));
  return entry ? decodeURIComponent(entry.substring('XSRF-TOKEN='.length)) : null;
}

// Top-level form POST (not fetch): the gateway answers with a redirect to
// Keycloak's end-session endpoint, which then returns the browser here.
export function submitLogout(csrfToken) {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = `${GATEWAY_URL}/logout`;
  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = '_csrf';
  input.value = csrfToken;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}

// RFC 7807 problem details from the gateway/services, or a fallback text.
export function describeProblem(result) {
  const body = result.body;
  if (body && typeof body === 'object') {
    return {
      title: body.title ?? body.error ?? `HTTP ${result.status}`,
      detail: body.detail ?? body.message ?? null,
    };
  }
  return { title: `HTTP ${result.status}`, detail: typeof body === 'string' ? body.slice(0, 300) : null };
}
