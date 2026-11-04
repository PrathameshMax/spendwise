import { useState } from 'react';
import HttpResult from '../components/HttpResult.jsx';
import StatusBanner from '../components/StatusBanner.jsx';
import { KEYCLOAK_ACCOUNT_URL } from '../config';
import { fetchMyProfile, GatewayUnreachableError, readCsrfToken, submitLogout } from '../gateway';
import usePageTitle from '../usePageTitle';

export default function SignedInPage({ user, check }) {
  usePageTitle('Signed in');
  const [apiCall, setApiCall] = useState({ kind: 'idle' });
  const [logoutError, setLogoutError] = useState(null);

  const callApi = () => {
    setApiCall({ kind: 'loading' });
    fetchMyProfile()
      .then((result) => setApiCall({ kind: 'done', result }))
      .catch((error) =>
        setApiCall({
          kind: 'failed',
          message: error instanceof GatewayUnreachableError ? error.message : `Unexpected error: ${error.message}`,
        }),
      );
  };

  const signOut = () => {
    const csrfToken = readCsrfToken();
    if (!csrfToken) {
      setLogoutError('The gateway has not issued an XSRF-TOKEN cookie yet. Reload the page and try again.');
      return;
    }
    submitLogout(csrfToken);
  };

  const roles = Array.isArray(user?.roles) ? user.roles : [];

  return (
    <>
      <StatusBanner tone="success" title="You are signed in">
        Keycloak authenticated you and the gateway keeps your tokens in its session. This page only holds an
        HttpOnly session cookie.
      </StatusBanner>

      <section className="card">
        <h2>Who you are</h2>
        <dl className="facts">
          <dt>Name</dt>
          <dd>{user?.name ?? '—'}</dd>
          <dt>Email</dt>
          <dd>{user?.email ?? '—'}</dd>
          <dt>Subject (sub)</dt>
          <dd>
            <code>{user?.subject ?? '—'}</code>
          </dd>
          <dt>Realm roles</dt>
          <dd>
            {roles.length > 0 ? (
              <ul className="chips">
                {roles.map((role) => (
                  <li key={role} className={role === 'CUSTOMER' || role === 'ADMIN' ? 'chip chip-strong' : 'chip'}>
                    {role}
                  </li>
                ))}
              </ul>
            ) : (
              '—'
            )}
          </dd>
        </dl>
        <p className="muted small">
          Session check: <code>GET {check.path}</code> returned {check.status}
          {check.correlationId ? (
            <>
              {' '}
              (X-Correlation-ID <code>{check.correlationId}</code>)
            </>
          ) : null}
        </p>
      </section>

      <section className="card">
        <h2>Check the API path</h2>
        <p className="muted">
          Browser (session cookie) &rarr; gateway (token relay) &rarr; user-service (JWT validation).
        </p>
        <button type="button" className="button" onClick={callApi} disabled={apiCall.kind === 'loading'}>
          {apiCall.kind === 'loading' ? 'Calling...' : 'Call user-service: GET /api/v1/users/me'}
        </button>
        {apiCall.kind === 'done' ? <HttpResult result={apiCall.result} /> : null}
        {apiCall.kind === 'failed' ? <p className="problem">{apiCall.message}</p> : null}
      </section>

      <section className="actions">
        <a className="button button-secondary" href={KEYCLOAK_ACCOUNT_URL}>
          Manage account in Keycloak
        </a>
        <button type="button" className="button button-danger" onClick={signOut}>
          Sign out
        </button>
      </section>
      {logoutError ? <p className="problem">{logoutError}</p> : null}
    </>
  );
}
