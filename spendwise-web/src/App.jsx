import Layout from './components/Layout.jsx';
import HomePage from './pages/HomePage.jsx';
import LoginErrorPage from './pages/LoginErrorPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';

// Routes the gateway can send the browser to (SecurityConfig):
//   /               after login and after logout
//   /?login_error   when Keycloak or the code exchange fails
function resolvePage() {
  const { pathname, search } = window.location;
  if (pathname !== '/' && pathname !== '/index.html') {
    return <NotFoundPage path={pathname} />;
  }
  if (new URLSearchParams(search).has('login_error')) {
    return <LoginErrorPage />;
  }
  return <HomePage />;
}

export default function App() {
  return <Layout>{resolvePage()}</Layout>;
}
