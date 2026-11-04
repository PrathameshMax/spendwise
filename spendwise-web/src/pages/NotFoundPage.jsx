import StatusBanner from '../components/StatusBanner.jsx';
import usePageTitle from '../usePageTitle';

export default function NotFoundPage({ path }) {
  usePageTitle('Page not found');
  return (
    <>
      <StatusBanner tone="warning" title="Page not found">
        There is no page at <code>{path}</code>.
      </StatusBanner>
      <a className="button" href="/">
        Go to home
      </a>
    </>
  );
}
