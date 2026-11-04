import StatusBanner from '../components/StatusBanner.jsx';
import { startLogin } from '../gateway';
import usePageTitle from '../usePageTitle';

export default function SignedOutPage({ check }) {
  usePageTitle('Sign in');
  return (
    <>
      <StatusBanner tone="info" title="You are signed out">
        Sign in to continue. You will be sent to Keycloak and brought back here once you are signed in.
      </StatusBanner>

      <section className="card">
        <h2>Sign in</h2>
        <p className="muted">
          Authorization Code flow with PKCE, run by the gateway. Your browser never receives an access token.
        </p>
        <button type="button" className="button" onClick={startLogin}>
          Sign in with Keycloak
        </button>
        <p className="muted small">
          Session check: <code>GET {check.path}</code> returned {check.status}
          {check.redirected ? ' (redirect)' : ''}, which is expected when nobody is signed in.
        </p>
      </section>
    </>
  );
}
