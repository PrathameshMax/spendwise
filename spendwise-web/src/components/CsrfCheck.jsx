import { useState } from 'react';
import HttpResult from './HttpResult.jsx';
import { GatewayUnreachableError, readCsrfToken, updateMyCurrency } from '../gateway';

const CHECKS = [
  {
    id: 'without',
    label: 'Write without CSRF token',
    expected: 403,
    includeCsrfToken: false,
  },
  {
    id: 'with',
    label: 'Write with CSRF token',
    expected: 200,
    includeCsrfToken: true,
  },
];

// Session-cookie requests are the ones a forged cross-site form could ride on,
// so the gateway only accepts a write when X-XSRF-TOKEN repeats the XSRF-TOKEN
// cookie value (double-submit). Bearer-token clients are exempt: a browser
// never attaches an Authorization header on its own.
export default function CsrfCheck() {
  const [results, setResults] = useState({});

  const run = (check) => {
    setResults((current) => ({ ...current, [check.id]: { kind: 'loading' } }));
    updateMyCurrency({ includeCsrfToken: check.includeCsrfToken })
      .then((result) => setResults((current) => ({ ...current, [check.id]: { kind: 'done', result } })))
      .catch((error) =>
        setResults((current) => ({
          ...current,
          [check.id]: {
            kind: 'failed',
            message: error instanceof GatewayUnreachableError ? error.message : `Unexpected error: ${error.message}`,
          },
        })),
      );
  };

  const csrfCookiePresent = readCsrfToken() !== null;

  return (
    <section className="card">
      <h2>Security check: CSRF</h2>
      <p className="muted">
        Sends <code>PATCH /api/v1/users/me</code> with your session cookie, once without and once with the{' '}
        <code>X-XSRF-TOKEN</code> header. XSRF-TOKEN cookie: {csrfCookiePresent ? 'present' : 'not issued yet'}.
      </p>
      {CHECKS.map((check) => {
        const state = results[check.id];
        const passed = state?.kind === 'done' ? state.result.status === check.expected : null;
        return (
          <div key={check.id} className="check">
            <div className="check-line">
              <button
                type="button"
                className="button button-secondary"
                onClick={() => run(check)}
                disabled={state?.kind === 'loading'}
              >
                {check.label}
              </button>
              <span className="muted">expect {check.expected}</span>
              {passed === true ? <span className="verdict verdict-pass">PASS</span> : null}
              {passed === false ? <span className="verdict verdict-fail">FAIL</span> : null}
            </div>
            {state?.kind === 'done' ? <HttpResult method="PATCH" result={state.result} /> : null}
            {state?.kind === 'failed' ? <p className="problem">{state.message}</p> : null}
          </div>
        );
      })}
    </section>
  );
}
