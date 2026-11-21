import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { UtilisateurDTO, AuthResponseDTO } from '../types';
import { queryClient } from '../lib/queryClient';

import { resolveApiBaseUrl } from '../lib/env';

interface AuthState {
  utilisateur: UtilisateurDTO | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (data: AuthResponseDTO) => void;
  updateTokens: (accessToken: string, refreshToken: string) => void;
  logout: () => Promise<void>;
}

const clearClientSessionData = (preserveUserId?: string) => {
  if (typeof window !== 'undefined' && 'caches' in window) {
    caches.keys().then((names) => {
      names.forEach((name) => {
        caches.delete(name);
      });
    });
  }
  try {
    queryClient.clear();
    if (typeof window !== 'undefined' && window.localStorage) {
      const preserveKey = preserveUserId ? `ecourtier_ia_conversation_${preserveUserId}` : null;
      Object.keys(localStorage).forEach((k) => {
        if (k.startsWith('ecourtier_ia_conversation_') && k !== preserveKey) {
          localStorage.removeItem(k);
        }
      });
    }
  } catch (err) {
    console.error('Erreur lors de la réinitialisation du cache session:', err);
  }
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      utilisateur: null,
      accessToken: null,
      refreshToken: null,
      setAuth: (data) => {
        const currentUserId = get().utilisateur?.id;
        if (currentUserId && currentUserId !== data.utilisateur.id) {
          clearClientSessionData(data.utilisateur.id);
        }
        set({
          utilisateur: data.utilisateur,
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        });
      },
      updateTokens: (accessToken, refreshToken) =>
        set({
          accessToken,
          refreshToken,
        }),
      logout: async () => {
        const { accessToken, refreshToken } = get();
        clearClientSessionData();
        set({
          utilisateur: null,
          accessToken: null,
          refreshToken: null,
        });
        if (typeof window !== 'undefined' && (accessToken || refreshToken)) {
          const headers: Record<string, string> = { 'Content-Type': 'application/json' };
          if (accessToken) {
            headers['Authorization'] = `Bearer ${accessToken}`;
          }
          try {
            await fetch(`${resolveApiBaseUrl()}/api/v1/auth/deconnexion`, {
              method: 'POST',
              credentials: 'include',
              keepalive: true,
              headers,
              body: JSON.stringify(refreshToken ? { refreshToken } : {}),
            });
          } catch {
            // Révocation locale déjà effectuée même en mode hors ligne
          }
        }
      },
    }),
    {
      name: 'ecourtier-auth',
      storage: createJSONStorage(() => sessionStorage), // Persisted strictly in sessionStorage (XSS compliance)
    }
  )
);
