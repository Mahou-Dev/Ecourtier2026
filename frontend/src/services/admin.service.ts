import { apiFetch } from './api';
import {
  AdminCreateUtilisateurInput,
  AdminCreateUtilisateurResponse,
  AdminStatistiquesDTO,
  ChangerMotDePasseInput,
  PageResponse,
  UtilisateurDTO,
} from '../types';

export const adminService = {
  getStatistiques: async (periode: string = '30j'): Promise<AdminStatistiquesDTO> => {
    return apiFetch<AdminStatistiquesDTO>(`/api/v1/admin/statistiques?periode=${encodeURIComponent(periode)}`);
  },

  getUtilisateurs: async (params: {
    page?: number;
    size?: number;
    role?: string;
    actif?: boolean;
    recherche?: string;
  }): Promise<PageResponse<UtilisateurDTO>> => {
    const query = new URLSearchParams();
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 15));
    if (params.role && params.role !== 'TOUS') {
      query.set('role', params.role);
    }
    if (params.actif !== undefined) {
      query.set('actif', String(params.actif));
    }
    if (params.recherche && params.recherche.trim()) {
      query.set('recherche', params.recherche.trim());
    }
    return apiFetch<PageResponse<UtilisateurDTO>>(`/api/v1/admin/utilisateurs?${query.toString()}`);
  },

  creerUtilisateur: async (payload: AdminCreateUtilisateurInput): Promise<AdminCreateUtilisateurResponse> => {
    return apiFetch<AdminCreateUtilisateurResponse>('/api/v1/admin/utilisateurs', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  changerRoleUtilisateur: async (
    id: string,
    role: 'ADMIN' | 'AGENT' | 'PROPRIETAIRE' | 'LOCATAIRE'
  ): Promise<UtilisateurDTO> => {
    return apiFetch<UtilisateurDTO>(`/api/v1/admin/utilisateurs/${id}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    });
  },

  changerStatutUtilisateur: async (id: string, actif: boolean): Promise<UtilisateurDTO> => {
    return apiFetch<UtilisateurDTO>(`/api/v1/admin/utilisateurs/${id}/statut`, {
      method: 'PUT',
      body: JSON.stringify({ actif }),
    });
  },

  changerMonMotDePasse: async (payload: ChangerMotDePasseInput): Promise<{ message: string }> => {
    return apiFetch<{ message: string }>('/api/v1/utilisateurs/moi/mot-de-passe', {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
};
