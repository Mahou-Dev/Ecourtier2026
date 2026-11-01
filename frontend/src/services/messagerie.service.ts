import { apiFetch } from './api';
import { ConversationChatDTO, MessageChatDTO } from '../types';

export const messagerieService = {
  async initierConversation(annonceId: string): Promise<ConversationChatDTO> {
    return apiFetch<ConversationChatDTO>(`/api/v1/annonces/${annonceId}/conversations`, {
      method: 'POST',
    });
  },

  async listerMesConversations(): Promise<ConversationChatDTO[]> {
    return apiFetch<ConversationChatDTO[]>('/api/v1/conversations/mes-conversations');
  },

  async obtenirConversation(conversationId: string): Promise<ConversationChatDTO> {
    return apiFetch<ConversationChatDTO>(`/api/v1/conversations/${conversationId}`);
  },

  async listerMessages(conversationId: string): Promise<MessageChatDTO[]> {
    return apiFetch<MessageChatDTO[]>(`/api/v1/conversations/${conversationId}/messages/tous`);
  },

  async marquerCommeLu(conversationId: string): Promise<void> {
    await apiFetch(`/api/v1/conversations/${conversationId}/marquer-lu`, {
      method: 'PUT',
    });
  },

  async compterMessagesNonLus(): Promise<number> {
    const data = await apiFetch<{ totalNonLus: number }>('/api/v1/conversations/non-lus/count');
    return data.totalNonLus || 0;
  },
};
