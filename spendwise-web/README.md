# spendwise-web

SpendWise web app (React + Vite), served by nginx on **http://localhost:5173**.

It talks only to the API gateway (`http://localhost:8080`), which acts as the
backend-for-frontend: the browser holds an HttpOnly `SPENDWISE_SESSION` cookie,
never an access token.

| URL | What you see |
|---|---|
| `/` | Session check (`GET /bff/user`): signed in (user, roles, API check, sign out), signed out (sign-in button), or gateway error / unreachable |
| `/?login_error` | Sign-in failed (the gateway sends you here when the login cannot complete) |
| anything else | Page not found |

## Run with the stack

```bash
docker compose up -d --build web
```

## Run without Docker (needs Node 22)

```bash
cd spendwise-web
npm ci
npm run dev
```

Stop the `web` container first: both use port 5173, the only origin the
gateway allows (`spa-base-url` and the CORS allow-list in
`config-repo/api-gateway.yml`).
