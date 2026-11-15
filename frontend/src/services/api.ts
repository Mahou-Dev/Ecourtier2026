import { useAuthStore } from '../store/useAuthStore';
import { HealthCheckResponse, TransactionResponseDTO } from '../types';
import { resolveApiBaseUrl } from '../lib/env';

export const API_BASE_URL = resolveApiBaseUrl();

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: string | null) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

export async function refreshSessionToken(): Promise<string> {
  const { refreshToken, updateTokens, logout } = useAuthStore.getState();
  if (!refreshToken) {
    logout();
    throw new Error('Session expirée');
  }

  if (isRefreshing) {
    return new Promise<string | null>((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    }).then((token) => {
      if (!token) {
        throw new Error('Rafraîchissement du token échoué');
      }
      return token;
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
    return refreshData.accessToken as string;
  } catch (err) {
    processQueue(err, null);
    isRefreshing = false;
    logout();
    throw err;
  }
}

export async function apiFetch<T>(
  endpoint: string,
  options: RequestInit = {},
  isRetry = false
): Promise<T> {
  const { accessToken, refreshToken, logout } = useAuthStore.getState();

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
    credentials: 'include',
    ...options,
    headers,
  });

  // Never attempt token refresh on 403 Forbidden; logout immediately if account was deactivated
  if (response.status === 403) {
    let errorMessage = 'Accès refusé — Droits insuffisants';
    try {
      const errorJson = await response.json();
      if (errorJson.error) errorMessage = errorJson.error;
      else if (errorJson.message) errorMessage = errorJson.message;
    } catch {
      /* Réponse JSON d'erreur 403 absente ou invalide */
    }
    if (errorMessage.toLowerCase().includes('désactivé')) {
      logout();
    }
    const err = new Error(errorMessage) as Error & { status?: number };
    err.status = 403;
    throw err;
  }

  // Intercept strictly 401 Unauthorized for token refresh (never on 403)
  if (
    response.status === 401 &&
    !isRetry &&
    !endpoint.includes('/auth/connexion') &&
    !endpoint.includes('/auth/rafraichir') &&
    !endpoint.includes('/auth/deconnexion')
  ) {
    if (!refreshToken) {
      logout();
      throw new Error('Session expirée');
    }

    const newToken = await refreshSessionToken();

    // Replay original request ONCE with new access token
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
  }

  if (!response.ok) {
    let errorMessage = `Erreur (${response.status})`;
    try {
      const errorJson = await response.json();
      if (errorJson.erreurs && typeof errorJson.erreurs === 'object') {
        const details = Object.values(errorJson.erreurs).filter(Boolean).join(', ');
        if (details) {
          errorMessage = errorJson.error ? `${errorJson.error} : ${details}` : details;
        } else if (errorJson.error) {
          errorMessage = errorJson.error;
        }
      } else if (errorJson.error) errorMessage = errorJson.error;
      else if (errorJson.message) errorMessage = errorJson.message;
    } catch {
      /* Réponse JSON d'erreur HTTP absente ou invalide */
    }
    const err = new Error(errorMessage) as Error & { status?: number };
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

export async function fetchProtectedBlob(endpoint: string): Promise<{ blobUrl: string; contentType: string }> {
  const { accessToken } = useAuthStore.getState();
  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (!response.ok) {
    let msg = `Impossible de charger le fichier protégé (${response.status})`;
    try {
      const err = await response.json();
      if (err.error) msg = err.error;
      else if (err.message) msg = err.message;
    } catch {
      /* Corps JSON d'erreur blob indisponible */
    }
    throw new Error(msg);
  }

  const rawBlob = await response.blob();
  const headerContentType = response.headers.get('content-type') || rawBlob.type || 'application/octet-stream';
  const typedBlob = rawBlob.type ? rawBlob : new Blob([rawBlob], { type: headerContentType });
  const blobUrl = window.URL.createObjectURL(typedBlob);
  return { blobUrl, contentType: headerContentType };
}

export async function genererPdfBail(transactionId: string): Promise<TransactionResponseDTO> {
  return apiFetch<TransactionResponseDTO>(`/api/v1/transactions/${transactionId}/bail/pdf/generer`, {
    method: 'POST',
  });
}

export async function downloadPdfBail(transactionId: string, customFilename?: string): Promise<void> {
  const { accessToken } = useAuthStore.getState();
  const response = await fetch(`${API_BASE_URL}/api/v1/transactions/${transactionId}/bail/pdf`, {
    headers: accessToken
      ? {
          Authorization: `Bearer ${accessToken}`,
        }
      : {},
  });

  if (!response.ok) {
    let msg =
      response.status === 503
        ? 'Le contrat PDF est en cours de génération ou indisponible temporairement. Veuillez relancer la génération.'
        : 'Impossible de télécharger le contrat de bail PDF';
    try {
      const err = await response.json();
      if (err.error) msg = err.error;
      else if (err.message) msg = err.message;
    } catch {
      /* Corps JSON d'erreur téléchargement PDF indisponible */
    }
    const error = new Error(msg) as Error & { status?: number };
    error.status = response.status;
    throw error;
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
