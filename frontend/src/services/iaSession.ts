/**
 * Gestion de la session de conversation IA (Samba).
 *
 * Contrat (ANO-PROD-01) :
 *  - Une conversation ANONYME (pas de JWT) est prouvée uniquement par le header HTTP
 *    `X-IA-Session-Token`, renvoyé à la création (`sessionToken`). Le cookie
 *    `ecourtier_ia_session` n'existe plus.
 *  - Le token est lié à UNE conversation : il est stocké en `sessionStorage` sous la clé
 *    `ecourtier_ia_session_token_<conversationId>`, jamais dans une URL, jamais loggé.
 *  - L'id de conversation anonyme vit dans `sessionStorage` (même durée de vie que le token).
 *  - Un utilisateur authentifié conserve `localStorage` (par utilisateur), sans token : son
 *    JWT (header Authorization, géré par apiFetch) reste la seule preuve.
 *
 * Ce module est pur (aucune dépendance applicative) afin d'être testable sans navigateur.
 */

export const IA_SESSION_HEADER = 'X-IA-Session-Token';
export const ANONYMOUS_USER_KEY = 'anon';

const CONVERSATION_KEY_PREFIX = 'ecourtier_ia_conversation_';
const TOKEN_KEY_PREFIX = 'ecourtier_ia_session_token_';

// Un header HTTP ne peut contenir que des caractères ASCII imprimables (sans espace).
const TOKEN_PATTERN = /^[\x21-\x7E]{1,1024}$/;

type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem' | 'key' | 'length'>;

const getStorage = (kind: 'session' | 'local'): StorageLike | null => {
  try {
    const g = globalThis as { sessionStorage?: StorageLike; localStorage?: StorageLike };
    return (kind === 'session' ? g.sessionStorage : g.localStorage) ?? null;
  } catch {
    return null;
  }
};

const listKeys = (storage: StorageLike, prefix: string): string[] => {
  const keys: string[] = [];
  for (let i = 0; i < storage.length; i += 1) {
    const k = storage.key(i);
    if (k && k.startsWith(prefix)) keys.push(k);
  }
  return keys;
};

export const isAnonymousUser = (userId: string): boolean => userId === ANONYMOUS_USER_KEY;

export const conversationStorageKey = (userId: string): string => `${CONVERSATION_KEY_PREFIX}${userId}`;
export const sessionTokenStorageKey = (conversationId: string): string => `${TOKEN_KEY_PREFIX}${conversationId}`;

const storageForUser = (userId: string): StorageLike | null =>
  getStorage(isAnonymousUser(userId) ? 'session' : 'local');

export const isValidSessionToken = (token: unknown): token is string =>
  typeof token === 'string' && TOKEN_PATTERN.test(token);

// --- Token (lié à une conversation précise) -------------------------------------------------

export const getSessionToken = (conversationId: string | null | undefined): string | null => {
  if (!conversationId) return null;
  const value = getStorage('session')?.getItem(sessionTokenStorageKey(conversationId)) ?? null;
  return isValidSessionToken(value) ? value : null;
};

export const setSessionToken = (conversationId: string, token: string): boolean => {
  if (!conversationId || !isValidSessionToken(token)) return false;
  try {
    getStorage('session')?.setItem(sessionTokenStorageKey(conversationId), token);
    return true;
  } catch {
    return false;
  }
};

export const clearSessionToken = (conversationId: string | null | undefined): void => {
  if (!conversationId) return;
  try {
    getStorage('session')?.removeItem(sessionTokenStorageKey(conversationId));
  } catch {
    /* stockage indisponible : rien à purger */
  }
};

// --- Conversation (id + éventuel token) -----------------------------------------------------

/**
 * Restaure l'id de conversation d'un utilisateur.
 * Anonyme : l'id n'est valable que s'il possède son token ; sinon (ou si un ancien id legacy
 * en localStorage existe, sans preuve possible) tout est nettoyé et on repart à neuf.
 */
export const loadStoredConversationId = (userId: string): string | null => {
  const key = conversationStorageKey(userId);
  if (isAnonymousUser(userId)) {
    // Legacy (cookie supprimé côté backend) : un id anonyme en localStorage est inutilisable.
    try {
      getStorage('local')?.removeItem(key);
    } catch {
      /* ignore */
    }
    const id = getStorage('session')?.getItem(key) ?? null;
    if (!id) return null;
    if (!getSessionToken(id)) {
      clearStoredConversation(userId);
      return null;
    }
    return id;
  }
  return storageForUser(userId)?.getItem(key) ?? null;
};

/**
 * Persiste la conversation courante. Anonyme : exige un sessionToken valide (sinon rien n'est
 * persisté et la fonction renvoie false). Authentifié : le token éventuel est IGNORÉ.
 */
export const persistConversation = (userId: string, conversationId: string, sessionToken?: string | null): boolean => {
  if (!conversationId) return false;
  try {
    if (isAnonymousUser(userId)) {
      if (!sessionToken || !setSessionToken(conversationId, sessionToken)) return false;
      getStorage('session')?.setItem(conversationStorageKey(userId), conversationId);
      return true;
    }
    storageForUser(userId)?.setItem(conversationStorageKey(userId), conversationId);
    return true;
  } catch {
    return false;
  }
};

/** Supprime l'id stocké ET le token de la conversation correspondante (+ celle fournie). */
export const clearStoredConversation = (userId: string, alsoConversationId?: string | null): void => {
  const key = conversationStorageKey(userId);
  try {
    const storage = storageForUser(userId);
    const storedId = storage?.getItem(key) ?? null;
    storage?.removeItem(key);
    clearSessionToken(storedId);
    clearSessionToken(alsoConversationId);
  } catch {
    /* ignore */
  }
};

/**
 * Purge toute session anonyme (ids + tokens). Appelé à la connexion/déconnexion : un token d'une
 * session précédente ne doit jamais être réutilisé.
 */
export const purgeAnonymousIaSession = (): void => {
  try {
    const session = getStorage('session');
    if (session) {
      listKeys(session, TOKEN_KEY_PREFIX).forEach((k) => session.removeItem(k));
      session.removeItem(conversationStorageKey(ANONYMOUS_USER_KEY));
    }
    getStorage('local')?.removeItem(conversationStorageKey(ANONYMOUS_USER_KEY));
  } catch {
    /* ignore */
  }
};

// --- Headers --------------------------------------------------------------------------------

/**
 * Headers propres à une requête portant sur une conversation. Anonyme : `X-IA-Session-Token`
 * (token de CETTE conversation uniquement). Authentifié : aucun (le JWT est ajouté par apiFetch).
 */
export const buildConversationHeaders = (userId: string, conversationId: string): Record<string, string> => {
  if (!isAnonymousUser(userId)) return {};
  const token = getSessionToken(conversationId);
  return token ? { [IA_SESSION_HEADER]: token } : {};
};
