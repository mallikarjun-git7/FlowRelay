
import { useEffect, useState } from 'react';

export default function Settings() {
  const [health, setHealth] = useState<string>('checking...');

  // Use the current backend origin by default.
  const configuredUrl = import.meta.env.VITE_API_BASE_URL;
  const baseUrl = (configuredUrl || window.location.origin)
    .replace(/\/+$/, '');

  useEffect(() => {
    const controller = new AbortController();

    async function checkHealth() {
      try {
        const response = await fetch(
          `${baseUrl}/actuator/health`,
          {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store',
            signal: controller.signal,
          }
        );

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const data: { status?: string } =
          await response.json();

        setHealth(data.status ?? 'unknown');
      } catch (error) {
        if (
          error instanceof Error &&
          error.name === 'AbortError'
        ) {
          return;
        }

        setHealth('unreachable');
      }
    }

    void checkHealth();

    return () => controller.abort();
  }, [baseUrl]);

  return (
    <div>
      <h1 className="text-h1 font-semibold text-on-surface mb-6">
        Settings
      </h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

        {/* Connection */}
        <div className="bg-surface-container rounded-md border border-outline-variant p-4">
          <h3 className="text-sm font-semibold text-on-surface mb-3">
            Connection
          </h3>

          <div className="space-y-2 text-xs">
            <div className="flex justify-between gap-4">
              <span className="text-on-surface-variant">
                API Base URL
              </span>

              <span className="font-mono text-on-surface break-all text-right">
                {baseUrl}
              </span>
            </div>

            <div className="flex justify-between gap-4">
              <span className="text-on-surface-variant">
                Backend Health
              </span>

              <span
                className={`font-mono font-medium ${
                  health === 'UP'
                    ? 'text-green-400'
                    : health === 'unreachable'
                    ? 'text-error'
                    : 'text-tertiary'
                }`}
              >
                {health}
              </span>
            </div>
          </div>
        </div>

        {/* Links */}
        <div className="bg-surface-container rounded-md border border-outline-variant p-4">
          <h3 className="text-sm font-semibold text-on-surface mb-3">
            Links
          </h3>

          <div className="space-y-2">
            <a
              href={`${baseUrl}/actuator/health`}
              target="_blank"
              rel="noopener noreferrer"
              className="block text-xs text-primary hover:underline"
            >
              Backend Health &rarr;
            </a>

            {import.meta.env.DEV && (
              <>
                <a
                  href={`${baseUrl}/swagger-ui.html`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block text-xs text-primary hover:underline"
                >
                  Swagger UI &rarr;
                </a>

                <a
                  href="http://localhost:3000"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block text-xs text-primary hover:underline"
                >
                  Local Grafana &rarr;
                </a>
              </>
            )}
          </div>
        </div>

        {/* Build Info */}
        <div className="bg-surface-container rounded-md border border-outline-variant p-4 md:col-span-2">
          <h3 className="text-sm font-semibold text-on-surface mb-3">
            Build Info
          </h3>

          <div className="space-y-1 text-xs">
            <div className="flex justify-between gap-4">
              <span className="text-on-surface-variant">
                Dashboard Version
              </span>

              <span className="font-mono text-on-surface">
                1.0.0
              </span>
            </div>

            <div className="flex justify-between gap-4">
              <span className="text-on-surface-variant">
                Stack
              </span>

              <span className="font-mono text-on-surface text-right">
                React + TypeScript + Vite + Tailwind CSS
              </span>
            </div>

            <div className="flex justify-between gap-4">
              <span className="text-on-surface-variant">
                Backend
              </span>

              <span className="font-mono text-on-surface text-right">
                Java 21 + Spring Boot 3.3 + PostgreSQL 16
              </span>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
