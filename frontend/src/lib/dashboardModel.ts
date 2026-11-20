import type { BadgeProps } from '../components/ui/Badge';
import type {
  AnnonceDTO,
  RendezVousResponseDTO,
  StatutBail,
  TransactionResponseDTO,
  UtilisateurDTO,
} from '../types';

/**
 * Modèle pur du dashboard métier : navigation par rôle, regroupement des visites,
 * lecture honnête des montants de transaction. Aucune dépendance React ni réseau.
 */

export type DashboardRole = UtilisateurDTO['role'];
export type DashboardTabId = 'annonces' | 'visites' | 'transactions' | 'messages' | 'sms';

const TAB_ALIASES: Record<string, DashboardTabId> = {
  annonces: 'annonces',
  visites: 'visites',
  transactions: 'transactions',
  // Ancien paramètre conservé pour les liens et favoris existants.
  sequestres: 'transactions',
  messages: 'messages',
  sms: 'sms',
};

export function canManageListings(role?: string | null): boolean {
  return role === 'PROPRIETAIRE' || role === 'AGENT' || role === 'ADMIN';
}

export function canPublishListings(role?: string | null): boolean {
  return role === 'PROPRIETAIRE' || role === 'AGENT';
}

export function getAllowedTabs(role?: string | null): DashboardTabId[] {
  return canManageListings(role)
    ? ['annonces', 'visites', 'transactions', 'messages', 'sms']
    : ['visites', 'transactions', 'messages', 'sms'];
}

export function getDefaultTab(role?: string | null): DashboardTabId {
  return canManageListings(role) ? 'annonces' : 'visites';
}

/**
 * Résout le paramètre `tab` en onglet autorisé pour le rôle.
 * `needsNormalization` est vrai si l'URL doit être réécrite (alias ou valeur invalide).
 */
export function resolveDashboardTab(
  raw: string | null,
  role?: string | null
): { tab: DashboardTabId; needsNormalization: boolean } {
  const allowed = getAllowedTabs(role);
  const parsed = raw ? TAB_ALIASES[raw.toLowerCase()] : undefined;
  if (parsed && allowed.includes(parsed)) {
    return { tab: parsed, needsNormalization: raw !== parsed };
  }
  const fallback = getDefaultTab(role);
  return { tab: fallback, needsNormalization: raw !== null };
}

/* -------------------------------------------------------------------------- */
/* Formatage                                                                  */
/* -------------------------------------------------------------------------- */

export function formatFcfa(value: number | null | undefined): string | null {
  return typeof value === 'number' && Number.isFinite(value)
    ? `${value.toLocaleString('fr-FR')} FCFA`
    : null;
}

export function formatDateTimeFr(iso?: string | null): string {
  if (!iso) return 'Date non renseignée';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'Date non renseignée';
  return date.toLocaleString('fr-FR', { dateStyle: 'full', timeStyle: 'short' });
}

export function formatDateFr(iso?: string | null): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString('fr-FR', { dateStyle: 'long' });
}

export function pluralize(count: number, singular: string, plural?: string): string {
  return count > 1 ? plural ?? `${singular}s` : singular;
}

/* -------------------------------------------------------------------------- */
/* Annonces                                                                   */
/* -------------------------------------------------------------------------- */

type BadgeVariant = NonNullable<BadgeProps['variant']>;

export function getPublicationMeta(statut: AnnonceDTO['statutPublication']): { label: string; variant: BadgeVariant } {
  switch (statut) {
    case 'DISPONIBLE':
      return { label: 'Disponible', variant: 'success' };
    case 'RESERVEE':
      return { label: 'Réservée', variant: 'info' };
    case 'LOUEE':
      return { label: 'Louée', variant: 'neutral' };
    case 'VENDUE':
      return { label: 'Vendue', variant: 'neutral' };
    case 'SUSPENDUE':
      return { label: 'Suspendue', variant: 'rose' };
    default:
      return { label: 'Statut inconnu', variant: 'neutral' };
  }
}

export function getVerificationMeta(statut: AnnonceDTO['statutVerification']): { label: string; variant: BadgeVariant } {
  switch (statut) {
    case 'VERIFIEE':
      return { label: 'Cadastre vérifié', variant: 'success' };
    case 'EN_ATTENTE':
      return { label: 'Vérification en cours', variant: 'warning' };
    case 'REJETEE':
      return { label: 'Vérification rejetée', variant: 'rose' };
    default:
      return { label: 'Vérification inconnue', variant: 'neutral' };
  }
}

export function formatRoomCount(count?: number | null): string | null {
  return typeof count === 'number' && count > 0 ? `${count} ${count === 1 ? 'pièce' : 'pièces'}` : null;
}

export function formatSurface(superficie?: number | null): string | null {
  return typeof superficie === 'number' && superficie > 0 ? `${superficie.toLocaleString('fr-FR')} m²` : null;
}

/* -------------------------------------------------------------------------- */
/* Visites                                                                    */
/* -------------------------------------------------------------------------- */

type VisitActor = Pick<UtilisateurDTO, 'id' | 'email' | 'role'>;

export function isActiveVisit(rdv: RendezVousResponseDTO): boolean {
  return rdv.statut === 'DEMANDE' || rdv.statut === 'CONFIRME';
}

/** Vrai si l'utilisateur est le demandeur de la visite. */
export function isOwnVisit(rdv: RendezVousResponseDTO, user: VisitActor): boolean {
  if (rdv.demandeurEmail && user.email && rdv.demandeurEmail === user.email) return true;
  return Boolean(rdv.demandeurId && user.id && rdv.demandeurId === user.id);
}

/**
 * Règle existante conservée : seuls les rôles de gestion peuvent confirmer / clôturer
 * une visite qui ne vient pas d'eux-mêmes.
 */
export function canManageVisit(rdv: RendezVousResponseDTO, user: VisitActor): boolean {
  return canManageListings(user.role) && !isOwnVisit(rdv, user);
}

function visitTime(rdv: RendezVousResponseDTO): number {
  const time = new Date(rdv.dateHeure).getTime();
  return Number.isNaN(time) ? 0 : time;
}

const byDateAsc = (a: RendezVousResponseDTO, b: RendezVousResponseDTO) => visitTime(a) - visitTime(b);
const byDateDesc = (a: RendezVousResponseDTO, b: RendezVousResponseDTO) => visitTime(b) - visitTime(a);

export function isVisitDatePassed(rdv: RendezVousResponseDTO, now: number = Date.now()): boolean {
  const time = visitTime(rdv);
  return time > 0 && time < now;
}

export interface VisitGroup {
  id: string;
  title: string;
  description: string;
  items: RendezVousResponseDTO[];
  /** Les éléments de ce groupe sont l'historique (affichage atténué). */
  isHistory?: boolean;
}

/** Répartition exhaustive : chaque visite appartient à un seul groupe. */
export function buildVisitGroups(visits: RendezVousResponseDTO[], user: VisitActor): VisitGroup[] {
  const staff = canManageListings(user.role);
  const history = visits.filter((v) => !isActiveVisit(v)).sort(byDateDesc);
  const active = visits.filter(isActiveVisit);

  if (!staff) {
    return [
      {
        id: 'a-venir',
        title: 'Visites confirmées',
        description: 'Rendez-vous validés par le propriétaire ou l’agent.',
        items: active.filter((v) => v.statut === 'CONFIRME').sort(byDateAsc),
      },
      {
        id: 'en-attente',
        title: 'Demandes en attente de confirmation',
        description: 'Votre demande a été transmise, la réponse est attendue.',
        items: active.filter((v) => v.statut === 'DEMANDE').sort(byDateAsc),
      },
      {
        id: 'historique',
        title: 'Historique des visites',
        description: 'Visites effectuées ou annulées.',
        items: history,
        isHistory: true,
      },
    ];
  }

  const managed = active.filter((v) => canManageVisit(v, user));
  const own = active.filter((v) => !canManageVisit(v, user));
  return [
    {
      id: 'a-traiter',
      title: 'Demandes à traiter',
      description: 'Demandes de visite sur vos biens en attente de réponse.',
      items: managed.filter((v) => v.statut === 'DEMANDE').sort(byDateAsc),
    },
    {
      id: 'confirmees',
      title: 'Visites confirmées sur vos biens',
      description: 'Rendez-vous validés, à marquer comme effectués après la visite.',
      items: managed.filter((v) => v.statut === 'CONFIRME').sort(byDateAsc),
    },
    {
      id: 'mes-demandes',
      title: 'Mes demandes de visite',
      description: 'Visites que vous avez demandées sur les biens d’autres annonceurs.',
      items: own.sort(byDateAsc),
    },
    {
      id: 'historique',
      title: 'Historique des visites',
      description: 'Visites effectuées ou annulées.',
      items: history,
      isHistory: true,
    },
  ];
}

export function countUpcomingConfirmedVisits(visits: RendezVousResponseDTO[], now: number = Date.now()): number {
  return visits.filter((v) => v.statut === 'CONFIRME' && visitTime(v) >= now).length;
}

export function countPendingRequestsToProcess(visits: RendezVousResponseDTO[], user: VisitActor): number {
  return visits.filter((v) => v.statut === 'DEMANDE' && canManageVisit(v, user)).length;
}

export function countOwnPendingRequests(visits: RendezVousResponseDTO[], user: VisitActor): number {
  return visits.filter((v) => v.statut === 'DEMANDE' && isOwnVisit(v, user)).length;
}

/* -------------------------------------------------------------------------- */
/* Transactions                                                               */
/* -------------------------------------------------------------------------- */

export type TransactionSide = 'acquereur' | 'proprietaire' | 'autre';

export function isTransactionOpen(tx: TransactionResponseDTO): boolean {
  if (typeof tx.dossierActif === 'boolean') return tx.dossierActif;
  return tx.statutSequestre === 'INITIALISE' || tx.statutSequestre === 'BLOQUE';
}

export function getTransactionSide(tx: TransactionResponseDTO, user: Pick<UtilisateurDTO, 'id'>): TransactionSide {
  if (tx.locataireId && tx.locataireId === user.id) return 'acquereur';
  if (tx.proprietaireId && tx.proprietaireId === user.id) return 'proprietaire';
  return 'autre';
}

export interface AmountLine {
  label: string;
  value: string;
}

/**
 * Total du séquestre : valeur backend si fournie, sinon somme uniquement lorsque
 * toutes les composantes sont réellement fournies. Jamais de frais inventés.
 */
export function getTransactionTotal(tx: TransactionResponseDTO): number | null {
  if (typeof tx.montantTotalSequestre === 'number' && Number.isFinite(tx.montantTotalSequestre)) {
    return tx.montantTotalSequestre;
  }
  if (typeof tx.montantCaution === 'number' && typeof tx.fraisDossier === 'number') {
    return tx.montantCaution + (typeof tx.montantPremierLoyer === 'number' ? tx.montantPremierLoyer : 0) + tx.fraisDossier;
  }
  return null;
}

export function buildTransactionAmountLines(tx: TransactionResponseDTO): AmountLine[] {
  const lines: AmountLine[] = [];
  const caution = formatFcfa(tx.montantCaution);
  if (caution) {
    const isVente = tx.modeTransaction === 'VENTE';
    const percent =
      isVente && typeof tx.acomptePourcentage === 'number' ? ` (${tx.acomptePourcentage} % du prix)` : '';
    lines.push({ label: isVente ? `Acompte de réservation${percent}` : 'Caution', value: caution });
  }
  if (tx.modeTransaction !== 'VENTE' && typeof tx.montantPremierLoyer === 'number' && tx.montantPremierLoyer > 0) {
    const loyer = formatFcfa(tx.montantPremierLoyer);
    if (loyer) lines.push({ label: '1er loyer', value: loyer });
  }
  const frais = formatFcfa(tx.fraisDossier);
  lines.push({ label: 'Frais de dossier', value: frais ?? 'Non communiqués' });
  return lines;
}

const CLOSED_SEQUESTRE = new Set(['REMBOURSE', 'ANNULE', 'EXPIRE']);

export function getTransactionNextStep(
  tx: TransactionResponseDTO,
  user: Pick<UtilisateurDTO, 'id'>
): { text: string; tone: 'action' | 'waiting' | 'done' | 'closed' } {
  const side = getTransactionSide(tx, user);
  const isVente = tx.modeTransaction === 'VENTE';
  const doc = isVente ? 'le récapitulatif de réservation' : 'le bail';

  if (CLOSED_SEQUESTRE.has(tx.statutSequestre) || tx.dossierActif === false) {
    return { text: 'Dossier clos : aucune action requise.', tone: 'closed' };
  }
  if (tx.statutSequestre === 'LIBERE') {
    return { text: 'Fonds libérés : le dossier est finalisé.', tone: 'done' };
  }
  if (tx.statutSequestre === 'INITIALISE') {
    return side === 'proprietaire'
      ? { text: `En attente du règlement du séquestre par ${isVente ? 'l’acquéreur' : 'le locataire'}.`, tone: 'waiting' }
      : { text: 'Prochaine étape : régler le séquestre pour débloquer la signature.', tone: 'action' };
  }

  // Séquestre bloqué : la suite dépend des signatures.
  const bail: StatutBail = tx.statutBail;
  if (bail === 'SIGNE_COMPLET') {
    return { text: `Signatures complètes pour ${doc}.`, tone: 'done' };
  }
  const signedByTenant = bail === 'SIGNE_LOCATAIRE';
  const signedByOwner = bail === 'SIGNE_PROPRIETAIRE';
  if (side === 'acquereur') {
    return signedByTenant
      ? { text: 'Votre signature est enregistrée : en attente de l’autre partie.', tone: 'waiting' }
      : { text: `Prochaine étape : signer ${doc}.`, tone: 'action' };
  }
  if (side === 'proprietaire') {
    return signedByOwner
      ? { text: 'Votre signature est enregistrée : en attente de l’autre partie.', tone: 'waiting' }
      : { text: `Prochaine étape : signer ${doc}.`, tone: 'action' };
  }
  const count = bail === 'NON_SIGNE' ? 0 : 1;
  return { text: `Signatures en cours (${count}/2).`, tone: 'waiting' };
}

export function getSequestreMeta(statut: TransactionResponseDTO['statutSequestre']): { label: string; variant: BadgeVariant } {
  switch (statut) {
    case 'INITIALISE':
      return { label: 'Séquestre : à consigner', variant: 'warning' };
    case 'BLOQUE':
      return { label: 'Séquestre : bloqué', variant: 'info' };
    case 'LIBERE':
      return { label: 'Séquestre : libéré', variant: 'success' };
    case 'REMBOURSE':
      return { label: 'Séquestre : remboursé', variant: 'neutral' };
    case 'ANNULE':
      return { label: 'Transaction : annulée', variant: 'neutral' };
    case 'EXPIRE':
      return { label: 'Transaction : expirée', variant: 'rose' };
    default:
      return { label: 'Séquestre : statut inconnu', variant: 'neutral' };
  }
}

export function getBailMeta(statut: StatutBail, isVente: boolean): { label: string; variant: BadgeVariant } {
  const doc = isVente ? 'Réservation' : 'Bail';
  switch (statut) {
    case 'NON_SIGNE':
      return { label: `${doc} : non signé`, variant: 'neutral' };
    case 'SIGNE_LOCATAIRE':
    case 'SIGNE_PROPRIETAIRE':
      return { label: `${doc} : 1/2 signature`, variant: 'info' };
    case 'SIGNE_COMPLET':
      return { label: `${doc} : signé complet`, variant: 'success' };
    default:
      return { label: `${doc} : statut inconnu`, variant: 'neutral' };
  }
}

/* -------------------------------------------------------------------------- */
/* Pagination                                                                 */
/* -------------------------------------------------------------------------- */

export function parsePageParam(raw: string | null): number {
  const parsed = Number.parseInt(raw ?? '', 10);
  return Number.isFinite(parsed) && parsed >= 1 ? parsed : 1;
}
