import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IFrame } from '@stomp/stompjs';
import { useAuthStore } from '../store/useAuthStore';
import { MessageChatDTO } from '../types';
import { messagerieService } from '../services/messagerie.service';
import { API_BASE_URL } from '../services/api';

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
    } catch (err: any) {
      console.error('Erreur chargement historique messages:', err);
      setError(err?.message || 'Erreur lors du chargement de la conversation');
    }
  }, []);

  useEffect(() => {
    if (!conversationId || !accessToken) {
      setMessages([]);
      setIsConnected(false);
      setIsConnecting(false);
      return;
    }

    // Load initial history
    fetchMessagesHistory(conversationId);

    // Build WS URL from API_BASE_URL or window location fallback
    const wsBaseUrl = API_BASE_URL.replace(/^http/, 'ws');
    const wsUrl = `${wsBaseUrl}/ws`;

    setIsConnecting(true);

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
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
        console.warn('Erreur STOMP broker:', frame.headers['message']);
        setError(frame.headers['message'] || 'Erreur de connexion messagerie');
        setIsConnected(false);
        setIsOffline(true);
      },

      onWebSocketClose: () => {
        setIsConnected(false);
        setIsOffline(true);
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
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [conversationId, accessToken, fetchMessagesHistory]);

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
    messages,
    isConnected,
    isConnecting,
    isOffline,
    error,
    sendMessage,
    refetchHistory: () => conversationId && fetchMessagesHistory(conversationId),
  };
}
