import { useAuthStore } from '../store/useAuthStore';
import { HealthCheckResponse } from '../types';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: any) => void;
  reject: (reason?: any) => void;
}> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

export async function apiFetch<T>(
  endpoint: string,
  options: RequestInit = {},
  isRetry = false
): Promise<T> {
  const { accessToken, refreshToken, updateTokens, logout } = useAuthStore.getState();

  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (options.body instanceof FormData) {
    delete headers['Content-Type'];
  }

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Intercept 401 Unauthorized for token refresh
  if (
    response.status === 401 &&
    !isRetry &&
    !endpoint.includes('/auth/connexion') &&
    !endpoint.includes('/auth/rafraichir')
  ) {
    if (!refreshToken) {
      logout();
      throw new Error('Session expirée');
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((newToken) => {
        return apiFetch<T>(
          endpoint,
          {
            ...options,
            headers: {
              ...options.headers,
              Authorization: `Bearer ${newToken}`,
            },
          },
          true
        );
      });
    }

    isRefreshing = true;

    try {
      const refreshRes = await fetch(`${API_BASE_URL}/api/v1/auth/rafraichir`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!refreshRes.ok) {
        throw new Error('Rafraîchissement du token échoué');
      }

      const refreshData = await refreshRes.json();
      updateTokens(refreshData.accessToken, refreshData.refreshToken);
      processQueue(null, refreshData.accessToken);
      isRefreshing = false;

      // Replay original request ONCE with new access token
      return apiFetch<T>(
        endpoint,
        {
          ...options,
          headers: {
            ...options.headers,
            Authorization: `Bearer ${refreshData.accessToken}`,
          },
        },
        true
      );
    } catch (err) {
      processQueue(err, null);
      isRefreshing = false;
      logout();
      throw err;
    }
  }

  if (!response.ok) {
    let errorMessage = `Erreur (${response.status})`;
    try {
      const errorJson = await response.json();
      if (errorJson.error) errorMessage = errorJson.error;
      else if (errorJson.message) errorMessage = errorJson.message;
    } catch (_) {}
    const err: any = new Error(errorMessage);
    err.status = response.status;
    throw err;
  }

  if (response.status === 204) {
    return {} as T;
  }

  return response.json();
}

export async function fetchHealthStatus(): Promise<HealthCheckResponse> {
  return apiFetch<HealthCheckResponse>('/api/v1/health');
}

export function getPhotoUrl(url?: string): string {
  if (!url) return 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80';
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  if (url.startsWith('/')) return `${API_BASE_URL}${url}`;
  return `${API_BASE_URL}/api/v1/uploads/annonces/${url}`;
}

export async function downloadPdfBail(transactionId: string, customFilename?: string): Promise<void> {
  const { accessToken } = useAuthStore.getState();
  const response = await fetch(`${API_BASE_URL}/api/v1/transactions/${transactionId}/bail/pdf`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    let msg = 'Impossible de télécharger le contrat de bail PDF';
    try {
      const err = await response.json();
      if (err.message) msg = err.message;
    } catch (_) {}
    throw new Error(msg);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = customFilename || `Contrat_Bail_Ecourtier_${transactionId.slice(0, 8)}.pdf`;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
}
