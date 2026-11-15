import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IFrame } from '@stomp/stompjs';
import { useAuthStore } from '../store/useAuthStore';
import { MessageChatDTO } from '../types';
import { messagerieService } from '../services/messagerie.service';
import { refreshSessionToken } from '../services/api';
import { resolveWsUrl } from '../lib/env';
import { getErrorMessage } from '../lib/errors';

export function useConversationSocket(conversationId: string | undefined) {
  const { accessToken } = useAuthStore();
  const [messages, setMessages] = useState<MessageChatDTO[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isOffline, setIsOffline] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stompClientRef = useRef<Client | null>(null);

  // Load message history from REST API
  const fetchMessagesHistory = useCallback(async (id: string) => {
    try {
      const history = await messagerieService.listerMessages(id);
      setMessages(history);
      setError(null);
    } catch (err: unknown) {
      console.error('Erreur chargement historique messages:', err);
      setError(getErrorMessage(err, 'Erreur lors du chargement de la conversation'));
    }
  }, []);

  useEffect(() => {
    if (!conversationId || !accessToken) {
      return;
    }

    let cancelled = false;
    let consecutiveFailures = 0;
    void Promise.resolve().then(() => {
      if (cancelled) return;
      setIsConnecting(true);
      void fetchMessagesHistory(conversationId);
    });

    const wsUrl = resolveWsUrl('/ws');

    const tryRefreshAccessToken = async (): Promise<string | null> => {
      if (!useAuthStore.getState().refreshToken) {
        return null;
      }
      try {
        return await refreshSessionToken();
      } catch {
        return null;
      }
    };

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      beforeConnect: async () => {
        const latestToken = useAuthStore.getState().accessToken;
        if (!latestToken) {
          await client.deactivate();
          return;
        }
        client.connectHeaders = {
          Authorization: latestToken.startsWith('Bearer ') ? latestToken : `Bearer ${latestToken}`,
        };
      },

      onConnect: () => {
        consecutiveFailures = 0;
        setIsConnected(true);
        setIsConnecting(false);
        setIsOffline(false);
        setError(null);

        // Catch up on any missed messages during offline period
        fetchMessagesHistory(conversationId);

        // Subscribe to real-time conversation topic
        client.subscribe(`/topic/conversations/${conversationId}`, (frame: IFrame) => {
          try {
            const newMessage: MessageChatDTO = JSON.parse(frame.body);
            setMessages((prev) => {
              // Avoid duplicate messages if already present
              if (prev.some((m) => m.id === newMessage.id)) {
                return prev;
              }
              return [...prev, newMessage];
            });
          } catch (err) {
            console.error('Erreur parsing message STOMP:', err);
          }
        });
      },

      onStompError: (frame: IFrame) => {
        const msg = frame.headers['message'] || 'Erreur de connexion messagerie';
        console.warn('Erreur STOMP broker:', msg);
        setError(msg);
        setIsConnected(false);
        setIsConnecting(false);
        setIsOffline(true);
        const lower = msg.toLowerCase();
        if (
          lower.includes('désactivé') ||
          lower.includes('expir') ||
          lower.includes('révoqu') ||
          lower.includes('revoqu') ||
          lower.includes('invalide') ||
          lower.includes('non authentifi') ||
          lower.includes('unauthorized')
        ) {
          void (async () => {
            const refreshed = await tryRefreshAccessToken();
            if (refreshed && !cancelled) {
              client.connectHeaders = {
                Authorization: refreshed.startsWith('Bearer ') ? refreshed : `Bearer ${refreshed}`,
              };
              return;
            }
            await client.deactivate();
            void useAuthStore.getState().logout();
          })();
        }
      },

      onWebSocketClose: (event) => {
        setIsConnected(false);
        setIsOffline(true);
        consecutiveFailures += 1;
        const closeEvent = event as CloseEvent | undefined;
        const policyViolation = closeEvent?.code === 1008;
        if (policyViolation || consecutiveFailures >= 3) {
          void (async () => {
            const refreshed = await tryRefreshAccessToken();
            if (refreshed && !cancelled) {
              consecutiveFailures = 0;
              client.connectHeaders = {
                Authorization: refreshed.startsWith('Bearer ') ? refreshed : `Bearer ${refreshed}`,
              };
              return;
            }
            if (policyViolation) {
              await client.deactivate();
              void useAuthStore.getState().logout();
            }
          })();
        }
      },

      onWebSocketError: (event) => {
        console.warn('Erreur WebSocket:', event);
        setIsConnected(false);
        setIsOffline(true);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      cancelled = true;
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [conversationId, accessToken, fetchMessagesHistory]);

  const hasActiveSession = Boolean(conversationId && accessToken);

  const sendMessage = useCallback(
    async (contenu: string): Promise<boolean> => {
      if (!conversationId) return false;

      const trimmed = contenu.trim();
      if (!trimmed || trimmed.length > 1000) return false;

      // Reject basic <script> tags on client side too
      if (/<script.*?>.*?<\/script.*?>|<script.*?>|javascript:|onload=/i.test(trimmed)) {
        setError('Le message contient du code non autorisé');
        return false;
      }

      if (stompClientRef.current && stompClientRef.current.connected) {
        stompClientRef.current.publish({
          destination: `/app/conversations/${conversationId}/envoyer`,
          body: JSON.stringify({ contenu: trimmed }),
        });
        return true;
      } else {
        // Fallback warning or attempt reconnect
        setError('WebSocket déconnecté — Reconnexion en cours...');
        return false;
      }
    },
    [conversationId]
  );

  return {
    messages: hasActiveSession ? messages : [],
    isConnected: hasActiveSession ? isConnected : false,
    isConnecting: hasActiveSession ? isConnecting : false,
    isOffline: hasActiveSession ? isOffline : false,
    error: hasActiveSession ? error : null,
    sendMessage,
    refetchHistory: () => conversationId && fetchMessagesHistory(conversationId),
  };
}
