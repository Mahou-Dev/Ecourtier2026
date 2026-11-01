import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';
import { ReferencesResponse } from '../types';

export const useReferences = () => {
  return useQuery<ReferencesResponse>({
    queryKey: ['references'],
    queryFn: () => apiFetch<ReferencesResponse>('/api/v1/references'),
    staleTime: 1000 * 60 * 60, // 1 heure de cache
    gcTime: 1000 * 60 * 60 * 24, // 24 heures
  });
};

export const useQuartiers = () => {
  return useQuery<string[]>({
    queryKey: ['annonces', 'quartiers'],
    queryFn: () => apiFetch<string[]>('/api/v1/annonces/quartiers'),
    staleTime: 1000 * 60 * 15, // 15 minutes
  });
};
