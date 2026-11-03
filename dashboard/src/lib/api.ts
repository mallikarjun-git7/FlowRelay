
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type CsrfResponse = {
  headerName: string;
  token: string;
};

async function request<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const method = (options?.method ?? 'GET').toUpperCase();
  const headers = new Headers(options?.headers);

  // Include a CSRF token for state-changing requests.
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrfResponse = await fetch(
      `${BASE_URL}/api/v1/auth/csrf`,
      {
        method: 'GET',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {
          Accept: 'application/json',
        },
      }
    );

    if (!csrfResponse.ok) {
      throw new Error(
        `Unable to obtain CSRF token: HTTP ${csrfResponse.status}`
      );
    }

    const csrf: CsrfResponse = await csrfResponse.json();

    headers.set(csrf.headerName, csrf.token);
  }

  if (options?.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  headers.set('Accept', 'application/json');

  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    method,
    credentials: 'same-origin',
    headers,
  });

  if (!res.ok) {
    const body = await res.text();

    throw new Error(
      `${res.status} ${res.statusText}: ${body}`
    );
  }

  // Allow endpoints that return an empty success response.
  if (res.status === 204) {
    return undefined as T;
  }

  const responseText = await res.text();

  if (!responseText) {
    return undefined as T;
  }

  return JSON.parse(responseText) as T;
}

export const api = {
  // Jobs
  getJobs: (params?: Record<string, string>) => {
    const qs = params
      ? '?' + new URLSearchParams(params).toString()
      : '';

    return request<
      import('../types/api').PaginatedResponse<
        import('../types/api').Job
      >
    >(`/api/v1/jobs${qs}`);
  },

  getJob: (id: string) =>
    request<import('../types/api').Job>(
      `/api/v1/jobs/${id}`
    ),

  submitJob: (data: Record<string, unknown>) =>
    request<import('../types/api').Job>(
      '/api/v1/jobs',
      {
        method: 'POST',
        body: JSON.stringify(data),
      }
    ),

  // AI diagnosis
  diagnoseJob: (jobId: string) =>
    request<import('../types/api').AiDiagnosisResponse>(
      `/api/v1/admin/ai/jobs/${jobId}/diagnose`,
      {
        method: 'POST',
      }
    ),

  // Admin dashboard
  getDashboardSummary: () =>
    request<import('../types/api').DashboardSummaryResponse>(
      '/api/v1/admin/dashboard/summary'
    ),

  getQueueStats: () =>
    request<import('../types/api').QueueStatsResponse>(
      '/api/v1/admin/queues/stats'
    ),

  getWorkers: () =>
    request<import('../types/api').WorkerSummaryResponse>(
      '/api/v1/admin/workers'
    ),

  getJobEvents: (jobId: string) =>
    request<import('../types/api').JobEventResponse[]>(
      `/api/v1/admin/jobs/${jobId}/events`
    ),

  requeueJob: (
    jobId: string,
    body?: {
      resetAttempts?: boolean;
      reason?: string;
    }
  ) =>
    request<import('../types/api').AdminActionResponse>(
      `/api/v1/admin/jobs/${jobId}/requeue`,
      {
        method: 'POST',
        body: JSON.stringify(body ?? {}),
      }
    ),

  cancelJob: (jobId: string) =>
    request<import('../types/api').AdminActionResponse>(
      `/api/v1/admin/jobs/${jobId}/cancel`,
      {
        method: 'POST',
      }
    ),

  retryNow: (
    jobId: string,
    body?: {
      resetAttempts?: boolean;
    }
  ) =>
    request<import('../types/api').AdminActionResponse>(
      `/api/v1/admin/jobs/${jobId}/retry-now`,
      {
        method: 'POST',
        body: JSON.stringify(body ?? {}),
      }
    ),

  // Worker
  getWorkerConfig: () =>
    request<import('../types/api').WorkerConfig>(
      '/api/v1/workers/config'
    ),

  runOnce: (workerId: string) =>
    request<{
      workerId: string;
      durationMs: number;
    }>(
      `/api/v1/workers/${workerId}/run-once`,
      {
        method: 'POST',
      }
    ),

  // Health
  health: () =>
    request<Record<string, unknown>>(
      '/actuator/health'
    ),
};
