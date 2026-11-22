/**
 * Mapping erreur → message utilisateur (FR) pour le widget Samba IA, formatage FCFA et
 * libellés du mode secours. Module pur (sans React ni DOM) : testable sans navigateur.
 * Aucun détail technique ou de sécurité interne n'est exposé à l'utilisateur.
 */

export const IA_MESSAGE_MAX_LENGTH = 1000;

export type IaErrorKind =
  | 'network'
  | 'security-policy'
  | 'too-long'
  | 'bad-request'
  | 'unauthorized'
  | 'conversation-invalid'
  | 'forbidden'
  | 'conversation-not-found'
  | 'rate-limit'
  | 'unavailable';

export interface IaErrorContext {
  /** La requête en échec portait sur une conversation existante (POST messages / GET conversation). */
  conversationScoped: boolean;
}

export interface IaErrorInfo {
  kind: IaErrorKind;
  status?: number;
  message: string;
  /** La conversation locale doit être abandonnée (id + token purgés, nouvelle conversation). */
  resetConversation: boolean;
}

export const IA_ERROR_MESSAGES = {
  network:
    'Connexion impossible : vérifiez votre connexion internet puis réessayez dans quelques instants.',
  securityPolicy:
    "Je ne peux pas traiter cette demande. Je ne propose que des annonces réelles, vérifiées et disponibles du catalogue E-Courtier.",
  tooLong: 'Votre message dépasse 1 000 caractères. Raccourcissez-le puis renvoyez-le.',
  badRequest: "Votre message n'a pas pu être traité. Reformulez votre demande puis réessayez.",
  unauthorized: 'Session expirée, reconnectez-vous pour continuer.',
  conversationInvalid:
    "Votre session de conversation a expiré ou n'est plus valide. Une nouvelle conversation a été démarrée : renvoyez votre message.",
  forbidden: 'Accès refusé.',
  conversationNotFound:
    'Cette conversation est introuvable. Une nouvelle conversation a été démarrée : renvoyez votre message.',
  rateLimit:
    'Limite de requêtes IA atteinte. Veuillez patienter quelques instants avant de renvoyer un message.',
  unavailable:
    "L'assistant IA est momentanément indisponible. Vous pouvez utiliser la recherche classique.",
} as const;

const normalize = (text: string): string =>
  text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

const getStatus = (err: unknown): number | undefined => {
  const status = (err as { status?: unknown } | null | undefined)?.status;
  return typeof status === 'number' ? status : undefined;
};

const getMessage = (err: unknown): string =>
  err instanceof Error && typeof err.message === 'string' ? err.message : '';

const isOffline = (): boolean => typeof navigator !== 'undefined' && navigator.onLine === false;

/** Erreur réseau (fetch rejeté : TypeError / hors-ligne) — pas une réponse HTTP. */
export const isNetworkError = (err: unknown): boolean => {
  if (getStatus(err) !== undefined) return false;
  if (isOffline()) return true;
  if (err instanceof TypeError) return true;
  const name = (err as { name?: unknown } | null | undefined)?.name;
  return name === 'TypeError' || name === 'NetworkError';
};

export const mapIaError = (err: unknown, context: IaErrorContext): IaErrorInfo => {
  const status = getStatus(err);
  const rawMessage = getMessage(err);
  const msg = normalize(rawMessage);

  if (status === undefined) {
    if (isNetworkError(err)) {
      return { kind: 'network', message: IA_ERROR_MESSAGES.network, resetConversation: false };
    }
    return { kind: 'unavailable', message: IA_ERROR_MESSAGES.unavailable, resetConversation: false };
  }

  switch (status) {
    case 400:
      if (msg.includes('politique de securite')) {
        return { kind: 'security-policy', status, message: IA_ERROR_MESSAGES.securityPolicy, resetConversation: false };
      }
      if (/(trop long|depasse|maximum|1000|500 caract|taille)/.test(msg)) {
        return { kind: 'too-long', status, message: IA_ERROR_MESSAGES.tooLong, resetConversation: false };
      }
      return { kind: 'bad-request', status, message: IA_ERROR_MESSAGES.badRequest, resetConversation: false };
    case 401:
      return { kind: 'unauthorized', status, message: IA_ERROR_MESSAGES.unauthorized, resetConversation: false };
    case 403:
      if (context.conversationScoped) {
        return {
          kind: 'conversation-invalid',
          status,
          message: IA_ERROR_MESSAGES.conversationInvalid,
          resetConversation: true,
        };
      }
      return { kind: 'forbidden', status, message: IA_ERROR_MESSAGES.forbidden, resetConversation: false };
    case 404:
      if (context.conversationScoped) {
        return {
          kind: 'conversation-not-found',
          status,
          message: IA_ERROR_MESSAGES.conversationNotFound,
          resetConversation: true,
        };
      }
      return { kind: 'unavailable', status, message: IA_ERROR_MESSAGES.unavailable, resetConversation: false };
    case 429:
      return {
        kind: 'rate-limit',
        status,
        message: rawMessage && !/^erreur \(\d+\)$/i.test(rawMessage) ? rawMessage : IA_ERROR_MESSAGES.rateLimit,
        resetConversation: false,
      };
    default:
      return { kind: 'unavailable', status, message: IA_ERROR_MESSAGES.unavailable, resetConversation: false };
  }
};

/** Statuts pour lesquels une restauration d'historique (GET) prouve que la conversation est abandonnée. */
export const isAbandonedConversationStatus = (status: number | undefined): boolean =>
  status === 400 || status === 401 || status === 403 || status === 404;

// --- Formatage FCFA -------------------------------------------------------------------------

/**
 * « 45 000 000 FCFA » (espaces insécables U+00A0 comme séparateurs de milliers).
 * Affichage uniquement : ne jamais utiliser pour construire une valeur envoyée au backend.
 */
export const formatFcfa = (amount: number | null | undefined): string => {
  if (typeof amount !== 'number' || !Number.isFinite(amount)) return 'Prix non communiqué';
  const rounded = Math.round(amount);
  const sign = rounded < 0 ? '-' : '';
  const digits = Math.abs(rounded).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0');
  return `${sign}${digits}\u00a0FCFA`;
};

// --- Mode secours ---------------------------------------------------------------------------

const MOTIF_SECOURS_LABELS: Record<string, string> = {
  CLE_ABSENTE: "l'assistant avancé n'est pas disponible pour le moment",
  IA_DESACTIVEE: "l'assistant avancé est désactivé",
  TIMEOUT: "l'assistant avancé a mis trop de temps à répondre",
  QUOTA: "la capacité de l'assistant avancé est momentanément atteinte",
  ERREUR_HTTP: "l'assistant avancé est momentanément indisponible",
  REPONSE_INVALIDE: "l'assistant avancé est momentanément indisponible",
  FOURNISSEUR_NON_AUTORISE: "l'assistant avancé n'est pas disponible pour le moment",
};

/** Libellé lisible, non technique, du motif de bascule en mode secours (pour title/tooltip). */
export const describeMotifSecours = (motif: unknown): string => {
  const base = 'Réponse générée par le moteur local';
  if (typeof motif !== 'string' || !motif) return base;
  const label = MOTIF_SECOURS_LABELS[motif.toUpperCase()];
  return label ? `${base} : ${label}.` : `${base}.`;
};

// --- Historique -----------------------------------------------------------------------------

/**
 * Conserve l'ordre serveur. Si TOUS les messages portent un `ordre` numérique, tri stable par
 * `ordre` croissant (l'ordre d'arrivée départage). Sinon : ordre d'arrivée, jamais de tri par date.
 */
export const orderHistoryMessages = <T extends { ordre?: unknown }>(messages: T[]): T[] => {
  const allOrdered = messages.length > 0 && messages.every((m) => typeof m.ordre === 'number' && Number.isFinite(m.ordre));
  if (!allOrdered) return [...messages];
  return messages
    .map((m, index) => ({ m, index }))
    .sort((a, b) => (a.m.ordre as number) - (b.m.ordre as number) || a.index - b.index)
    .map(({ m }) => m);
};
