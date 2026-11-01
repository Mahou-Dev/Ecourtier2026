import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import {
  MessageSquare,
  Building2,
  Send,
  ShieldCheck,
  Search,
  CheckCheck,
  WifiOff,
} from 'lucide-react';
import { ConversationChatDTO } from '../../types';
import { messagerieService } from '../../services/messagerie.service';
import { useAuthStore } from '../../store/useAuthStore';
import { useConversationSocket } from '../../hooks/useConversationSocket';

export const ConversationsTab: React.FC = () => {
  const { utilisateur } = useAuthStore();
  const [conversations, setConversations] = useState<ConversationChatDTO[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<ConversationChatDTO | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [inputContent, setInputContent] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const activeId = selectedConversation?.id;

  const { messages, isConnected, isOffline, sendMessage } = useConversationSocket(activeId);

  const fetchConversations = async () => {
    try {
      setLoading(true);
      const list = await messagerieService.listerMesConversations();
      setConversations(list);

      // Auto select first conversation if none selected
      if (list.length > 0 && !selectedConversation) {
        setSelectedConversation(list[0]);
      }
    } catch (err) {
      console.error('Erreur chargement des conversations:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConversations();
  }, []);

  // When selected conversation changes, mark as read
  useEffect(() => {
    if (activeId) {
      messagerieService.marquerCommeLu(activeId).then(() => {
        setConversations((prev) =>
          prev.map((c) => (c.id === activeId ? { ...c, nbMessagesNonLus: 0 } : c))
        );
      }).catch(console.error);
    }
  }, [activeId]);

  // Scroll to bottom on new messages
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputContent.trim()) return;

    const success = await sendMessage(inputContent);
    if (success) {
      setInputContent('');
      // Refresh conversation list preview
      fetchConversations();
    }
  };

  const filteredConversations = conversations.filter((c) => {
    const isLocataire = utilisateur?.id === c.locataire?.id;
    const interlocuteur = isLocataire ? c.proprietaire : c.locataire;
    const name = `${interlocuteur?.prenom || ''} ${interlocuteur?.nom || ''}`.toLowerCase();
    const title = (c.annonceTitre || '').toLowerCase();
    const q = searchQuery.toLowerCase();
    return name.includes(q) || title.includes(q);
  });

  if (loading && conversations.length === 0) {
    return (
      <div className="flex items-center justify-center p-12 text-slate-400">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-600"></div>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden h-[680px] flex flex-col md:flex-row">
      {/* Left Sidebar: Conversations List */}
      <div className="w-full md:w-80 border-r border-slate-200 dark:border-slate-800 flex flex-col bg-slate-50/50 dark:bg-slate-900/50">
        {/* Search header */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800">
          <h2 className="font-semibold text-slate-900 dark:text-slate-100 text-lg mb-3 flex items-center gap-2">
            <MessageSquare className="w-5 h-5 text-amber-600" />
            Messagerie Instantanée
          </h2>
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Rechercher un correspondant..."
              className="w-full pl-9 pr-3 py-2 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
            />
          </div>
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800/60">
          {filteredConversations.length === 0 ? (
            <div className="p-8 text-center text-slate-400 text-sm">
              Aucune conversation trouvée
            </div>
          ) : (
            filteredConversations.map((conv) => {
              const isLocataire = utilisateur?.id === conv.locataire?.id;
              const interlocuteur = isLocataire ? conv.proprietaire : conv.locataire;
              const nomComplet = interlocuteur
                ? `${interlocuteur.prenom} ${interlocuteur.nom}`
                : 'Interlocuteur';
              const isSelected = selectedConversation?.id === conv.id;

              return (
                <button
                  key={conv.id}
                  onClick={() => setSelectedConversation(conv)}
                  className={`w-full text-left p-4 transition-colors flex items-start gap-3 hover:bg-slate-100/70 dark:hover:bg-slate-800/50 ${
                    isSelected ? 'bg-amber-50/80 dark:bg-amber-950/20 border-l-4 border-amber-600' : ''
                  }`}
                >
                  <div className="relative shrink-0">
                    {interlocuteur?.photoProfilUrl ? (
                      <img
                        src={interlocuteur.photoProfilUrl}
                        alt={nomComplet}
                        className="w-11 h-11 rounded-full object-cover"
                      />
                    ) : (
                      <div className="w-11 h-11 rounded-full bg-gradient-to-br from-amber-500 to-orange-600 text-white font-semibold flex items-center justify-center">
                        {nomComplet.charAt(0)}
                      </div>
                    )}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-1">
                      <h4 className="font-medium text-slate-900 dark:text-slate-100 text-sm truncate">
                        {nomComplet}
                      </h4>
                      {conv.dernierMessage && (
                        <span className="text-[11px] text-slate-400 shrink-0">
                          {new Date(conv.dernierMessage.dateEnvoi).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      )}
                    </div>

                    <p className="text-xs text-amber-700 dark:text-amber-400 font-medium truncate mt-0.5 flex items-center gap-1">
                      <Building2 className="w-3 h-3 shrink-0" />
                      {conv.annonceTitre}
                    </p>

                    <p className="text-xs text-slate-500 dark:text-slate-400 truncate mt-1">
                      {conv.dernierMessage ? conv.dernierMessage.contenu : 'Nouvelle conversation'}
                    </p>
                  </div>

                  {conv.nbMessagesNonLus > 0 && (
                    <span className="shrink-0 ml-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-amber-600 text-white">
                      {conv.nbMessagesNonLus}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Right Main Chat View */}
      <div className="flex-1 flex flex-col bg-white dark:bg-slate-900 min-w-0">
        {selectedConversation ? (
          <>
            {/* Active Header */}
            {(() => {
              const isLocataire = utilisateur?.id === selectedConversation.locataire?.id;
              const interlocuteur = isLocataire
                ? selectedConversation.proprietaire
                : selectedConversation.locataire;
              const nomComplet = interlocuteur
                ? `${interlocuteur.prenom} ${interlocuteur.nom}`
                : 'Interlocuteur';

              return (
                <div className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-slate-900">
                  <div className="flex items-center gap-3 min-w-0">
                    {interlocuteur?.photoProfilUrl ? (
                      <img
                        src={interlocuteur.photoProfilUrl}
                        alt={nomComplet}
                        className="w-10 h-10 rounded-full object-cover"
                      />
                    ) : (
                      <div className="w-10 h-10 rounded-full bg-gradient-to-br from-amber-500 to-orange-600 text-white font-semibold flex items-center justify-center">
                        {nomComplet.charAt(0)}
                      </div>
                    )}
                    <div className="min-w-0">
                      <h3 className="font-semibold text-slate-900 dark:text-slate-100 text-base truncate flex items-center gap-1.5">
                        {nomComplet}
                        <ShieldCheck className="w-4 h-4 text-amber-600 inline" />
                      </h3>
                      <p className="text-xs text-slate-500 dark:text-slate-400 flex items-center gap-1 truncate">
                        <Building2 className="w-3.5 h-3.5 text-amber-600" />
                        {selectedConversation.annonceTitre}
                      </p>
                    </div>
                  </div>

                  <span
                    className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
                      isConnected
                        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
                    }`}
                  >
                    <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                    {isConnected ? 'Temps réel actif' : 'Reconnexion...'}
                  </span>
                </div>
              );
            })()}

            {/* Offline Network Banner */}
            {isOffline && (
              <div className="bg-amber-500 text-white text-xs px-4 py-2 flex items-center gap-2 font-medium">
                <WifiOff className="w-4 h-4" />
                Connexion réseau interrompue. Reconnexion automatique...
              </div>
            )}

            {/* Message Stream */}
            <div className="flex-1 overflow-y-auto p-6 space-y-4 bg-slate-50/40 dark:bg-slate-950/40 min-h-0">
              {messages.map((msg) => {
                const isMe = msg.expediteur?.id === utilisateur?.id;
                const timeStr = new Date(msg.dateEnvoi).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                });

                return (
                  <motion.div
                    key={msg.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.18 }}
                    className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm shadow-xs ${
                        isMe
                          ? 'bg-amber-600 text-white rounded-br-xs'
                          : 'bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 border border-slate-200 dark:border-slate-700 rounded-bl-xs'
                      }`}
                    >
                      <p className="whitespace-pre-wrap break-words leading-relaxed">{msg.contenu}</p>
                      <div
                        className={`flex items-center justify-end gap-1 mt-1 text-[10px] ${
                          isMe ? 'text-amber-100' : 'text-slate-400'
                        }`}
                      >
                        <span>{timeStr}</span>
                        {isMe && <CheckCheck className={`w-3.5 h-3.5 ${msg.lu ? 'text-amber-200' : 'opacity-70'}`} />}
                      </div>
                    </div>
                  </motion.div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* Bottom Input Form */}
            <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <form onSubmit={handleSend} className="flex gap-2">
                <input
                  type="text"
                  value={inputContent}
                  onChange={(e) => setInputContent(e.target.value)}
                  placeholder="Écrivez un message..."
                  maxLength={1000}
                  className="flex-1 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
                />
                <button
                  type="submit"
                  disabled={!inputContent.trim()}
                  className="px-5 py-2.5 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium transition-colors disabled:opacity-40 flex items-center gap-2 shadow-xs"
                >
                  <Send className="w-4 h-4" />
                  Envoyer
                </button>
              </form>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-400">
            <MessageSquare className="w-12 h-12 text-slate-300 dark:text-slate-700 mb-3" />
            <p className="text-base font-medium text-slate-700 dark:text-slate-300">
              Sélectionnez une conversation
            </p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm">
              Choisissez un fil dans la liste de gauche pour lire vos échanges avec vos locataires ou propriétaires.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
