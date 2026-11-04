import { GATEWAY_URL } from '../config';

export default function Layout({ children }) {
  return (
    <div className="shell">
      <header className="topbar">
        <a className="brand" href="/">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          <span>SpendWise</span>
        </a>
        <span className="env-tag">local dev</span>
      </header>
      <main className="content">{children}</main>
      <footer className="footer">
        API gateway: <code>{GATEWAY_URL}</code>
      </footer>
    </div>
  );
}
