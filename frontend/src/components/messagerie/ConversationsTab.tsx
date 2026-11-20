import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  MessageSquare,
  Building2,
  Send,
  ShieldCheck,
  Search,
  CheckCheck,
  WifiOff,
  ArrowLeft,
  ExternalLink,
  AlertCircle,
  Inbox,
  User,
} from 'lucide-react';
import { ConversationChatDTO } from '../../types';
import { messagerieService } from '../../services/messagerie.service';
import { useAuthStore } from '../../store/useAuthStore';
import { useConversationSocket } from '../../hooks/useConversationSocket';
import { Avatar } from '../ui/Avatar';

export const ConversationsTab: React.FC = () => {
  const { utilisateur } = useAuthStore();
  const [conversations, setConversations] = useState<ConversationChatDTO[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<ConversationChatDTO | null>(null);
  const [mobilePane, setMobilePane] = useState<'list' | 'chat'>('list');
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [inputContent, setInputContent] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const activeId = selectedConversation?.id;

  const { messages, isConnected, isConnecting, isOffline, error, sendMessage } = useConversationSocket(activeId);

  const fetchConversations = useCallback(async (withSpinner = false) => {
    try {
      if (withSpinner) {
        setLoading(true);
      }
      const list = await messagerieService.listerMesConversations();
      setConversations(list);

      // Auto select first conversation if none selected
      if (list.length > 0) {
        setSelectedConversation((current) => current ?? list[0]);
      }
    } catch (err) {
      console.error('Erreur chargement des conversations:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const list = await messagerieService.listerMesConversations();
        if (cancelled) return;
        setConversations(list);
        if (list.length > 0) {
          setSelectedConversation((current) => current ?? list[0]);
        }
      } catch (err) {
        console.error('Erreur chargement des conversations:', err);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // When selected conversation changes, mark as read
  useEffect(() => {
    if (activeId) {
      messagerieService
        .marquerCommeLu(activeId)
        .then(() => {
          setConversations((prev) =>
            prev.map((c) => (c.id === activeId ? { ...c, nbMessagesNonLus: 0 } : c))
          );
        })
        .catch(console.error);
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
    const trimmed = inputContent.trim();
    if (!trimmed) return;

    const success = await sendMessage(trimmed);
    if (success) {
      setInputContent('');
      // Refresh conversation list preview
      void fetchConversations(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    }
  };

  const filteredConversations = conversations.filter((c) => {
    const isLocataire = utilisateur?.id === c.locataire?.id;
    const interlocuteur = isLocataire ? c.proprietaire : c.locataire;
    const name = `${interlocuteur?.prenom || ''} ${interlocuteur?.nom || ''}`.toLowerCase();
    const title = (c.annonceTitre || '').toLowerCase();
    const q = searchQuery.toLowerCase().trim();
    return name.includes(q) || title.includes(q);
  });

  if (loading && conversations.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-charcoal-400 space-y-3">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-terracotta-500 border-t-transparent" />
        <p className="text-xs font-semibold text-charcoal-600">Chargement de votre messagerie...</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-argile-200/80 shadow-warm-card overflow-hidden h-[calc(100dvh-13rem)] min-h-[340px] max-h-[720px] flex flex-col md:flex-row">
      {/* Left Sidebar: Conversations List */}
      <div
        className={`${
          mobilePane === 'chat' ? 'hidden md:flex' : 'flex flex-1 md:flex-none'
        } w-full md:w-72 lg:w-80 min-h-0 border-r border-argile-200/80 flex-col bg-sand-50/60`}
      >
        {/* Search header */}
        <div className="p-3 sm:p-4 border-b border-argile-200/80 shrink-0 bg-white/70 backdrop-blur-xs">
          <div className="flex items-center justify-between gap-2 mb-2.5 sm:mb-3">
            <h2 className="font-display font-bold text-charcoal-900 text-sm sm:text-base flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-terracotta-600 shrink-0" />
              <span>Messagerie Directe</span>
            </h2>
            {conversations.length > 0 && (
              <span className="text-[11px] font-bold text-charcoal-500 bg-sand-200/80 px-2 py-0.5 rounded-full">
                {conversations.length}
              </span>
            )}
          </div>
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-charcoal-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Rechercher un correspondant..."
              className="w-full pl-9 pr-3 py-2 text-xs bg-white border border-argile-200 rounded-xl placeholder:text-charcoal-400 text-charcoal-900 focus:outline-none focus:ring-2 focus:ring-terracotta-500/20 focus:border-terracotta-500 transition-colors"
            />
          </div>
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto divide-y divide-sand-200/70 min-h-0">
          {filteredConversations.length === 0 ? (
            <div className="p-8 text-center text-charcoal-400 space-y-2">
              <Inbox className="w-8 h-8 text-charcoal-300 mx-auto" />
              <p className="text-xs font-semibold text-charcoal-700">Aucune conversation trouvée</p>
              {searchQuery && (
                <p className="text-[11px] text-charcoal-500">
                  Aucun résultat ne correspond à votre recherche.
                </p>
              )}
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
                  type="button"
                  onClick={() => {
                    setSelectedConversation(conv);
                    setMobilePane('chat');
                  }}
                  className={`w-full text-left p-3.5 sm:p-4 transition-colors flex items-start gap-3 hover:bg-sand-100/70 ${
                    isSelected
                      ? 'bg-sand-100/90 border-l-4 border-terracotta-500'
                      : 'border-l-4 border-transparent'
                  }`}
                >
                  <div className="relative shrink-0">
                    <Avatar
                      src={interlocuteur?.photoProfilUrl}
                      name={nomComplet}
                      size="md"
                    />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-1">
                      <h3 className="font-display font-bold text-charcoal-900 text-xs sm:text-sm truncate">
                        {nomComplet}
                      </h3>
                      {conv.dernierMessage && (
                        <span className="text-[10px] font-medium text-charcoal-500 shrink-0">
                          {new Date(conv.dernierMessage.dateEnvoi).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      )}
                    </div>

                    {conv.annonceTitre && (
                      <p className="text-[11px] text-terracotta-700 font-semibold truncate mt-0.5 flex items-center gap-1">
                        <Building2 className="w-3 h-3 shrink-0" />
                        <span>{conv.annonceTitre}</span>
                      </p>
                    )}

                    <p className="text-xs text-charcoal-600 truncate mt-1">
                      {conv.dernierMessage ? conv.dernierMessage.contenu : 'Nouvelle conversation'}
                    </p>
                  </div>

                  {conv.nbMessagesNonLus > 0 && (
                    <span className="shrink-0 ml-1 px-2 py-0.5 rounded-full text-[11px] font-extrabold bg-terracotta-500 text-white shadow-xs">
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
      <div
        className={`${
          mobilePane === 'list' ? 'hidden md:flex' : 'flex'
        } flex-1 flex-col bg-white min-w-0 min-h-0`}
      >
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
              const roleLabel = isLocataire ? 'Propriétaire / Gestionnaire' : 'Locataire candidat';

              return (
                <div className="p-3 sm:p-4 border-b border-argile-200/80 flex items-center justify-between gap-2 bg-white shrink-0">
                  <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
                    <button
                      type="button"
                      onClick={() => setMobilePane('list')}
                      aria-label="Retour aux conversations"
                      className="md:hidden p-2 -ml-1 rounded-xl hover:bg-sand-100 text-charcoal-700 shrink-0 min-h-[44px] min-w-[44px] flex items-center justify-center transition-colors"
                    >
                      <ArrowLeft className="w-5 h-5" />
                    </button>

                    <Avatar
                      src={interlocuteur?.photoProfilUrl}
                      name={nomComplet}
                      size="md"
                    />

                    <div className="min-w-0">
                      <h3 className="font-display font-bold text-charcoal-900 text-xs sm:text-sm truncate flex items-center gap-1.5">
                        <span className="truncate">{nomComplet}</span>
                        <ShieldCheck className="w-4 h-4 text-sahel-700 shrink-0" />
                      </h3>
                      <p className="text-[11px] text-charcoal-500 flex items-center gap-1 truncate mt-0.5">
                        <User className="w-3 h-3 text-charcoal-400 shrink-0" />
                        <span className="truncate">{roleLabel}</span>
                      </p>
                    </div>
                  </div>

                  {/* Real-time status badge */}
                  <span
                    className={`inline-flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-full shrink-0 border ${
                      isConnected
                        ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                        : isConnecting
                        ? 'bg-ocher-50 text-ocher-800 border-ocher-200'
                        : 'bg-sand-100 text-charcoal-600 border-sand-300'
                    }`}
                  >
                    <span
                      className={`w-2 h-2 rounded-full ${
                        isConnected
                          ? 'bg-emerald-500 animate-pulse'
                          : isConnecting
                          ? 'bg-ocher-500 animate-pulse'
                          : 'bg-charcoal-400'
                      }`}
                    />
                    <span className="hidden xs:inline">
                      {isConnected ? 'Temps réel actif' : isConnecting ? 'Reconnexion...' : 'Hors ligne'}
                    </span>
                  </span>
                </div>
              );
            })()}

            {/* Property Context Banner */}
            {selectedConversation.annonceTitre && (
              <div className="shrink-0 px-4 py-2 bg-sand-100/80 border-b border-argile-200/80 flex items-center justify-between gap-3 text-xs">
                <div className="flex items-center gap-2 min-w-0">
                  <Building2 className="w-3.5 h-3.5 text-terracotta-600 shrink-0" />
                  <span className="text-charcoal-500 shrink-0 font-medium hidden sm:inline">
                    Bien concerné :
                  </span>
                  <span className="font-semibold text-charcoal-900 truncate">
                    {selectedConversation.annonceTitre}
                  </span>
                </div>
                {selectedConversation.annonceId && (
                  <Link
                    to={`/annonce/${selectedConversation.annonceId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-terracotta-600 hover:text-terracotta-700 text-xs font-bold hover:underline shrink-0"
                  >
                    <span>Voir l'annonce</span>
                    <ExternalLink className="w-3 h-3" />
                  </Link>
                )}
              </div>
            )}

            {/* Offline Network Banner */}
            {isOffline && (
              <div className="bg-ocher-600 text-white text-xs px-4 py-2 flex items-center gap-2 font-medium shrink-0">
                <WifiOff className="w-4 h-4 shrink-0" />
                <span>Connexion réseau interrompue. Reconnexion automatique...</span>
              </div>
            )}

            {/* Error Banner */}
            {error && (
              <div className="bg-rose-600 text-white text-xs px-4 py-2 flex items-center gap-2 font-medium shrink-0">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span className="flex-1">{error}</span>
              </div>
            )}

            {/* Message Stream */}
            <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3.5 bg-sand-50/40 min-h-0">
              {messages.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center p-6 text-center text-charcoal-400">
                  <MessageSquare className="w-10 h-10 text-charcoal-300 mb-2" />
                  <p className="text-xs font-semibold text-charcoal-700">Aucun message pour le moment</p>
                  <p className="text-[11px] text-charcoal-500 mt-0.5 max-w-xs">
                    Envoyez un message pour démarrer la conversation avec votre correspondant.
                  </p>
                </div>
              ) : (
                messages.map((msg) => {
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
                      transition={{ duration: 0.16 }}
                      className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-[80%] sm:max-w-[72%] rounded-2xl px-4 py-2.5 text-xs sm:text-sm shadow-xs ${
                          isMe
                            ? 'bg-terracotta-500 text-white rounded-br-xs'
                            : 'bg-white text-charcoal-900 border border-argile-200/90 rounded-bl-xs'
                        }`}
                      >
                        <p className="whitespace-pre-wrap break-words leading-relaxed">{msg.contenu}</p>
                        <div
                          className={`flex items-center justify-end gap-1 mt-1 text-[10px] ${
                            isMe ? 'text-terracotta-100' : 'text-charcoal-400'
                          }`}
                        >
                          <span>{timeStr}</span>
                          {isMe && (
                            <CheckCheck
                              className={`w-3.5 h-3.5 ${msg.lu ? 'text-white' : 'opacity-70'}`}
                            />
                          )}
                        </div>
                      </div>
                    </motion.div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Bottom Input Form (reserves right gutter clearance for floating Samba IA FAB on mobile/tablet) */}
            <div className="p-3 pr-16 sm:p-4 sm:pr-20 2xl:pr-4 border-t border-argile-200/80 bg-white shrink-0">
              <form onSubmit={handleSend} className="flex gap-2">
                <input
                  type="text"
                  value={inputContent}
                  onChange={(e) => setInputContent(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Écrivez un message..."
                  maxLength={1000}
                  className="flex-1 min-w-0 bg-sand-50/80 border border-argile-200 rounded-xl px-3.5 sm:px-4 py-2 sm:py-2.5 text-xs sm:text-sm text-charcoal-900 placeholder:text-charcoal-400 focus:outline-none focus:ring-2 focus:ring-terracotta-500/20 focus:border-terracotta-500 focus:bg-white transition-colors min-h-[44px]"
                />
                <button
                  type="submit"
                  disabled={!inputContent.trim()}
                  aria-label="Envoyer"
                  className="px-3.5 sm:px-5 py-2 sm:py-2.5 rounded-xl bg-terracotta-500 hover:bg-terracotta-600 text-white text-xs sm:text-sm font-bold transition-colors disabled:opacity-40 flex items-center gap-1.5 shadow-xs shrink-0 min-h-[44px]"
                >
                  <Send className="w-4 h-4" />
                  <span className="hidden xs:inline">Envoyer</span>
                </button>
              </form>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-charcoal-400">
            <MessageSquare className="w-12 h-12 text-charcoal-300 mb-3" />
            <h3 className="text-base font-display font-bold text-charcoal-800">
              Sélectionnez une conversation
            </h3>
            <p className="text-xs text-charcoal-500 mt-1 max-w-sm">
              Choisissez un fil dans la liste de gauche pour lire vos échanges avec vos locataires ou propriétaires.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ConversationsTab;
