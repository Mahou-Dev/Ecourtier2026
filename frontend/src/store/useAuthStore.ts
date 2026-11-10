import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { UtilisateurDTO, AuthResponseDTO } from '../types';
import { queryClient } from '../lib/queryClient';

interface AuthState {
  utilisateur: UtilisateurDTO | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (data: AuthResponseDTO) => void;
  updateTokens: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      utilisateur: null,
      accessToken: null,
      refreshToken: null,
      setAuth: (data) =>
        set({
          utilisateur: data.utilisateur,
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        }),
      updateTokens: (accessToken, refreshToken) =>
        set({
          accessToken,
          refreshToken,
        }),
      logout: () => {
        if (typeof window !== 'undefined' && 'caches' in window) {
          caches.keys().then((names) => {
            names.forEach((name) => {
              caches.delete(name);
            });
          });
        }
        try {
          queryClient.clear();
        } catch (err) {
          console.error('Erreur lors de la réinitialisation du cache QueryClient:', err);
        }
        set({
          utilisateur: null,
          accessToken: null,
          refreshToken: null,
        });
      },
    }),
    {
      name: 'ecourtier-auth',
      storage: createJSONStorage(() => sessionStorage), // Persisted strictly in sessionStorage (XSS compliance)
    }
  )
);
