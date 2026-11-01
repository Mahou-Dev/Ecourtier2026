import { apiFetch } from './api';
import { AdminStatistiquesDTO } from '../types';

export const adminService = {
  getStatistiques: async (periode: string = '30j'): Promise<AdminStatistiquesDTO> => {
    return apiFetch<AdminStatistiquesDTO>(`/api/v1/admin/statistiques?periode=${encodeURIComponent(periode)}`);
  },
};
