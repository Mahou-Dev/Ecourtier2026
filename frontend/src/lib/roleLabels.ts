import { BadgeProps } from '../components/ui/Badge';
import { UtilisateurDTO } from '../types';

export type UserRole = UtilisateurDTO['role'];

const KNOWN_ROLES: readonly UserRole[] = ['ADMIN', 'AGENT', 'PROPRIETAIRE', 'LOCATAIRE'];

export function isKnownRole(role?: string | null): role is UserRole {
  return typeof role === 'string' && KNOWN_ROLES.includes(role as UserRole);
}

/**
 * Normalise les codes rôles backend en libellés français lisibles côté UI.
 */
export function getRoleLabel(role?: string | null): string {
  switch (role) {
    case 'LOCATAIRE':
      return 'Locataire / Acheteur';
    case 'PROPRIETAIRE':
      return 'Propriétaire';
    case 'AGENT':
      return 'Agent immobilier';
    case 'ADMIN':
      return 'Administrateur';
    default:
      return 'Utilisateur';
  }
}

export function getRoleBadgeVariant(role?: string | null): NonNullable<BadgeProps['variant']> {
  switch (role) {
    case 'ADMIN':
      return 'terracotta';
    case 'AGENT':
      return 'ocher';
    case 'PROPRIETAIRE':
      return 'sahel';
    case 'LOCATAIRE':
      return 'sand';
    default:
      return 'neutral';
  }
}

export function getRoleDescription(role?: string | null): string {
  switch (role) {
    case 'LOCATAIRE':
      return 'Recherche de logements vérifiés, suivi des visites planifiées, favoris et signature de bail sous séquestre.';
    case 'PROPRIETAIRE':
      return 'Publication et suivi de vos biens immobiliers, gestion des rendez-vous, justificatifs et baux numériques.';
    case 'AGENT':
      return 'Gestion de portefeuille immobilier agréé, qualification des visites et accompagnement transactionnel sécurisé.';
    case 'ADMIN':
      return 'Supervision globale de la plateforme, modération documentaire, contrôle des séquestres et gestion des comptes.';
    default:
      return 'Espace personnel sécurisé sur E-Courtier Sénégal.';
  }
}

export function getRoleHomeRoute(role?: string | null): string {
  switch (role) {
    case 'ADMIN':
      return '/admin/dashboard';
    case 'LOCATAIRE':
      return '/recherche';
    case 'PROPRIETAIRE':
    case 'AGENT':
      return '/dashboard';
    default:
      return '/';
  }
}

/**
 * Résout la destination post-authentification en validant `state.from`
 * pour éviter les redirections externes, boucles vers `/connexion` ou accès admin interdits.
 */
export function resolvePostAuthDestination(role: UserRole, from?: string | null): string {
  if (
    typeof from === 'string' &&
    from.startsWith('/') &&
    !from.startsWith('//') &&
    !from.startsWith('/connexion') &&
    !from.startsWith('/inscription')
  ) {
    if (from.startsWith('/admin') && role !== 'ADMIN') {
      return getRoleHomeRoute(role);
    }
    return from;
  }
  return getRoleHomeRoute(role);
}
