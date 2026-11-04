import usePageTitle from '../usePageTitle';

export default function LoadingPage() {
  usePageTitle('Checking session');
  return (
    <div className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>
        Checking your session with the gateway (<code>GET /bff/user</code>)...
      </span>
    </div>
  );
}
