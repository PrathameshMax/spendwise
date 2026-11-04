const ICONS = {
  success: '✓',
  error: '!',
  info: 'i',
  warning: '?',
};

export default function StatusBanner({ tone, title, children }) {
  return (
    <section className={`banner banner-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span className="banner-icon" aria-hidden="true">
        {ICONS[tone]}
      </span>
      <div>
        <h1 className="banner-title">{title}</h1>
        {children ? <div className="banner-body">{children}</div> : null}
      </div>
    </section>
  );
}
