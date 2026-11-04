import { useEffect, useState } from 'react';
import { fetchSessionUser, GatewayUnreachableError } from '../gateway';
import GatewayErrorPage from './GatewayErrorPage.jsx';
import LoadingPage from './LoadingPage.jsx';
import SignedInPage from './SignedInPage.jsx';
import SignedOutPage from './SignedOutPage.jsx';

// GET /bff/user decides what the user sees:
//   200 -> signed in, 401 -> signed out, other status -> gateway error,
//   no response at all -> gateway unreachable.
export default function HomePage() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setState({ kind: 'loading' });
    fetchSessionUser()
      .then((result) => {
        if (cancelled) return;
        if (result.ok) {
          setState({ kind: 'signed-in', user: result.body, check: result });
        } else if (result.status === 401) {
          setState({ kind: 'signed-out', check: result });
        } else {
          setState({ kind: 'gateway-error', check: result });
        }
      })
      .catch((error) => {
        if (cancelled) return;
        if (error instanceof GatewayUnreachableError) {
          setState({ kind: 'unreachable', url: error.url });
        } else {
          setState({ kind: 'client-error', message: error.message });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = () => setAttempt((n) => n + 1);

  switch (state.kind) {
    case 'loading':
      return <LoadingPage />;
    case 'signed-in':
      return <SignedInPage user={state.user} check={state.check} />;
    case 'signed-out':
      return <SignedOutPage check={state.check} />;
    case 'unreachable':
      return <GatewayErrorPage unreachableUrl={state.url} onRetry={retry} />;
    case 'gateway-error':
      return <GatewayErrorPage result={state.check} onRetry={retry} />;
    default:
      return <GatewayErrorPage clientMessage={state.message} onRetry={retry} />;
  }
}
