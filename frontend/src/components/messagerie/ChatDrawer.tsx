import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X,
  Send,
  WifiOff,
  CheckCheck,
  Building2,
  User,
  ShieldCheck,
  AlertCircle,
} from 'lucide-react';
import { useConversationSocket } from '../../hooks/useConversationSocket';
import { ConversationChatDTO } from '../../types';
import { useAuthStore } from '../../store/useAuthStore';
import { messagerieService } from '../../services/messagerie.service';

interface ChatDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  conversation: ConversationChatDTO | null;
  onConversationUpdated?: () => void;
}

export const ChatDrawer: React.FC<ChatDrawerProps> = ({
  isOpen,
  onClose,
  conversation,
  onConversationUpdated,
}) => {
  const { utilisateur } = useAuthStore();
  const [inputContent, setInputContent] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const conversationId = conversation?.id;

  const {
    messages,
    isConnected,
    isOffline,
    error,
    sendMessage,
  } = useConversationSocket(conversationId);

  // Determine interlocutor (locataire vs proprietaire)
  const isUserLocataire = utilisateur?.id === conversation?.locataire?.id;
  const interlocuteur = isUserLocataire
    ? conversation?.proprietaire
    : conversation?.locataire;

  const interlocuteurNom = interlocuteur
    ? `${interlocuteur.prenom} ${interlocuteur.nom}`
    : 'Interlocuteur';

  const interlocuteurRole = isUserLocataire ? 'Propriétaire / Agent' : 'Locataire candidat';

  // Mark as read when conversation drawer opens
  useEffect(() => {
    if (isOpen && conversationId) {
      messagerieService.marquerCommeLu(conversationId).then(() => {
        if (onConversationUpdated) onConversationUpdated();
      }).catch(console.error);
    }
  }, [isOpen, conversationId, onConversationUpdated]);

  // Auto-scroll to bottom of messages
  useEffect(() => {
    if (isOpen && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputContent.trim()) return;

    const success = await sendMessage(inputContent);
    if (success) {
      setInputContent('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 overflow-hidden bg-slate-900/40 backdrop-blur-xs flex justify-end">
        {/* Backdrop click */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="absolute inset-0"
          onClick={onClose}
        />

        {/* Slide-over Drawer Panel */}
        <motion.div
          initial={{ x: '100%' }}
          animate={{ x: 0 }}
          exit={{ x: '100%' }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          className="relative w-full max-w-lg bg-white dark:bg-slate-900 h-full shadow-2xl flex flex-col z-10"
        >
          {/* Header */}
          <div className="px-5 py-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-white/80 dark:bg-slate-900/80 backdrop-blur-md sticky top-0">
            <div className="flex items-center gap-3">
              <div className="relative">
                {interlocuteur?.photoProfilUrl ? (
                  <img
                    src={interlocuteur.photoProfilUrl}
                    alt={interlocuteurNom}
                    className="w-10 h-10 rounded-full object-cover ring-2 ring-amber-500/20"
                  />
                ) : (
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center text-white font-semibold shadow-sm">
                    {interlocuteurNom.charAt(0)}
                  </div>
                )}
                <span
                  className={`absolute bottom-0 right-0 w-3 h-3 rounded-full ring-2 ring-white dark:ring-slate-900 ${
                    isConnected ? 'bg-emerald-500' : 'bg-slate-400'
                  }`}
                  title={isConnected ? 'En ligne' : 'Hors ligne'}
                />
              </div>

              <div>
                <h3 className="font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-1.5 text-base">
                  {interlocuteurNom}
                  <ShieldCheck className="w-4 h-4 text-amber-600 dark:text-amber-400 inline" />
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 flex items-center gap-1">
                  <User className="w-3 h-3" />
                  {interlocuteurRole}
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="p-2 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Annonce Banner context */}
          {conversation?.annonceTitre && (
            <div className="px-5 py-2.5 bg-amber-50/80 dark:bg-amber-950/30 border-b border-amber-100 dark:border-amber-900/40 flex items-center gap-2 text-xs text-amber-900 dark:text-amber-200">
              <Building2 className="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0" />
              <span className="font-medium truncate">Annonce : {conversation.annonceTitre}</span>
            </div>
          )}

          {/* Offline network banner */}
          {isOffline && (
            <div className="px-4 py-2 bg-amber-500 text-white text-xs flex items-center justify-between font-medium">
              <span className="flex items-center gap-1.5">
                <WifiOff className="w-4 h-4" />
                Connexion WebSocket interrompue. Reconnexion automatique...
              </span>
            </div>
          )}

          {/* Error banner */}
          {error && (
            <div className="px-4 py-2 bg-rose-500 text-white text-xs flex items-center gap-1.5 font-medium">
              <AlertCircle className="w-4 h-4" />
              {error}
            </div>
          )}

          {/* Messages Body */}
          <div className="flex-1 overflow-y-auto p-5 space-y-3.5 bg-slate-50/50 dark:bg-slate-950/50 min-h-0">
            {messages.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
                <div className="w-12 h-12 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 flex items-center justify-center mb-3">
                  <Building2 className="w-6 h-6" />
                </div>
                <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Démarrez la conversation
                </p>
                <p className="text-xs text-slate-500 mt-1 max-w-xs">
                  Posez vos questions sur le logement, organisez une visite ou demandez des précisions.
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
                    initial={{ opacity: 0, y: 12, scale: 0.98 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    transition={{ duration: 0.2, ease: 'easeOut' }}
                    className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-[82%] rounded-2xl px-4 py-3 shadow-xs text-sm ${
                        isMe
                          ? 'bg-amber-600 text-white rounded-br-xs'
                          : 'bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 border border-slate-100 dark:border-slate-700 rounded-bl-xs'
                      }`}
                    >
                      <p className="whitespace-pre-wrap break-words leading-relaxed">{msg.contenu}</p>
                      <div
                        className={`flex items-center justify-end gap-1 mt-1 text-[10px] ${
                          isMe ? 'text-amber-100' : 'text-slate-400 dark:text-slate-500'
                        }`}
                      >
                        <span>{timeStr}</span>
                        {isMe && (
                          <CheckCheck
                            className={`w-3.5 h-3.5 ${
                              msg.lu ? 'text-amber-200 font-bold' : 'opacity-70'
                            }`}
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

          {/* Footer Input Area */}
          <div className="p-4 border-t border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900">
            <form onSubmit={handleSend} className="space-y-2">
              <div className="relative flex items-center">
                <textarea
                  value={inputContent}
                  onChange={(e) => setInputContent(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Écrivez votre message..."
                  maxLength={1000}
                  rows={2}
                  className="w-full resize-none rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60 p-3 pr-12 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:border-amber-500 focus:bg-white dark:focus:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-amber-500/20 transition-all"
                />
                <button
                  type="submit"
                  disabled={!inputContent.trim()}
                  className="absolute right-2.5 bottom-2.5 p-2 rounded-lg bg-amber-600 hover:bg-amber-700 text-white disabled:opacity-40 disabled:hover:bg-amber-600 transition-colors shadow-xs"
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-400 px-1">
                <span>Appuyez sur Entrée pour envoyer</span>
                <span className={inputContent.length > 900 ? 'text-amber-600 font-semibold' : ''}>
                  {inputContent.length} / 1000
                </span>
              </div>
            </form>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
