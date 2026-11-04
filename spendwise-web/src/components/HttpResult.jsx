import { describeProblem } from '../gateway';

export default function HttpResult({ method = 'GET', result }) {
  const problem = result.ok ? null : describeProblem(result);
  return (
    <div className="http-result">
      <div className="http-result-line">
        <span className={`status-pill ${result.ok ? 'status-ok' : 'status-bad'}`}>{result.status}</span>
        <code>
          {method} {result.path}
        </code>
        {result.correlationId ? (
          <span className="muted">
            X-Correlation-ID <code>{result.correlationId}</code>
          </span>
        ) : null}
      </div>
      {problem ? (
        <p className="problem">
          <strong>{problem.title}</strong>
          {problem.detail ? ` — ${problem.detail}` : null}
        </p>
      ) : null}
      {result.body !== null ? (
        <pre className="json">{typeof result.body === 'string' ? result.body : JSON.stringify(result.body, null, 2)}</pre>
      ) : null}
    </div>
  );
}
