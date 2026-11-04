import StatusBanner from '../components/StatusBanner.jsx';
import { startLogin } from '../gateway';
import usePageTitle from '../usePageTitle';

export default function LoginErrorPage() {
  usePageTitle('Sign-in failed');
  return (
    <>
      <StatusBanner tone="error" title="Sign-in failed">
        The login did not complete: it was cancelled, it expired, or Keycloak rejected it. No session was
        created.
      </StatusBanner>

      <section className="card">
        <h2>What you can do</h2>
        <ul className="checklist">
          <li>Try again. A login page left open too long expires.</li>
          <li>
            Check the gateway log for the reason:
            <pre className="cmd">docker logs spendwise-api-gateway --tail 50</pre>
          </li>
          <li>
            Check Keycloak events (failed logins, lockouts):
            <pre className="cmd">docker logs spendwise-keycloak --tail 50</pre>
          </li>
        </ul>
        <div className="actions">
          <button type="button" className="button" onClick={startLogin}>
            Try again
          </button>
          <a className="button button-secondary" href="/">
            Back to home
          </a>
        </div>
      </section>
    </>
  );
}
