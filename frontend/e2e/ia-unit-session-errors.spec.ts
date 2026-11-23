import { test, expect } from '@playwright/test';
import {
  ANONYMOUS_USER_KEY,
  IA_SESSION_HEADER,
  buildConversationHeaders,
  clearSessionToken,
  clearStoredConversation,
  conversationStorageKey,
  getSessionToken,
  isAnonymousUser,
  isValidSessionToken,
  loadStoredConversationId,
  persistConversation,
  purgeAnonymousIaSession,
  sessionTokenStorageKey,
  setSessionToken,
} from '../src/services/iaSession';
import {
  IA_ERROR_MESSAGES,
  IA_MESSAGE_MAX_LENGTH,
  describeMotifSecours,
  formatFcfa,
  isAbandonedConversationStatus,
  isNetworkError,
  isTimeoutError,
  mapIaError,
  orderHistoryMessages,
} from '../src/components/ai/iaErrors';

class MemoryStorage implements Storage {
  private map = new Map<string, string>();
  get length(): number {
    return this.map.size;
  }
  clear(): void {
    this.map.clear();
  }
  getItem(key: string): string | null {
    return this.map.get(key) ?? null;
  }
  key(index: number): string | null {
    return Array.from(this.map.keys())[index] ?? null;
  }
  removeItem(key: string): void {
    this.map.delete(key);
  }
  setItem(key: string, value: string): void {
    this.map.set(key, String(value));
  }
}

test.describe('Tests unitaires iaSession & iaErrors (sans navigateur)', () => {
  let origSession: unknown;
  let origLocal: unknown;
  let memSession: MemoryStorage;
  let memLocal: MemoryStorage;

  test.beforeEach(() => {
    const g = globalThis as Record<string, unknown>;
    origSession = g.sessionStorage;
    origLocal = g.localStorage;
    memSession = new MemoryStorage();
    memLocal = new MemoryStorage();
    g.sessionStorage = memSession;
    g.localStorage = memLocal;
  });

  test.afterEach(() => {
    const g = globalThis as Record<string, unknown>;
    g.sessionStorage = origSession;
    g.localStorage = origLocal;
  });

  test('iaSession : gestion stricte anonyme vs authentifié sans fuite cross-session', () => {
    expect(isAnonymousUser(ANONYMOUS_USER_KEY)).toBe(true);
    expect(isAnonymousUser('user-uuid-1')).toBe(false);
    expect(isValidSessionToken('tok_abc-123_XYZ')).toBe(true);
    expect(isValidSessionToken('')).toBe(false);
    expect(isValidSessionToken('bad token with spaces')).toBe(false);

    // Anonyme sans sessionToken -> refus de persister
    expect(persistConversation('anon', 'conv-a', null)).toBe(false);
    expect(loadStoredConversationId('anon')).toBeNull();

    // Anonyme avec sessionToken -> sessionStorage uniquement + header X-IA-Session-Token
    expect(persistConversation('anon', 'conv-a', 'tok-secret-a')).toBe(true);
    expect(memSession.getItem(conversationStorageKey('anon'))).toBe('conv-a');
    expect(memSession.getItem(sessionTokenStorageKey('conv-a'))).toBe('tok-secret-a');
    expect(memLocal.getItem(conversationStorageKey('anon'))).toBeNull();
    expect(getSessionToken('conv-a')).toBe('tok-secret-a');
    expect(buildConversationHeaders('anon', 'conv-a')).toEqual({ [IA_SESSION_HEADER]: 'tok-secret-a' });
    expect(buildConversationHeaders('anon', 'conv-autre')).toEqual({});

    // Un id anonyme legacy en localStorage sans token est purgé automatiquement
    memLocal.setItem(conversationStorageKey('anon'), 'legacy-conv');
    expect(loadStoredConversationId('anon')).toBe('conv-a');
    expect(memLocal.getItem(conversationStorageKey('anon'))).toBeNull();

    // Authentifié : localStorage utilisé, token ignoré et jamais émis en header
    expect(persistConversation('user-1', 'conv-auth-1', 'ignored-token')).toBe(true);
    expect(memLocal.getItem(conversationStorageKey('user-1'))).toBe('conv-auth-1');
    expect(buildConversationHeaders('user-1', 'conv-auth-1')).toEqual({});

    // Purge anonyme complète
    setSessionToken('conv-b', 'tok-b');
    purgeAnonymousIaSession();
    expect(loadStoredConversationId('anon')).toBeNull();
    expect(getSessionToken('conv-a')).toBeNull();
    expect(getSessionToken('conv-b')).toBeNull();
    expect(loadStoredConversationId('user-1')).toBe('conv-auth-1');

    // Clear ciblé
    persistConversation('anon', 'conv-c', 'tok-c');
    clearStoredConversation('anon', 'conv-c');
    expect(getSessionToken('conv-c')).toBeNull();
    clearSessionToken('conv-c');
  });

  test('iaErrors : mapping HTTP 400/401/403/404/429/5xx, réseau, formatFcfa, motifSecours et tri par ordre', () => {
    expect(IA_MESSAGE_MAX_LENGTH).toBe(1000);

    const net = mapIaError(new TypeError('Failed to fetch'), { conversationScoped: true });
    expect(net.kind).toBe('network');
    expect(net.resetConversation).toBe(false);

    const sec400 = mapIaError(
      Object.assign(new Error('Requête invalide ou non autorisée par la politique de sécurité'), { status: 400 }),
      { conversationScoped: true }
    );
    expect(sec400.kind).toBe('security-policy');
    expect(sec400.resetConversation).toBe(false);

    const tooLong = mapIaError(Object.assign(new Error('ne doit pas dépasser 1000 caractères'), { status: 400 }), {
      conversationScoped: true,
    });
    expect(tooLong.kind).toBe('too-long');

    const scoped403 = mapIaError(Object.assign(new Error('Accès refusé : preuve invalide'), { status: 403 }), {
      conversationScoped: true,
    });
    expect(scoped403.kind).toBe('conversation-invalid');
    expect(scoped403.resetConversation).toBe(true);

    const unscoped403 = mapIaError(Object.assign(new Error('Forbidden'), { status: 403 }), {
      conversationScoped: false,
    });
    expect(unscoped403.kind).toBe('forbidden');
    expect(unscoped403.resetConversation).toBe(false);

    const scoped404 = mapIaError(Object.assign(new Error('Not found'), { status: 404 }), {
      conversationScoped: true,
    });
    expect(scoped404.kind).toBe('conversation-not-found');
    expect(scoped404.resetConversation).toBe(true);

    const r429 = mapIaError(Object.assign(new Error('Erreur (429)'), { status: 429 }), {
      conversationScoped: true,
    });
    expect(r429.message).toBe(IA_ERROR_MESSAGES.rateLimit);

    const r503 = mapIaError(Object.assign(new Error('Service Unavailable'), { status: 503 }), {
      conversationScoped: true,
    });
    expect(r503.kind).toBe('unavailable');
    expect(r503.message).toBe(IA_ERROR_MESSAGES.gatewayUnavailable);

    for (const gwStatus of [502, 504]) {
      const gwErr = mapIaError(Object.assign(new Error(`Gateway ${gwStatus}`), { status: gwStatus }), {
        conversationScoped: true,
      });
      expect(gwErr.kind).toBe('unavailable');
      expect(gwErr.message).toBe(IA_ERROR_MESSAGES.gatewayUnavailable);
    }

    const r500 = mapIaError(Object.assign(new Error('Internal Server Error'), { status: 500 }), {
      conversationScoped: true,
    });
    expect(r500.kind).toBe('unavailable');
    expect(r500.message).toBe(IA_ERROR_MESSAGES.unavailable);

    const r401 = mapIaError(Object.assign(new Error('Unauthorized'), { status: 401 }), {
      conversationScoped: true,
    });
    expect(r401.kind).toBe('unauthorized');
    expect(r401.message).toBe(IA_ERROR_MESSAGES.unauthorized);

    const timeoutByName = Object.assign(new Error('Timed out'), { name: 'TimeoutError' });
    expect(isTimeoutError(timeoutByName)).toBe(true);
    expect(isNetworkError(timeoutByName)).toBe(false);
    const rTimeout = mapIaError(timeoutByName, { conversationScoped: true });
    expect(rTimeout.kind).toBe('timeout');
    expect(rTimeout.message).toBe(IA_ERROR_MESSAGES.timeout);
    expect(rTimeout.resetConversation).toBe(false);

    const timeoutByFlag = Object.assign(new Error('Timed out'), { isTimeout: true });
    expect(mapIaError(timeoutByFlag, { conversationScoped: false }).kind).toBe('timeout');

    expect(isAbandonedConversationStatus(403)).toBe(true);
    expect(isAbandonedConversationStatus(404)).toBe(true);
    expect(isAbandonedConversationStatus(500)).toBe(false);

    expect(formatFcfa(300000)).toBe('300\u00a0000\u00a0FCFA');
    expect(formatFcfa(45000)).toBe('45\u00a0000\u00a0FCFA');
    expect(formatFcfa(null)).toBe('Prix non communiqué');

    expect(describeMotifSecours('QUOTA')).toContain('capacité');
    expect(describeMotifSecours('CLE_ABSENTE')).toContain('pas disponible');
    expect(describeMotifSecours(undefined)).toBe('Réponse générée par le moteur local');

    // Tri strict par ordre numérique (jamais par date!)
    const disordered = [
      { id: '4', ordre: 4, role: 'ASSISTANT' },
      { id: '2', ordre: 2, role: 'ASSISTANT' },
      { id: '1', ordre: 1, role: 'UTILISATEUR' },
      { id: '3', ordre: 3, role: 'UTILISATEUR' },
    ];
    expect(orderHistoryMessages(disordered).map((m) => m.id)).toEqual(['1', '2', '3', '4']);

    // Sans ordre -> conserve exactement l'ordre d'arrivée
    const legacy = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
    expect(orderHistoryMessages(legacy).map((m) => m.id)).toEqual(['a', 'b', 'c']);
  });
});
