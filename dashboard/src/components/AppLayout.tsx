
import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

export default function AppLayout() {
  const [authenticated, setAuthenticated] = useState(false);
  const [checking, setChecking] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    async function checkAuthentication() {
      try {
        const response = await fetch(
          '/api/v1/admin/dashboard/summary',
          { credentials: 'same-origin' }
        );

        if (active) {
          setAuthenticated(response.ok);
        }
      } catch {
        if (active) setAuthenticated(false);
      } finally {
        if (active) setChecking(false);
      }
    }

    void checkAuthentication();

    return () => {
      active = false;
    };
  }, []);

  async function handleLogout() {
    setLoggingOut(true);
    setError('');

    try {
      const csrfResponse = await fetch(
        '/api/v1/auth/csrf',
        {
          credentials: 'same-origin',
          cache: 'no-store',
        }
      );

      if (!csrfResponse.ok) {
        throw new Error('Could not obtain CSRF token');
      }

      const csrf: {
        headerName: string;
        token: string;
      } = await csrfResponse.json();

      const response = await fetch('/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          [csrf.headerName]: csrf.token,
        },
      });

      if (!response.ok) {
        throw new Error('Logout request failed');
      }

      window.location.replace('/login');
    } catch {
      setError('Unable to sign out. Please try again.');
      setLoggingOut(false);
    }
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />

      <main className="flex-1 overflow-auto">
        <header className="flex items-center justify-between border-b border-slate-700 px-6 py-4">
          <div>
            <h1 className="text-lg font-semibold">
              FlowRelay AI
            </h1>

            <p className="text-sm text-slate-400">
              Distributed Job Queue Dashboard
            </p>
          </div>

          <div className="flex items-center gap-3">
            {checking ? (
              <span className="text-sm text-slate-400">
                Checking session...
              </span>
            ) : authenticated ? (
              <button
                type="button"
                onClick={handleLogout}
                disabled={loggingOut}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
              >
                {loggingOut ? 'Signing out...' : 'Sign out'}
              </button>
            ) : (
              <a
                href="/login"
                className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
              >
                Sign in
              </a>
            )}
          </div>
        </header>

        {error && (
          <div
            role="alert"
            className="mx-6 mt-4 rounded-lg border border-red-500 p-3 text-sm text-red-400"
          >
            {error}
          </div>
        )}

        <div className="max-w-[1600px] p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
