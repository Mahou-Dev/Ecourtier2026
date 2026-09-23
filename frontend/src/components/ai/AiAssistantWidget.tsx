import React, { useState } from 'react';
import { Sparkles, X, Send, Bot, User, ShieldCheck, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '../ui/Button';
import { apiFetch, getPhotoUrl } from '../../services/api';
import { AnnonceDTO, MessageIAResponseDTO } from '../../types';
import { Link } from 'react-router-dom';

export interface ChatMessageItem {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  questionDeRelance?: string;
  suggestedProperties?: AnnonceDTO[];
  isError?: boolean;
}

export const AiAssistantWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [inputText, setInputText] = useState('');
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);

  const [messages, setMessages] = useState<ChatMessageItem[]>([
    {
      id: 'msg-welcome',
      sender: 'ai',
      text: 'Na nga def ! Je suis Samba, votre assistant IA E-Courtier. Dites-moi quel logement vous cherchez (ville, budget, type, meublé)...',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);

  const suggestions = [
    'Appartement 3 pièces à Nema < 160 000 FCFA',
    'Studio meublé à Dakar',
    'Villa avec piscine à Saly',
  ];

  const handleSendMessage = async (textToSend?: string) => {
    const query = textToSend || inputText;
    if (!query.trim() || isSending) return;

    const userMsg: ChatMessageItem = {
      id: `user-${Date.now()}`,
      sender: 'user',
      text: query,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
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
        });
        currentConvId = newConv.conversationId;
        setConversationId(currentConvId);
      }

      // 2. Send message to conversation
      const assistantMsg = await apiFetch<MessageIAResponseDTO>(`/api/v1/ia/conversations/${currentConvId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ message: query }),
      });
      addAssistantMessage(assistantMsg);
    } catch (err: any) {
      const errorMsg: ChatMessageItem = {
        id: `err-${Date.now()}`,
        sender: 'ai',
        text: "Désolé, l'assistant IA est actuellement indisponible ou rencontre un problème réseau. Vous pouvez utiliser la recherche classique.",
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        isError: true,
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsSending(false);
    }
  };

  const addAssistantMessage = (msg: MessageIAResponseDTO) => {
    const aiMsg: ChatMessageItem = {
      id: msg.id || `ai-${Date.now()}`,
      sender: 'ai',
      text: msg.contenu,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      questionDeRelance: msg.questionDeRelance,
      suggestedProperties: msg.annoncesSuggeres && msg.annoncesSuggeres.length > 0 ? msg.annoncesSuggeres : undefined,
    };
    setMessages((prev) => [...prev, aiMsg]);
  };

  return (
    <div className="fixed bottom-20 sm:bottom-5 right-5 z-50">
      
      {/* Floating Launcher Button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className="group relative flex items-center gap-3 px-4 py-3 rounded-full bg-terracotta-500 text-white font-display font-bold shadow-terracotta-glow hover:bg-terracotta-600 transition-all duration-300 hover:scale-105"
        >
          <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-white animate-spin-slow" />
          </div>
          <span className="text-sm">Assistant IA E-Courtier</span>
          <span className="flex h-3 w-3 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-ocher-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-3 w-3 bg-ocher-500"></span>
          </span>
        </button>
      )}

      {/* Chat Window Panel */}
      {isOpen && (
        <div className="w-[calc(100vw-2.5rem)] max-w-[420px] sm:w-[420px] h-[500px] sm:h-[580px] bg-white rounded-3xl shadow-warm-hover border border-argile-200 flex flex-col overflow-hidden animate-in fade-in slide-in-from-bottom-5">
          
          {/* Header */}
          <div className="p-4 bg-sahel-800 text-white flex items-center justify-between">
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
            <button
              onClick={() => setIsOpen(false)}
              className="p-1.5 rounded-full hover:bg-sahel-700 text-sahel-200 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Messages Feed */}
          <div className="flex-1 p-4 overflow-y-auto space-y-4 bg-sand-100/60">
            {messages.map((msg) => (
              <div
                key={msg.id}
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
                      {msg.suggestedProperties.map((prop) => {
                        const firstPhoto = prop.photos && prop.photos.length > 0 ? getPhotoUrl(prop.photos[0].url) : getPhotoUrl();
                        return (
                          <Link
                            key={prop.id}
                            to={`/annonce/${prop.id}`}
                            onClick={() => setIsOpen(false)}
                            className="block bg-white p-2.5 rounded-xl border border-argile-300 hover:border-terracotta-400 transition-all shadow-sm group"
                          >
                            <div className="flex gap-2.5 items-center">
                              <img src={firstPhoto} alt={prop.titre} className="w-14 h-14 object-cover rounded-lg shrink-0" />
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
                        );
                      })}
                    </div>
                  )}
                </div>

                {msg.sender === 'user' && (
                  <div className="w-7 h-7 rounded-full bg-terracotta-100 text-terracotta-800 flex items-center justify-center shrink-0 mt-1">
                    <User className="w-4 h-4" />
                  </div>
                )}
              </div>
            ))}

            {isSending && (
              <div className="flex gap-2.5 items-center text-xs text-charcoal-500">
                <div className="w-7 h-7 rounded-full bg-sahel-800 text-white flex items-center justify-center shrink-0">
                  <Bot className="w-4 h-4 animate-spin" />
                </div>
                <span>Samba réfléchit...</span>
              </div>
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

        </div>
      )}

    </div>
  );
};
