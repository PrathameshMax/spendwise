import HttpResult from '../components/HttpResult.jsx';
import StatusBanner from '../components/StatusBanner.jsx';
import usePageTitle from '../usePageTitle';

export default function GatewayErrorPage({ unreachableUrl, result, clientMessage, onRetry }) {
  usePageTitle('Service problem');

  if (unreachableUrl) {
    return (
      <>
        <StatusBanner tone="error" title="Cannot reach the API gateway">
          No response from <code>{unreachableUrl}</code>. The gateway is stopped, still starting, or blocked.
        </StatusBanner>
        <section className="card">
          <h2>Check</h2>
          <ul className="checklist">
            <li>
              Is it running and healthy?
              <pre className="cmd">docker compose ps api-gateway</pre>
            </li>
            <li>
              Why did it stop or fail to start?
              <pre className="cmd">docker logs spendwise-api-gateway --tail 50</pre>
            </li>
          </ul>
          <button type="button" className="button" onClick={onRetry}>
            Retry
          </button>
        </section>
      </>
    );
  }

  if (result) {
    return (
      <>
        <StatusBanner tone="error" title={`The gateway answered ${result.status}`}>
          The gateway is up, but it could not check your session. Use the correlation ID below to find this
          request in the logs.
        </StatusBanner>
        <section className="card">
          <h2>Response</h2>
          <HttpResult result={result} />
          <button type="button" className="button" onClick={onRetry}>
            Retry
          </button>
        </section>
      </>
    );
  }

  return (
    <>
      <StatusBanner tone="error" title="Something went wrong in the page">
        {clientMessage}
      </StatusBanner>
      <button type="button" className="button" onClick={onRetry}>
        Retry
      </button>
    </>
  );
}
