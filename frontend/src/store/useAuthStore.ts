import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { UtilisateurDTO, AuthResponseDTO } from '../types';

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
      logout: () =>
        set({
          utilisateur: null,
          accessToken: null,
          refreshToken: null,
        }),
    }),
    {
      name: 'ecourtier-auth',
      storage: createJSONStorage(() => sessionStorage), // Persisted strictly in sessionStorage (XSS compliance)
    }
  )
);
