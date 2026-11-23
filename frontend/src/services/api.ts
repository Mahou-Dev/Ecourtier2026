import { useAuthStore } from '../store/useAuthStore';
import { HealthCheckResponse, TransactionResponseDTO } from '../types';
import { resolveApiBaseUrl } from '../lib/env';
import { IA_SESSION_HEADER } from './iaSession';
import fallbackDefaultVisual from '../assets/home/property-fallbacks/default/default-01.webp';

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

export const DEFAULT_REQUEST_TIMEOUT_MS = 45_000;

export interface ApiFetchOptions extends RequestInit {
  /** Délai maximal en millisecondes avant interruption de la requête (<= 0 pour désactiver). */
  timeoutMs?: number;
}

const createTimeoutError = (): Error & { isTimeout?: boolean } => {
  const err = new Error(
    "Délai d'attente dépassé : le serveur met trop de temps à répondre."
  ) as Error & { isTimeout?: boolean };
  err.name = 'TimeoutError';
  err.isTimeout = true;
  return err;
};

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  callerSignal?: AbortSignal | null
): Promise<Response> {
  if (timeoutMs <= 0) {
    return fetch(url, { ...init, signal: callerSignal ?? undefined });
  }

  const requestController = new AbortController();
  let didTimeout = false;
  const onCallerAbort = () => requestController.abort(callerSignal?.reason);

  if (callerSignal) {
    if (callerSignal.aborted) {
      requestController.abort(callerSignal.reason);
    } else {
      callerSignal.addEventListener('abort', onCallerAbort, { once: true });
    }
  }

  const timeoutId = setTimeout(() => {
    didTimeout = true;
    requestController.abort();
  }, timeoutMs);

  try {
    return await fetch(url, {
      ...init,
      signal: requestController.signal,
    });
  } catch (err) {
    if (didTimeout && !callerSignal?.aborted) {
      throw createTimeoutError();
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
    callerSignal?.removeEventListener('abort', onCallerAbort);
  }
}

export async function refreshSessionToken(): Promise<string> {
  const { refreshToken, updateTokens, logout } = useAuthStore.getState();
  if (!refreshToken) {
    logout();
    throw withStatus(new Error('Session expirée'), 401);
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
    const refreshRes = await fetchWithTimeout(
      `${API_BASE_URL}/api/v1/auth/rafraichir`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      },
      DEFAULT_REQUEST_TIMEOUT_MS
    );

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

const isIaEndpoint = (endpoint: string): boolean => endpoint.includes('/api/v1/ia/');

function withStatus(error: unknown, status: number): Error & { status?: number } {
  const err = (error instanceof Error ? error : new Error(String(error))) as Error & { status?: number };
  if (err.status === undefined) err.status = status;
  return err;
}

export async function apiFetch<T>(
  endpoint: string,
  options: ApiFetchOptions = {},
  isRetry = false
): Promise<T> {
  const { accessToken, refreshToken, logout } = useAuthStore.getState();

  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;
  const {
    timeoutMs = DEFAULT_REQUEST_TIMEOUT_MS,
    signal: callerSignal,
    headers: optionHeaders,
    ...fetchOptions
  } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(optionHeaders as Record<string, string> | undefined),
  };

  if (fetchOptions.body instanceof FormData) {
    delete headers['Content-Type'];
  }

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  const response = await fetchWithTimeout(
    url,
    {
      credentials: 'include',
      ...fetchOptions,
      headers,
    },
    timeoutMs,
    callerSignal
  );

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
    // Un 403 sur une conversation IA anonyme (preuve X-IA-Session-Token invalide/expirée) ne doit JAMAIS
    // déconnecter : on ne déconnecte que si un utilisateur est réellement authentifié et que la requête
    // ne porte pas la preuve de session IA anonyme.
    const isIaConversationRequest = Object.keys(headers).some(
      (h) => h.toLowerCase() === IA_SESSION_HEADER.toLowerCase()
    );
    if (accessToken && !isIaConversationRequest && errorMessage.toLowerCase().includes('désactivé')) {
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
      // Visiteur anonyme sur un endpoint IA : rien à déconnecter (et ne pas purger la session IA anonyme).
      if (!isIaEndpoint(endpoint)) {
        logout();
      }
      throw withStatus(new Error('Session expirée'), 401);
    }

    let newToken: string;
    try {
      newToken = await refreshSessionToken();
    } catch (refreshError) {
      throw withStatus(refreshError, 401);
    }

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
  if (!url) return fallbackDefaultVisual;
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
