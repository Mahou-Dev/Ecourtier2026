import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';
import { useAuthStore } from '../store/useAuthStore';
import { UtilisateurDTO } from '../types';
import { isKnownRole } from '../lib/roleLabels';

function isValidUserProfile(data: unknown): data is UtilisateurDTO {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return false;
  }
  const record = data as Partial<UtilisateurDTO>;
  return (
    typeof record.id === 'string' &&
    record.id.length > 0 &&
    typeof record.email === 'string' &&
    record.email.length > 0 &&
    typeof record.nom === 'string' &&
    typeof record.prenom === 'string' &&
    isKnownRole(record.role)
  );
}

function hasProfileChanged(current: UtilisateurDTO, fresh: UtilisateurDTO): boolean {
  return (
    current.nom !== fresh.nom ||
    current.prenom !== fresh.prenom ||
    current.email !== fresh.email ||
    current.telephone !== fresh.telephone ||
    current.role !== fresh.role ||
    current.actif !== fresh.actif ||
    current.dateCreation !== fresh.dateCreation ||
    current.photoProfilUrl !== fresh.photoProfilUrl
  );
}

/**
 * Synchronise sans bruit le profil courant depuis GET /api/v1/utilisateurs/moi
 * sans déclencher de déconnexion sur erreur réseau ou réponse factice incomplète.
 */
export function useCurrentProfile() {
  const utilisateur = useAuthStore((s) => s.utilisateur);
  const accessToken = useAuthStore((s) => s.accessToken);
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const setAuth = useAuthStore((s) => s.setAuth);

  const query = useQuery<UtilisateurDTO | null>({
    queryKey: ['current-user-profile', utilisateur?.id],
    queryFn: async () => {
      try {
        const res = await apiFetch<unknown>('/api/v1/utilisateurs/moi');
        return isValidUserProfile(res) ? res : null;
      } catch (err) {
        console.warn('[useCurrentProfile] Synchronisation profil différée:', err);
        return null;
      }
    },
    enabled: Boolean(utilisateur?.id && accessToken),
    staleTime: 60_000,
    retry: false,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    const fresh = query.data;
    if (!fresh || !utilisateur || !accessToken || !refreshToken) {
      return;
    }
    if (fresh.id === utilisateur.id && hasProfileChanged(utilisateur, fresh)) {
      setAuth({
        accessToken,
        refreshToken,
        tokenType: 'Bearer',
        expiresIn: 0,
        utilisateur: fresh,
      });
    }
  }, [query.data, utilisateur, accessToken, refreshToken, setAuth]);

  return query;
}
