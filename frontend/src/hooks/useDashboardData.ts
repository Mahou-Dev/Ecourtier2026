import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';
import { messagerieService } from '../services/messagerie.service';
import type {
  AnnonceDTO,
  PageResponse,
  RendezVousResponseDTO,
  TransactionResponseDTO,
} from '../types';

export const MY_ANNONCES_PAGE_SIZE = 10;

export type AnnonceModeFilter = 'LOCATION' | 'VENTE';
export type AnnonceVerificationFilter = 'EN_ATTENTE' | 'VERIFIEE' | 'REJETEE';

export interface MyAnnoncesFilters {
  q?: string;
  modeTransaction?: AnnonceModeFilter;
  statutVerification?: AnnonceVerificationFilter;
}

export interface MyAnnoncesParams extends MyAnnoncesFilters {
  /** Index de page 0-based tel qu'attendu par le backend. */
  page: number;
  size: number;
}

/**
 * Utilise uniquement les paramètres déjà pris en charge par GET /api/v1/annonces :
 * mes_annonces, page, size, q, modeTransaction, statutVerification.
 */
export function buildMyAnnoncesUrl(params: MyAnnoncesParams): string {
  const search = new URLSearchParams();
  search.set('mes_annonces', 'true');
  search.set('page', String(params.page));
  search.set('size', String(params.size));
  if (params.q?.trim()) search.set('q', params.q.trim());
  if (params.modeTransaction) search.set('modeTransaction', params.modeTransaction);
  if (params.statutVerification) search.set('statutVerification', params.statutVerification);
  return `/api/v1/annonces?${search.toString()}`;
}

/** Liste paginée des annonces de l'utilisateur (jamais chargée pour un LOCATAIRE). */
export function useMyAnnonces(params: MyAnnoncesParams, enabled: boolean) {
  return useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['my-annonces', 'list', params],
    queryFn: () => apiFetch<PageResponse<AnnonceDTO>>(buildMyAnnoncesUrl(params)),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/**
 * Compteur léger (size=1) : on ne lit que `totalElements`.
 * Retourne `null` si le backend ne le fournit pas, afin de ne jamais afficher de chiffre inventé.
 */
export function useMyAnnoncesCount(filters: MyAnnoncesFilters, enabled: boolean) {
  return useQuery<number | null>({
    queryKey: ['my-annonces', 'count', filters],
    queryFn: async () => {
      const page = await apiFetch<PageResponse<AnnonceDTO>>(
        buildMyAnnoncesUrl({ ...filters, page: 0, size: 1 })
      );
      return typeof page?.totalElements === 'number' ? page.totalElements : null;
    },
    enabled,
    staleTime: 30_000,
  });
}

export function useMyVisites(enabled: boolean) {
  return useQuery<RendezVousResponseDTO[]>({
    queryKey: ['my-visites'],
    queryFn: async () => {
      const data = await apiFetch<RendezVousResponseDTO[]>('/api/v1/rendez-vous/mes-rendez-vous');
      return Array.isArray(data) ? data : [];
    },
    enabled,
  });
}

export function useMyTransactions(enabled: boolean) {
  return useQuery<TransactionResponseDTO[]>({
    queryKey: ['my-transactions'],
    queryFn: async () => {
      const data = await apiFetch<TransactionResponseDTO[]>('/api/v1/transactions/mes-transactions');
      return Array.isArray(data) ? data : [];
    },
    enabled,
  });
}

export function useUnreadMessagesCount(enabled: boolean) {
  return useQuery({
    queryKey: ['messages-unread-count'],
    queryFn: () => messagerieService.compterMessagesNonLus(),
    enabled,
    refetchInterval: 10000,
  });
}
