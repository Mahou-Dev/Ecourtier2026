import React, { useState, useEffect, useRef } from 'react';
import { Sparkles, X, Send, Bot, User, ShieldCheck, CheckCircle2, AlertCircle, RotateCcw } from 'lucide-react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Button } from '../ui/Button';
import { PropertyVisual } from '../property/PropertyVisual';
import { apiFetch } from '../../services/api';
import { AnnonceDTO, MessageIAResponseDTO, ConversationHistoryResponseDTO } from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { Link, useLocation } from 'react-router-dom';

export interface ChatMessageItem {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  questionDeRelance?: string;
  suggestedProperties?: AnnonceDTO[];
  isError?: boolean;
}

const formatTimeLabel = (dateInput?: string): string => {
  const d = dateInput ? new Date(dateInput) : new Date();
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const createWelcomeMessage = (): ChatMessageItem => ({
  id: 'msg-welcome',
  sender: 'ai',
  text: 'Na nga def ! Je suis Samba, votre assistant IA E-Courtier. Dites-moi quel logement vous cherchez (ville, budget, type, meublé)...',
  timestamp: formatTimeLabel(),
});

type LegacyMessageIADTO = MessageIAResponseDTO & {
  reponse?: string;
  criteresExtraits?: Record<string, unknown> & { questionDeRelance?: string };
};

export const AiAssistantWidget: React.FC = () => {
  const { utilisateur } = useAuthStore();
  const currentUserId = utilisateur?.id || 'anon';
  return <AiAssistantWidgetInner key={currentUserId} currentUserId={currentUserId} />;
};

const AiAssistantWidgetInner: React.FC<{ currentUserId: string }> = ({ currentUserId }) => {
  const shouldReduceMotion = useReducedMotion();
  const location = useLocation();
  const isHome = location.pathname === '/';
  const storageKey = `ecourtier_ia_conversation_${currentUserId}`;

  const [isOpen, setIsOpen] = useState(false);
  const [inputText, setInputText] = useState('');
  const [conversationId, setConversationId] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(storageKey);
  });
  const [isSending, setIsSending] = useState(false);
  const [messages, setMessages] = useState<ChatMessageItem[]>(() => [createWelcomeMessage()]);

  // R1: Compteur de génération et contrôleur d'envoi pour bloquer les courses asynchrones
  const generationRef = useRef(0);
  const msgSeqRef = useRef(0);
  const sendAbortControllerRef = useRef<AbortController | null>(null);

  const isSessionStillValid = (expectedGen: number, expectedUserId: string, signal?: AbortSignal) => {
    const liveUserId = useAuthStore.getState().utilisateur?.id || 'anon';
    return (
      generationRef.current === expectedGen &&
      liveUserId === expectedUserId &&
      !(signal?.aborted)
    );
  };

  // Restore existing conversation on reload (F5) for this user key
  useEffect(() => {
    const genCounter = generationRef;
    const currentGen = ++genCounter.current;
    const abortController = new AbortController();

    const savedConvId = typeof window !== 'undefined' ? localStorage.getItem(storageKey) : null;
    if (!savedConvId) {
      return () => {
        genCounter.current++;
        abortController.abort();
      };
    }

    apiFetch<ConversationHistoryResponseDTO>(`/api/v1/ia/conversations/${savedConvId}`, {
      signal: abortController.signal,
    })
      .then((history) => {
        if (!isSessionStillValid(currentGen, currentUserId, abortController.signal)) {
          return;
        }

        if (history && history.messages && history.messages.length > 0) {
          const restored: ChatMessageItem[] = history.messages.map((m, idx) => ({
            id: m.id || `msg-restored-${idx}`,
            sender: m.role === 'UTILISATEUR' ? 'user' : 'ai',
            text: m.contenu,
            timestamp: formatTimeLabel(m.dateEnvoi),
            questionDeRelance:
              typeof m.criteresExtraits?.questionDeRelance === 'string'
                ? m.criteresExtraits.questionDeRelance
                : undefined,
          }));
          setMessages(restored);
        }
      })
      .catch((err: unknown) => {
        if (
          !isSessionStillValid(currentGen, currentUserId, abortController.signal) ||
          (err instanceof Error && err.name === 'AbortError')
        ) {
          return;
        }

        if (typeof window !== 'undefined') {
          localStorage.removeItem(storageKey);
        }
        setConversationId(null);
        setMessages([createWelcomeMessage()]);
      });

    return () => {
      genCounter.current++;
      abortController.abort();
      sendAbortControllerRef.current?.abort();
      sendAbortControllerRef.current = null;
    };
  }, [currentUserId, storageKey]);

  const handleResetConversation = () => {
    generationRef.current++;
    sendAbortControllerRef.current?.abort();
    sendAbortControllerRef.current = null;
    setIsSending(false);
    if (typeof window !== 'undefined') {
      localStorage.removeItem(storageKey);
    }
    setConversationId(null);
    setMessages([createWelcomeMessage()]);
  };

  const suggestions = [
    'Appartement 3 pièces à Nema < 160 000 FCFA',
    'Studio meublé à Dakar',
    'Villa avec piscine à Saly',
  ];

  const nextSequenceId = (prefix: string): string => {
    msgSeqRef.current += 1;
    return `${prefix}-${msgSeqRef.current}`;
  };

  const addAssistantMessage = (msg: LegacyMessageIADTO) => {
    const relance =
      msg.questionDeRelance ??
      (typeof msg.criteresExtraits?.questionDeRelance === 'string'
        ? msg.criteresExtraits.questionDeRelance
        : undefined);
    const aiMsg: ChatMessageItem = {
      id: msg.id || nextSequenceId('ai'),
      sender: 'ai',
      text: msg.contenu || msg.reponse || '',
      timestamp: formatTimeLabel(),
      questionDeRelance: relance,
      suggestedProperties: msg.annoncesSuggeres && msg.annoncesSuggeres.length > 0 ? msg.annoncesSuggeres : undefined,
    };
    setMessages((prev) => [...prev, aiMsg]);
  };

  const handleSendMessage = async (textToSend?: string) => {
    const query = textToSend || inputText;
    if (!query.trim() || isSending) return;

    const sendGen = generationRef.current;
    const sendController = new AbortController();
    sendAbortControllerRef.current = sendController;

    const userMsg: ChatMessageItem = {
      id: nextSequenceId('user'),
      sender: 'user',
      text: query,
      timestamp: formatTimeLabel(),
    };

    setMessages((prev) => [...prev, userMsg]);
    if (!textToSend) setInputText('');
    setIsSending(true);

    try {
      let currentConvId = conversationId;

      // 1. Create conversation on first user message if not already created
      if (!currentConvId) {
        const newConv = await apiFetch<{ conversationId: string; dateCreation: string }>('/api/v1/ia/conversations', {
          method: 'POST',
          signal: sendController.signal,
        });

        if (!isSessionStillValid(sendGen, currentUserId, sendController.signal)) {
          return;
        }

        currentConvId = newConv.conversationId;
        setConversationId(currentConvId);
        if (typeof window !== 'undefined') {
          localStorage.setItem(storageKey, currentConvId);
        }
      }

      // 2. Send message to conversation
      const assistantMsg = await apiFetch<LegacyMessageIADTO>(`/api/v1/ia/conversations/${currentConvId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ message: query }),
        signal: sendController.signal,
      });

      if (!isSessionStillValid(sendGen, currentUserId, sendController.signal)) {
        return;
      }

      addAssistantMessage(assistantMsg);
    } catch (err: unknown) {
      if (
        !isSessionStillValid(sendGen, currentUserId, sendController.signal) ||
        (err instanceof Error && err.name === 'AbortError')
      ) {
        return;
      }

      const status = (err as { status?: number } | null)?.status;
      const apiMsg = err instanceof Error && err.message ? err.message : '';
      let displayText =
        "Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique.";
      if (status === 429) {
        displayText =
          apiMsg ||
          'Limite de requêtes IA atteinte. Veuillez patienter quelques instants avant de renvoyer un message.';
      } else if (status === 400) {
        displayText =
          apiMsg || 'Message invalide (maximum 500 caractères autorisés).';
      }

      const errorMsg: ChatMessageItem = {
        id: nextSequenceId('err'),
        sender: 'ai',
        text: displayText,
        timestamp: formatTimeLabel(),
        isError: true,
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      if (sendAbortControllerRef.current === sendController) {
        sendAbortControllerRef.current = null;
      }
      if (isSessionStillValid(sendGen, currentUserId, sendController.signal)) {
        setIsSending(false);
      }
    }
  };

  return (
    <div
      className={
        isHome
          ? 'fixed bottom-3 right-3 sm:bottom-4 sm:right-4 z-40 max-w-[calc(100vw-1.5rem)]'
          : 'fixed bottom-3 right-3 z-40 max-w-[calc(100vw-1.5rem)]'
      }
    >
      
      {/* Floating Launcher Button (compact across all routes; icon-only FAB on !isHome keeps right-side cards & table actions uncovered) */}
      {!isOpen && (
        <motion.button
          onClick={() => setIsOpen(true)}
          whileHover={{ scale: shouldReduceMotion ? 1 : 1.05 }}
          whileTap={{ scale: shouldReduceMotion ? 1 : 0.95 }}
          aria-label="Ouvrir l'assistant IA E-Courtier"
          title="Samba IA — Ouvrir l'assistant immobilier"
          className={
            isHome
              ? 'group relative flex items-center justify-center sm:justify-start gap-2 w-11 h-11 sm:w-auto sm:h-auto sm:px-3.5 sm:py-2 rounded-full bg-[#B85C42] text-white font-display font-bold shadow-[0_10px_24px_-6px_rgba(184,92,66,0.55)] hover:bg-[#9D4A32] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] focus-visible:ring-offset-2'
              : 'group relative flex items-center justify-center w-11 h-11 rounded-full bg-[#B85C42] text-white font-display font-bold shadow-[0_10px_24px_-6px_rgba(184,92,66,0.55)] hover:bg-[#9D4A32] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] focus-visible:ring-offset-2'
          }
        >
          <div className="w-7 h-7 rounded-full bg-white/20 flex items-center justify-center shrink-0">
            <Sparkles className="w-4 h-4 text-white animate-spin-slow" />
          </div>
          {isHome && <span className="hidden sm:inline text-xs font-bold tracking-tight">Samba IA</span>}
          <span className="sr-only">Assistant IA E-Courtier</span>
          <span
            className={
              isHome
                ? 'flex h-2 w-2 absolute -top-0.5 -right-0.5 sm:static sm:h-2.5 sm:w-2.5 sm:relative shrink-0'
                : 'flex h-2.5 w-2.5 absolute -top-0.5 -right-0.5 shrink-0'
            }
          >
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-ocher-400 opacity-75"></span>
            <span
              className={
                isHome
                  ? 'relative inline-flex rounded-full h-2 w-2 sm:h-2.5 sm:w-2.5 bg-ocher-500 border border-[#B85C42] sm:border-0'
                  : 'relative inline-flex rounded-full h-2.5 w-2.5 bg-ocher-500 border border-[#B85C42]'
              }
            ></span>
          </span>
        </motion.button>
      )}

      {/* Chat Window Panel */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: shouldReduceMotion ? 0 : 20, scale: shouldReduceMotion ? 1 : 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: shouldReduceMotion ? 0 : 15, scale: shouldReduceMotion ? 1 : 0.95 }}
            transition={{ duration: shouldReduceMotion ? 0.05 : 0.22, ease: [0.25, 1, 0.5, 1] }}
            className="w-[calc(100vw-1.5rem)] max-w-[420px] sm:w-[420px] h-[min(78dvh,540px)] max-h-[calc(100dvh-1.5rem)] sm:h-[580px] bg-white rounded-2xl sm:rounded-3xl shadow-warm-hover border border-argile-200 flex flex-col overflow-hidden"
          >
            
            {/* Header */}
            <div className="p-3 sm:p-4 bg-sahel-800 text-white flex items-center justify-between gap-2">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-terracotta-500 flex items-center justify-center text-white shadow-terracotta-glow">
                  <Bot className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-1.5">
                    <h3 className="font-display font-bold text-sm text-white">Samba — IA Immobilier</h3>
                    <span className="bg-sahel-600 text-sahel-100 text-[10px] font-bold px-1.5 py-0.5 rounded">SN</span>
                  </div>
                  <p className="text-[11px] text-sahel-200 flex items-center gap-1">
                    <ShieldCheck className="w-3 h-3 text-ocher-400" /> Annonces & Cautions vérifiées
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={handleResetConversation}
                  title="Nouvelle conversation"
                  aria-label="Nouvelle conversation"
                  className="p-1.5 rounded-full hover:bg-sahel-700 text-sahel-200 transition-colors"
                >
                  <RotateCcw className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setIsOpen(false)}
                  aria-label="Fermer l'assistant"
                  className="p-1.5 rounded-full hover:bg-sahel-700 text-sahel-200 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Messages Feed */}
            <div className="flex-1 p-4 overflow-y-auto space-y-4 bg-sand-100/60">
              {messages.map((msg) => (
                <motion.div
                  key={msg.id}
                  initial={{ opacity: 0, y: shouldReduceMotion ? 0 : 8, scale: shouldReduceMotion ? 1 : 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  transition={{ duration: 0.18 }}
                  className={`flex gap-2.5 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.sender === 'ai' && (
                    <div className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 mt-1 ${msg.isError ? 'bg-red-500 text-white' : 'bg-sahel-800 text-white'}`}>
                      {msg.isError ? <AlertCircle className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                    </div>
                  )}
                  
                  <div className="space-y-2 max-w-[82%]">
                    <div
                      className={`p-3.5 rounded-2xl text-xs leading-relaxed ${
                        msg.sender === 'user'
                          ? 'bg-terracotta-500 text-white rounded-br-none shadow-terracotta-glow'
                          : msg.isError
                          ? 'bg-red-50 text-red-900 rounded-bl-none border border-red-200 shadow-sm'
                          : 'bg-white text-charcoal-800 rounded-bl-none border border-argile-200 shadow-warm-card'
                      }`}
                    >
                      <p>{msg.text}</p>
                      {msg.questionDeRelance && (
                        <p className="mt-2 pt-2 border-t border-argile-200 font-semibold text-terracotta-600">
                          ❓ {msg.questionDeRelance}
                        </p>
                      )}
                      <span className={`block text-[10px] mt-1 text-right ${msg.sender === 'user' ? 'text-terracotta-200' : 'text-charcoal-400'}`}>
                        {msg.timestamp}
                      </span>
                    </div>

                    {/* Shortlist Mini Cards inside Chat */}
                    {msg.suggestedProperties && (
                      <div className="space-y-2 pt-1">
                        {msg.suggestedProperties.map((prop) => (
                            <Link
                              key={prop.id}
                              to={`/annonce/${prop.id}`}
                              onClick={() => setIsOpen(false)}
                              className="block bg-white p-2.5 rounded-xl border border-argile-300 hover:border-terracotta-400 transition-all shadow-sm group"
                            >
                              <div className="flex gap-2.5 items-center">
                                <PropertyVisual
                                  mode="thumbnail"
                                  rawUrl={prop.photos?.[0]?.url}
                                  listingId={prop.id}
                                  type={prop.type}
                                  title={prop.titre}
                                  quartier={prop.quartier}
                                  className="w-14 h-14 rounded-lg shrink-0"
                                  showIllustrativeBadge={true}
                                  illustrativeBadgeCompact={true}
                                />
                                <div className="flex-1 min-w-0 text-left">
                                  <h4 className="font-display font-bold text-xs text-charcoal-900 truncate group-hover:text-terracotta-600">
                                    {prop.titre}
                                  </h4>
                                  <p className="text-[11px] font-extrabold text-terracotta-600">
                                    {prop.prix.toLocaleString('fr-FR')} FCFA {prop.modeTransaction === 'LOCATION' ? '/ mois' : ''}
                                  </p>
                                  <div className="flex items-center gap-1 text-[10px] text-sahel-800 font-semibold mt-0.5">
                                    <CheckCircle2 className="w-3 h-3 text-sahel-600" />
                                    <span>{prop.quartier}, {prop.ville}</span>
                                  </div>
                                </div>
                              </div>
                            </Link>
                        ))}
                      </div>
                    )}
                  </div>

                  {msg.sender === 'user' && (
                    <div className="w-7 h-7 rounded-full bg-terracotta-100 text-terracotta-800 flex items-center justify-center shrink-0 mt-1">
                      <User className="w-4 h-4" />
                    </div>
                  )}
                </motion.div>
              ))}

              {isSending && (
                <motion.div
                  initial={{ opacity: 0, y: shouldReduceMotion ? 0 : 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="flex gap-2.5 items-center text-xs text-charcoal-600 bg-white p-3 rounded-2xl rounded-bl-none border border-argile-200 w-fit shadow-xs"
                >
                  <div className="w-6 h-6 rounded-full bg-sahel-800 text-white flex items-center justify-center shrink-0">
                    <Bot className="w-3.5 h-3.5" />
                  </div>
                  <span className="font-semibold text-[11px] text-sahel-800 mr-1">Samba réfléchit</span>
                  <div className="flex items-center gap-1">
                    <motion.span
                      className="w-1.5 h-1.5 rounded-full bg-terracotta-500"
                      animate={shouldReduceMotion ? {} : { y: [0, -4, 0] }}
                      transition={{ duration: 0.6, repeat: Infinity, repeatDelay: 0.1, ease: 'easeInOut' }}
                    />
                    <motion.span
                      className="w-1.5 h-1.5 rounded-full bg-sahel-500"
                      animate={shouldReduceMotion ? {} : { y: [0, -4, 0] }}
                      transition={{ duration: 0.6, repeat: Infinity, delay: 0.15, repeatDelay: 0.1, ease: 'easeInOut' }}
                    />
                    <motion.span
                      className="w-1.5 h-1.5 rounded-full bg-ocher-500"
                      animate={shouldReduceMotion ? {} : { y: [0, -4, 0] }}
                      transition={{ duration: 0.6, repeat: Infinity, delay: 0.3, repeatDelay: 0.1, ease: 'easeInOut' }}
                    />
                  </div>
                </motion.div>
              )}
            </div>

            {/* Suggestion Chips */}
            <div className="px-3 py-2 bg-white border-t border-argile-200 overflow-x-auto no-scrollbar flex items-center gap-1.5">
              {suggestions.map((chip, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSendMessage(chip)}
                  className="px-2.5 py-1 rounded-full bg-sand-200 hover:bg-terracotta-100 text-[11px] font-semibold text-charcoal-700 hover:text-terracotta-800 whitespace-nowrap transition-colors border border-argile-200"
                >
                  {chip}
                </button>
              ))}
            </div>

            {/* Input Box */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleSendMessage();
              }}
              className="p-3 bg-white border-t border-argile-200 flex items-center gap-2"
            >
              <input
                type="text"
                maxLength={500}
                placeholder="Posez votre question en français wolofisé..."
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                disabled={isSending}
                className="flex-1 px-3.5 py-2.5 bg-sand-100 text-xs text-charcoal-900 rounded-xl border border-argile-300 focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 disabled:opacity-50"
              />
              <Button type="submit" variant="primary" size="sm" disabled={isSending} className="rounded-xl px-3 py-2.5">
                <Send className="w-4 h-4" />
              </Button>
            </form>

          </motion.div>
        )}
      </AnimatePresence>

    </div>
  );
};
