import React, { useState, useEffect, useLayoutEffect, useRef } from 'react';
import { Sparkles, X, Send, Bot, User, ShieldCheck, CheckCircle2, AlertCircle, RotateCcw } from 'lucide-react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Button } from '../ui/Button';
import { PropertyVisual } from '../property/PropertyVisual';
import { apiFetch } from '../../services/api';
import {
  AnnonceDTO,
  ConversationHistoryResponseDTO,
  IaConversationCreatedDTO,
  IaMessageReponseDTO,
} from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { Link, useLocation } from 'react-router-dom';
import {
  buildConversationHeaders,
  clearStoredConversation,
  isAnonymousUser,
  loadStoredConversationId,
  persistConversation,
} from '../../services/iaSession';
import {
  IA_MESSAGE_MAX_LENGTH,
  describeMotifSecours,
  formatFcfa,
  isAbandonedConversationStatus,
  mapIaError,
  orderHistoryMessages,
} from './iaErrors';

const INPUT_MAX_LENGTH = IA_MESSAGE_MAX_LENGTH;
const INPUT_WARNING_THRESHOLD = Math.ceil(IA_MESSAGE_MAX_LENGTH * 0.9);
// ~4 lignes (text-xs / leading-4 = 16px) + padding vertical (20px) + bordures (2px)
const INPUT_MAX_HEIGHT_PX = 88;

export interface ChatMessageItem {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  questionDeRelance?: string;
  suggestedProperties?: AnnonceDTO[];
  explicationsAnnonces?: Record<string, string>;
  modeSecours?: boolean;
  motifSecours?: string;
  isError?: boolean;
  /** Instrumentation (ANO-PROD-10) : performance.now() à l'envoi de la question. */
  uiStartedAt?: number;
  /** Temps total serveur (metriques.totalMs) et part réseau estimée (aller-retour - total serveur). */
  serverTotalMs?: number;
  networkMs?: number;
}

const logUiMetric = (details: Record<string, number | undefined>) => {
  if (import.meta.env.DEV) {
    console.debug('[ia-metrics]', details);
  }
};

const nowMs = (): number => performance.now();

/** Pose `data-ui-total-ms` dès que la bulle assistant est montée (commit React). */
const measureUiTotal = (el: HTMLElement | null, msg: ChatMessageItem) => {
  if (!el || msg.uiStartedAt === undefined || el.dataset.uiTotalMs !== undefined) return;
  const uiTotalMs = Math.round(nowMs() - msg.uiStartedAt);
  el.dataset.uiTotalMs = String(uiTotalMs);
  logUiMetric({ uiTotalMs, serverTotalMs: msg.serverTotalMs, networkMs: msg.networkMs });
};

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
    // Anonyme : sessionStorage (id + token) ; authentifié : localStorage par utilisateur, sans token.
    return loadStoredConversationId(currentUserId);
  });
  const [isSending, setIsSending] = useState(false);
  const [pasteOverflow, setPasteOverflow] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
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

    const savedConvId = typeof window !== 'undefined' ? loadStoredConversationId(currentUserId) : null;
    if (!savedConvId) {
      return () => {
        genCounter.current++;
        abortController.abort();
      };
    }

    apiFetch<ConversationHistoryResponseDTO>(`/api/v1/ia/conversations/${encodeURIComponent(savedConvId)}`, {
      signal: abortController.signal,
      headers: buildConversationHeaders(currentUserId, savedConvId),
    })
      .then((history) => {
        if (!isSessionStillValid(currentGen, currentUserId, abortController.signal)) {
          return;
        }

        if (history && history.messages && history.messages.length > 0) {
          // Ordre serveur conservé (tri par `ordre` si présent) : jamais de re-tri par date.
          const restored: ChatMessageItem[] = orderHistoryMessages(history.messages).map((m, idx) => {
            const isAssistant = m.role !== 'UTILISATEUR' && m.role !== 'USER';
            return {
              id: m.id || `msg-restored-${idx}`,
              sender: isAssistant ? 'ai' : 'user',
              text: m.contenu,
              timestamp: formatTimeLabel(m.dateEnvoi),
              questionDeRelance:
                m.questionDeRelance ??
                (typeof m.criteresExtraits?.questionDeRelance === 'string'
                  ? m.criteresExtraits.questionDeRelance
                  : undefined),
              suggestedProperties:
                m.annoncesSuggeres && m.annoncesSuggeres.length > 0 ? m.annoncesSuggeres : undefined,
              explicationsAnnonces:
                m.criteresExtraits?.explicationsAnnonces && typeof m.criteresExtraits.explicationsAnnonces === 'object'
                  ? m.criteresExtraits.explicationsAnnonces
                  : undefined,
              modeSecours: isAssistant && m.criteresExtraits?.modeSecours === true,
              motifSecours:
                isAssistant && m.criteresExtraits?.modeSecours === true && typeof m.criteresExtraits.motifSecours === 'string'
                  ? m.criteresExtraits.motifSecours
                  : undefined,
            };
          });
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

        const status = (err as { status?: number } | null)?.status;
        if (isAbandonedConversationStatus(status)) {
          // Conversation abandonnée (preuve invalide / introuvable) : purge id + token, repart à neuf.
          clearStoredConversation(currentUserId, savedConvId);
          setConversationId(null);
          setMessages([createWelcomeMessage()]);
          return;
        }

        // Erreur transitoire (réseau, 5xx, 429) : on conserve la conversation, on prévient l'utilisateur.
        setMessages([
          createWelcomeMessage(),
          {
            id: 'msg-restore-error',
            sender: 'ai',
            text: "Impossible de recharger l'historique de votre conversation pour le moment. Vous pouvez continuer à écrire.",
            timestamp: formatTimeLabel(),
            isError: true,
          },
        ]);
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
    // Purge l'id stocké ET le token de l'ancienne conversation.
    clearStoredConversation(currentUserId, conversationId);
    setConversationId(null);
    setPasteOverflow(false);
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

  const addAssistantMessage = (
    msg: IaMessageReponseDTO,
    timing: { uiStartedAt: number; roundTripMs: number }
  ) => {
    const relance =
      msg.questionDeRelance ??
      (typeof msg.criteresExtraits?.questionDeRelance === 'string'
        ? msg.criteresExtraits.questionDeRelance
        : undefined);
    const explications =
      msg.criteresExtraits?.explicationsAnnonces && typeof msg.criteresExtraits.explicationsAnnonces === 'object'
        ? msg.criteresExtraits.explicationsAnnonces
        : undefined;
    const serverTotalMs =
      typeof msg.metriques?.totalMs === 'number' && Number.isFinite(msg.metriques.totalMs)
        ? msg.metriques.totalMs
        : undefined;
    const modeSecours = msg.criteresExtraits?.modeSecours === true;
    const aiMsg: ChatMessageItem = {
      id: msg.id || nextSequenceId('ai'),
      sender: 'ai',
      text: msg.contenu || msg.reponse || '',
      timestamp: formatTimeLabel(),
      questionDeRelance: relance,
      suggestedProperties: msg.annoncesSuggeres && msg.annoncesSuggeres.length > 0 ? msg.annoncesSuggeres : undefined,
      explicationsAnnonces: explications,
      modeSecours,
      motifSecours:
        modeSecours && typeof msg.criteresExtraits?.motifSecours === 'string'
          ? msg.criteresExtraits.motifSecours
          : undefined,
      uiStartedAt: timing.uiStartedAt,
      serverTotalMs,
      networkMs:
        serverTotalMs !== undefined ? Math.max(0, Math.round(timing.roundTripMs - serverTotalMs)) : undefined,
    };
    setMessages((prev) => [...prev, aiMsg]);
  };

  const handleSendMessage = async (textToSend?: string) => {
    // Le message est transmis tel que saisi (seul le trim de bord est appliqué).
    const query = (textToSend ?? inputText).trim();
    if (!query || isSending) return;
    if (query.length > INPUT_MAX_LENGTH) return;

    const uiStartedAt = nowMs();
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
    if (textToSend === undefined) {
      setInputText('');
      setPasteOverflow(false);
    }
    setIsSending(true);

    let currentConvId = conversationId;
    let conversationScoped = Boolean(currentConvId);

    try {
      // 1. Create conversation on first user message if not already created
      if (!currentConvId) {
        const newConv = await apiFetch<IaConversationCreatedDTO>('/api/v1/ia/conversations', {
          method: 'POST',
          signal: sendController.signal,
        });

        if (!isSessionStillValid(sendGen, currentUserId, sendController.signal)) {
          return;
        }

        currentConvId = newConv.conversationId;
        conversationScoped = true;
        setConversationId(currentConvId);
        // Anonyme : id + sessionToken (sessionStorage). Authentifié : id seul (localStorage), token ignoré.
        persistConversation(currentUserId, currentConvId, isAnonymousUser(currentUserId) ? newConv.sessionToken : null);
      }

      // 2. Send message to conversation (header X-IA-Session-Token pour une conversation anonyme)
      const postStartedAt = nowMs();
      const assistantMsg = await apiFetch<IaMessageReponseDTO>(
        `/api/v1/ia/conversations/${encodeURIComponent(currentConvId)}/messages`,
        {
          method: 'POST',
          body: JSON.stringify({ message: query }),
          signal: sendController.signal,
          headers: buildConversationHeaders(currentUserId, currentConvId),
        }
      );

      if (!isSessionStillValid(sendGen, currentUserId, sendController.signal)) {
        return;
      }

      addAssistantMessage(assistantMsg, {
        uiStartedAt,
        roundTripMs: nowMs() - postStartedAt,
      });
    } catch (err: unknown) {
      if (
        !isSessionStillValid(sendGen, currentUserId, sendController.signal) ||
        (err instanceof Error && err.name === 'AbortError')
      ) {
        return;
      }

      const info = mapIaError(err, { conversationScoped });
      if (info.resetConversation) {
        // 403/404 sur la conversation : abandon propre (id + token purgés), nouvelle conversation au prochain envoi.
        clearStoredConversation(currentUserId, currentConvId);
        setConversationId(null);
        if (textToSend === undefined) setInputText(query);
      }

      const errorMsg: ChatMessageItem = {
        id: nextSequenceId('err'),
        sender: 'ai',
        text: info.message,
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

  // Auto-hauteur du champ de saisie (limitée à ~4 lignes)
  useLayoutEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, INPUT_MAX_HEIGHT_PX)}px`;
  }, [inputText, isOpen]);

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value;
    if (value.length > INPUT_MAX_LENGTH) {
      // Dépassement (collage, autofill…) : coupé à la limite ET signalé explicitement.
      setInputText(value.slice(0, INPUT_MAX_LENGTH));
      setPasteOverflow(true);
      return;
    }
    setInputText(value);
    if (value.length < INPUT_MAX_LENGTH) setPasteOverflow(false);
  };

  const handleInputPaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pasted = e.clipboardData.getData('text');
    const target = e.currentTarget;
    const replaced = Math.max(0, target.selectionEnd - target.selectionStart);
    if (inputText.length - replaced + pasted.length > INPUT_MAX_LENGTH) {
      setPasteOverflow(true);
    }
  };

  const handleInputKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void handleSendMessage();
    }
  };

  const charCount = inputText.length;
  const isAtLimit = charCount >= INPUT_MAX_LENGTH;
  const isNearLimit = charCount >= INPUT_WARNING_THRESHOLD;

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
                      ref={(el) => {
                        if (msg.sender === 'ai' && !msg.isError && msg.id !== 'msg-welcome') {
                          measureUiTotal(el, msg);
                        }
                      }}
                      data-network-ms={msg.networkMs}
                      data-total-ms={msg.serverTotalMs}
                      className={`p-3.5 rounded-2xl text-xs leading-relaxed break-words ${
                        msg.sender === 'user'
                          ? 'bg-terracotta-500 text-white rounded-br-none shadow-terracotta-glow'
                          : msg.isError
                          ? 'bg-red-50 text-red-900 rounded-bl-none border border-red-200 shadow-sm'
                          : 'bg-white text-charcoal-800 rounded-bl-none border border-argile-200 shadow-warm-card'
                      }`}
                    >
                      <p className="whitespace-pre-wrap">{msg.text}</p>
                      {msg.questionDeRelance && (
                        <p className="mt-2 pt-2 border-t border-argile-200 font-semibold text-terracotta-600">
                          ❓ {msg.questionDeRelance}
                        </p>
                      )}
                      {msg.sender === 'ai' && !msg.isError && msg.modeSecours && (
                        <span
                          data-testid="ia-fallback-badge"
                          title={describeMotifSecours(msg.motifSecours)}
                          aria-label={describeMotifSecours(msg.motifSecours)}
                          className="inline-flex items-center gap-1 mt-1.5 px-2 py-0.5 rounded-full text-[10px] font-medium bg-argile-100 text-charcoal-600 border border-argile-200"
                        >
                          Mode secours local
                        </span>
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
                                    {formatFcfa(prop.prix)} {prop.modeTransaction === 'LOCATION' ? '/ mois' : ''}
                                  </p>
                                  <div className="flex items-center gap-1 text-[10px] text-sahel-800 font-semibold mt-0.5">
                                    <CheckCircle2 className="w-3 h-3 text-sahel-600" />
                                    <span>{prop.quartier}, {prop.ville}</span>
                                  </div>
                                  {msg.explicationsAnnonces?.[prop.id] && (
                                    <p className="text-[10px] text-charcoal-500 mt-1 line-clamp-2 italic">
                                      Pertinence : {msg.explicationsAnnonces[prop.id]}
                                    </p>
                                  )}
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
                void handleSendMessage();
              }}
              className="p-3 bg-white border-t border-argile-200 flex flex-col gap-1.5"
            >
              <div className="flex items-end gap-2">
                <textarea
                  ref={inputRef}
                  data-testid="ia-input"
                  rows={1}
                  maxLength={INPUT_MAX_LENGTH}
                  placeholder="Posez votre question en français wolofisé..."
                  aria-label="Message pour l'assistant IA"
                  value={inputText}
                  onChange={handleInputChange}
                  onPaste={handleInputPaste}
                  onKeyDown={handleInputKeyDown}
                  disabled={isSending}
                  className="flex-1 min-w-0 resize-none overflow-y-auto px-3.5 py-2 bg-sand-100 text-xs leading-4 text-charcoal-900 rounded-xl border border-argile-300 focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 disabled:opacity-50"
                />
                <Button type="submit" variant="primary" size="sm" disabled={isSending} className="rounded-xl px-3 py-2.5 shrink-0">
                  <Send className="w-4 h-4" />
                </Button>
              </div>
              <div className="flex items-center justify-between gap-2 text-[10px] min-h-[14px]">
                <div aria-live="polite" className="min-w-0 truncate">
                  {(isAtLimit || pasteOverflow) ? (
                    <span data-testid="ia-char-limit-message" className="font-semibold text-red-600">
                      Limite de {INPUT_MAX_LENGTH} caractères atteinte
                    </span>
                  ) : isNearLimit ? (
                    <span className="font-medium text-ocher-700">
                      Vous approchez de la limite de {INPUT_MAX_LENGTH} caractères
                    </span>
                  ) : null}
                </div>
                <span
                  data-testid="ia-char-counter"
                  className={`shrink-0 font-mono ${
                    isAtLimit ? 'text-red-600 font-bold' : isNearLimit ? 'text-ocher-700 font-semibold' : 'text-charcoal-500'
                  }`}
                >
                  {charCount}/{INPUT_MAX_LENGTH}
                </span>
              </div>
            </form>

          </motion.div>
        )}
      </AnimatePresence>

    </div>
  );
};
