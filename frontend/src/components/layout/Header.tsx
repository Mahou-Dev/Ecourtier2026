import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Home,
  Sparkles,
  Search,
  LayoutDashboard,
  User,
  LogOut,
  Heart,
  MessageSquare,
  ShieldCheck,
  Menu,
  UserPlus,
  MapPin,
  Lock,
  ChevronRight,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'framer-motion';
import { Button } from '../ui/Button';
import { Drawer } from '../ui/Drawer';
import { Avatar } from '../ui/Avatar';
import { useAuthStore } from '../../store/useAuthStore';
import { apiFetch } from '../../services/api';
import { messagerieService } from '../../services/messagerie.service';
import { getRoleLabel } from '../../lib/roleLabels';
import { toast } from 'sonner';

export const Header: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { utilisateur, logout } = useAuthStore();
  const isHome = location.pathname === '/';
  const [isScrolled, setIsScrolled] = useState(false);
  const routeKey = `${location.pathname}${location.search}`;
  const [openRouteKey, setOpenRouteKey] = useState<string | null>(null);
  const mobileMenuOpen = openRouteKey === routeKey;
  const setMobileMenuOpen = (open: boolean) => setOpenRouteKey(open ? routeKey : null);

  useEffect(() => {
    if (!isHome) return;
    const onScroll = () => setIsScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [isHome]);

  const { data: favorisData } = useQuery<{ count?: number; totalElements?: number }>({
    queryKey: ['favoris-count'],
    queryFn: () =>
      apiFetch<{ count?: number; totalElements?: number }>('/api/v1/utilisateurs/moi/favoris/count'),
    enabled: !!utilisateur,
  });

  const { data: messagesUnreadCount } = useQuery({
    queryKey: ['messages-unread-count'],
    queryFn: () => messagerieService.compterMessagesNonLus(),
    enabled: !!utilisateur,
    refetchInterval: 10000,
  });

  const favorisCount =
    typeof favorisData?.count === 'number'
      ? favorisData.count
      : typeof favorisData?.totalElements === 'number'
        ? favorisData.totalElements
        : 0;
  const unreadCount = messagesUnreadCount || 0;

  const handleLogout = async () => {
    setMobileMenuOpen(false);
    await logout();
    toast.success('Déconnexion réussie');
    navigate('/');
  };

  return (
    <>
      <header
        className={
          isHome
            ? `sticky top-0 z-40 transition-all duration-200 border-b ${
                isScrolled
                  ? 'bg-[#F5F0E8]/95 backdrop-blur-md border-[#E2DACB] shadow-[0_8px_24px_-12px_rgba(37,42,41,0.12)]'
                  : 'bg-[#F5F0E8]/90 backdrop-blur-md border-[#E2DACB]/80'
              }`
            : 'sticky top-0 z-40 bg-sand-100/90 backdrop-blur-md border-b border-argile-200/80 transition-all'
        }
      >
        <div
          className={`max-w-7xl mx-auto px-2.5 sm:px-6 lg:px-8 flex items-center justify-between gap-2 ${
            isHome ? 'h-16 sm:h-[72px]' : 'h-16 sm:h-20'
          }`}
        >
          
          {/* Brand Logo */}
          <Link
            to="/"
            className="flex items-center gap-1.5 sm:gap-2.5 group shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] rounded-xl"
          >
            <div
              className={`rounded-xl flex items-center justify-center text-white group-hover:scale-105 transition-transform ${
                isHome
                  ? 'w-8 h-8 sm:w-9 sm:h-9 bg-[#B85C42] shadow-[0_6px_18px_-4px_rgba(184,92,66,0.42)]'
                  : 'w-8 h-8 sm:w-11 sm:h-11 bg-terracotta-500 shadow-terracotta-glow'
              }`}
            >
              <Home className="w-4 h-4 sm:w-5 sm:h-5" />
            </div>
            <div>
              <div className="flex items-center gap-1 sm:gap-1.5">
                <span
                  className={`font-display font-extrabold tracking-tight whitespace-nowrap ${
                    isHome ? 'text-base xs:text-lg sm:text-[22px] text-[#252A29]' : 'text-base xs:text-lg sm:text-2xl text-charcoal-800'
                  }`}
                >
                  E-Courtier
                </span>
                <span
                  className={`text-[9px] sm:text-[10px] font-extrabold px-1 sm:px-1.5 py-0.5 rounded tracking-wider uppercase ${
                    isHome ? 'bg-[#D5AD72] text-[#1C352E]' : 'bg-ocher-500 text-charcoal-900'
                  }`}
                >
                  SN
                </span>
              </div>
            </div>
          </Link>

          {/* Center Nav Links (Desktop xl+) */}
          <nav
            className={`hidden xl:flex items-center whitespace-nowrap ${
              isHome ? 'gap-5 text-[13px] font-semibold text-[#252A29]' : 'gap-6 text-xs font-bold text-charcoal-700'
            }`}
            aria-label="Navigation principale"
          >
            <Link
              to="/recherche"
              className={`flex items-center gap-1.5 hover:text-[#B85C42] transition-colors whitespace-nowrap rounded-lg px-2 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] ${
                location.pathname === '/recherche' ? 'text-terracotta-600 font-extrabold' : ''
              }`}
            >
              <Search className="w-3.5 h-3.5 text-[#B85C42]" />
              <span>Rechercher</span>
            </Link>

            {isHome && !utilisateur && (
              <>
                <a
                  href="#annonces-verifiees"
                  className="hover:text-[#B85C42] transition-colors whitespace-nowrap rounded-lg px-2 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
                >
                  Biens vérifiés
                </a>
                <a
                  href="#territoires-senegal"
                  className="hover:text-[#B85C42] transition-colors whitespace-nowrap rounded-lg px-2 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
                >
                  Quartiers
                </a>
                <a
                  href="#comment-ca-marche"
                  className="hover:text-[#B85C42] transition-colors whitespace-nowrap rounded-lg px-2 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
                >
                  Fonctionnement
                </a>
                <a
                  href="#confiance-services"
                  className="hover:text-[#B85C42] transition-colors whitespace-nowrap rounded-lg px-2 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
                >
                  Confiance
                </a>
              </>
            )}

            {utilisateur && (
              <>
                <Link
                  to="/favoris"
                  className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors whitespace-nowrap ${
                    location.pathname === '/favoris' ? 'text-terracotta-600 font-extrabold' : ''
                  }`}
                >
                  <Heart className="w-3.5 h-3.5 text-rose-500 fill-rose-500/20" />
                  <span>Favoris</span>
                  {favorisCount > 0 && (
                    <AnimatePresence mode="popLayout">
                      <motion.span
                        key={favorisCount}
                        initial={{ scale: 0.6, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.6, opacity: 0 }}
                        transition={{ type: 'spring', stiffness: 500, damping: 20 }}
                        className="bg-terracotta-500 text-white text-[10px] font-extrabold px-1.5 py-0.5 rounded-full min-w-[18px] text-center shadow-xs"
                      >
                        {favorisCount}
                      </motion.span>
                    </AnimatePresence>
                  )}
                </Link>

                <Link
                  to="/dashboard?tab=messages"
                  className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors whitespace-nowrap ${
                    location.pathname === '/dashboard' && location.search.includes('messages') ? 'text-terracotta-600 font-extrabold' : ''
                  }`}
                >
                  <MessageSquare className="w-3.5 h-3.5 text-terracotta-500" />
                  <span>Messages</span>
                  {unreadCount > 0 && (
                    <AnimatePresence mode="popLayout">
                      <motion.span
                        key={unreadCount}
                        initial={{ scale: 0.6, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.6, opacity: 0 }}
                        transition={{ type: 'spring', stiffness: 500, damping: 20 }}
                        className="bg-terracotta-500 text-white text-[10px] font-extrabold px-1.5 py-0.5 rounded-full min-w-[18px] text-center shadow-xs"
                      >
                        {unreadCount}
                      </motion.span>
                    </AnimatePresence>
                  )}
                </Link>
              </>
            )}

            {utilisateur?.role === 'ADMIN' && (
              <Link
                to="/admin/dashboard"
                className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors whitespace-nowrap ${
                  location.pathname.startsWith('/admin') ? 'text-terracotta-600 font-extrabold' : ''
                }`}
              >
                <ShieldCheck className="w-3.5 h-3.5 text-terracotta-500" />
                <span>Administration</span>
              </Link>
            )}

            {utilisateur?.role === 'LOCATAIRE' && (
              <Link
                to="/dashboard?tab=visites"
                className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors whitespace-nowrap ${
                  location.pathname === '/dashboard' ? 'text-terracotta-600 font-extrabold' : ''
                }`}
              >
                <LayoutDashboard className="w-3.5 h-3.5 text-sahel-800" />
                <span>Mes visites & dossiers</span>
              </Link>
            )}

            {(utilisateur?.role === 'PROPRIETAIRE' || utilisateur?.role === 'AGENT') && (
              <Link
                to="/dashboard"
                className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors whitespace-nowrap ${
                  location.pathname === '/dashboard' ? 'text-terracotta-600 font-extrabold' : ''
                }`}
              >
                <LayoutDashboard className="w-3.5 h-3.5 text-sahel-800" />
                <span>Espace Partenaire</span>
              </Link>
            )}
          </nav>

          {/* Auth & Action Buttons */}
          <div className="flex items-center gap-1 xs:gap-1.5 sm:gap-3 shrink-0">
            {utilisateur ? (
              <div className="flex items-center gap-1 xs:gap-2">
                <Link
                  to="/mon-compte"
                  title="Mon compte"
                  aria-label="Mon compte"
                  className={`flex items-center justify-center sm:justify-start gap-2 min-w-[44px] min-h-[44px] sm:min-w-0 sm:min-h-[40px] sm:px-2.5 sm:py-1.5 rounded-xl border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] ${
                    location.pathname === '/mon-compte'
                      ? 'bg-terracotta-50 border-terracotta-400 text-terracotta-800'
                      : 'border-transparent hover:bg-sand-200/80'
                  }`}
                >
                  <User className="w-4 h-4 text-sahel-800 sm:hidden" aria-hidden="true" />
                  <div className="hidden sm:flex flex-col text-right">
                    <span className="text-xs font-bold text-charcoal-900">{utilisateur.prenom} {utilisateur.nom}</span>
                    <span className="text-[10px] text-terracotta-600 font-semibold">Mon compte &bull; {getRoleLabel(utilisateur.role)}</span>
                  </div>
                </Link>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleLogout}
                  title="Se déconnecter"
                  aria-label="Se déconnecter"
                  className="hidden sm:inline-flex min-h-[38px]"
                  leftIcon={<LogOut className="w-4 h-4 text-charcoal-600" />}
                >
                  <span className="hidden sm:inline">Déconnexion</span>
                </Button>
              </div>
            ) : (
              <>
                <Link
                  to="/connexion"
                  aria-label="Connexion"
                  className="inline-flex items-center justify-center gap-1.5 rounded-full font-display font-semibold text-xs text-charcoal-700 hover:bg-sand-200/80 min-w-[44px] min-h-[44px] sm:min-w-0 sm:min-h-[36px] px-2.5 sm:px-3 py-1.5 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-charcoal-300"
                >
                  <User className="w-4 h-4 shrink-0" aria-hidden="true" />
                  <span className="hidden sm:inline">Connexion</span>
                </Link>

                <Link
                  to="/inscription"
                  className="hidden sm:inline-flex items-center justify-center rounded-full font-display font-semibold text-xs border-2 border-terracotta-500 text-terracotta-600 hover:bg-terracotta-50 hover:border-terracotta-600 min-h-[36px] px-3 py-1.5 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-400"
                >
                  Inscription
                </Link>
              </>
            )}

            <Link
              to="/recherche"
              aria-label="Trouver un bien"
              className="inline-flex items-center justify-center gap-1.5 rounded-full font-display font-semibold text-xs bg-terracotta-500 text-white hover:bg-terracotta-600 hover:-translate-y-0.5 shadow-terracotta-glow active:translate-y-0 active:scale-[0.98] min-w-[44px] min-h-[44px] sm:min-w-0 sm:min-h-[36px] px-2.5 xs:px-3 py-1.5 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-400"
            >
              <Sparkles className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span className="hidden sm:inline">Trouver un bien</span>
              <span className="hidden xs:inline sm:hidden text-xs">Trouver</span>
            </Link>

            {/* Mobile / Tablet Menu Button (< xl) */}
            <button
              type="button"
              onClick={() => setMobileMenuOpen(true)}
              aria-label="Ouvrir le menu de navigation"
              aria-expanded={mobileMenuOpen}
              className="xl:hidden relative inline-flex items-center justify-center w-11 h-11 sm:w-10 sm:h-10 rounded-xl border border-argile-200 bg-white/80 hover:bg-white text-charcoal-800 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
            >
              <Menu className="w-5 h-5" />
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-amber-600 text-white text-[9px] font-extrabold flex items-center justify-center">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>
          </div>

        </div>
      </header>

      {/* Mobile & Tablet Navigation Drawer (< xl) */}
      <Drawer
        isOpen={mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        title="Menu E-Courtier"
        position="right"
      >
        <div className={isHome ? 'flex flex-col gap-5' : 'flex flex-col h-full justify-between gap-6'}>
          <div className="space-y-4">
            {utilisateur && (
              <Link
                to="/mon-compte"
                className={`flex items-center gap-3 p-3 min-h-[56px] rounded-xl border transition-colors ${
                  location.pathname === '/mon-compte'
                    ? 'bg-terracotta-50/70 border-terracotta-400'
                    : 'bg-sand-50 border-argile-200 hover:border-terracotta-300'
                }`}
              >
                <Avatar
                  src={utilisateur.photoProfilUrl}
                  name={`${utilisateur.prenom} ${utilisateur.nom}`}
                  size="md"
                />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-charcoal-900 truncate">
                    {utilisateur.prenom} {utilisateur.nom}
                  </p>
                  <p className="text-xs font-semibold text-terracotta-600 truncate">
                    Mon compte &bull; {getRoleLabel(utilisateur.role)}
                  </p>
                </div>
                <ChevronRight className="w-4 h-4 text-charcoal-400 shrink-0" aria-hidden="true" />
              </Link>
            )}

            {isHome && (
              <p className="px-3.5 text-[11px] font-bold uppercase tracking-wider text-[#6E675C]">
                Explorer la plateforme
              </p>
            )}

            <nav className="flex flex-col gap-1.5" aria-label="Menu mobile">
              <Link
                to="/recherche"
                className={`flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                  location.pathname === '/recherche'
                    ? 'bg-terracotta-50 text-terracotta-700'
                    : 'text-charcoal-700 hover:bg-sand-100'
                }`}
              >
                <span className="flex items-center gap-2.5">
                  <Search className="w-4 h-4 text-terracotta-500" />
                  <span>Rechercher un bien</span>
                </span>
              </Link>

              {isHome && (
                <>
                  <a
                    href="#annonces-verifiees"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold text-charcoal-700 hover:bg-sand-100 transition-colors"
                  >
                    <span className="flex items-center gap-2.5">
                      <ShieldCheck className="w-4 h-4 text-[#B85C42]" />
                      <span>Biens vérifiés disponibles</span>
                    </span>
                  </a>
                  <a
                    href="#territoires-senegal"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold text-charcoal-700 hover:bg-sand-100 transition-colors"
                  >
                    <span className="flex items-center gap-2.5">
                      <MapPin className="w-4 h-4 text-[#1C352E]" />
                      <span>Quartiers recherchés</span>
                    </span>
                  </a>
                  <a
                    href="#comment-ca-marche"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold text-charcoal-700 hover:bg-sand-100 transition-colors"
                  >
                    <span className="flex items-center gap-2.5">
                      <LayoutDashboard className="w-4 h-4 text-sahel-800" />
                      <span>Comment ça fonctionne</span>
                    </span>
                  </a>
                  <a
                    href="#confiance-services"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold text-charcoal-700 hover:bg-sand-100 transition-colors"
                  >
                    <span className="flex items-center gap-2.5">
                      <Lock className="w-4 h-4 text-[#B85C42]" />
                      <span>Confiance &amp; Services</span>
                    </span>
                  </a>
                </>
              )}

              {utilisateur && (
                <>
                  <Link
                    to="/favoris"
                    className={`flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                      location.pathname === '/favoris'
                        ? 'bg-terracotta-50 text-terracotta-700'
                        : 'text-charcoal-700 hover:bg-sand-100'
                    }`}
                  >
                    <span className="flex items-center gap-2.5">
                      <Heart className="w-4 h-4 text-rose-500 fill-rose-500/20" />
                      <span>Mes favoris</span>
                    </span>
                    {favorisCount > 0 && (
                      <span className="bg-terracotta-500 text-white text-[11px] font-extrabold px-2 py-0.5 rounded-full">
                        {favorisCount}
                      </span>
                    )}
                  </Link>

                  <Link
                    to="/dashboard?tab=messages"
                    className={`flex items-center justify-between px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                      location.pathname === '/dashboard' && location.search.includes('messages')
                        ? 'bg-terracotta-50 text-terracotta-700'
                        : 'text-charcoal-700 hover:bg-sand-100'
                    }`}
                  >
                    <span className="flex items-center gap-2.5">
                      <MessageSquare className="w-4 h-4 text-amber-600" />
                      <span>Messagerie</span>
                    </span>
                    {unreadCount > 0 && (
                      <span className="bg-terracotta-500 text-white text-[11px] font-extrabold px-2 py-0.5 rounded-full">
                        {unreadCount}
                      </span>
                    )}
                  </Link>
                </>
              )}

              {utilisateur?.role === 'LOCATAIRE' && (
                <Link
                  to="/dashboard?tab=visites"
                  className={`flex items-center gap-2.5 px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                    location.pathname === '/dashboard' && !location.search.includes('messages')
                      ? 'bg-terracotta-50 text-terracotta-700'
                      : 'text-charcoal-700 hover:bg-sand-100'
                  }`}
                >
                  <LayoutDashboard className="w-4 h-4 text-sahel-800" />
                  <span>Mes visites & dossiers</span>
                </Link>
              )}

              {(utilisateur?.role === 'PROPRIETAIRE' || utilisateur?.role === 'AGENT') && (
                <Link
                  to="/dashboard"
                  className={`flex items-center gap-2.5 px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                    location.pathname === '/dashboard' && !location.search.includes('messages')
                      ? 'bg-terracotta-50 text-terracotta-700'
                      : 'text-charcoal-700 hover:bg-sand-100'
                  }`}
                >
                  <LayoutDashboard className="w-4 h-4 text-sahel-800" />
                  <span>Espace Partenaire</span>
                </Link>
              )}

              {utilisateur?.role === 'ADMIN' && (
                <Link
                  to="/admin/dashboard"
                  className={`flex items-center gap-2.5 px-3.5 py-2.5 min-h-[44px] rounded-xl text-sm font-semibold transition-colors ${
                    location.pathname.startsWith('/admin')
                      ? 'bg-terracotta-50 text-terracotta-700'
                      : 'text-charcoal-700 hover:bg-sand-100'
                  }`}
                >
                  <ShieldCheck className="w-4 h-4 text-terracotta-500" />
                  <span>Administration</span>
                </Link>
              )}
            </nav>
          </div>

          {isHome && !utilisateur && (
            <div className="rounded-2xl bg-[#FAF7F2] border border-[#E2DACB] p-4 space-y-2">
              <div className="flex items-center gap-2 text-xs font-bold text-[#1C352E]">
                <ShieldCheck className="w-4 h-4 text-[#B85C42] shrink-0" />
                <span>Parcours immobilier encadré</span>
              </div>
              <p className="text-xs text-[#4F5654] leading-relaxed">
                Pièces justificatives contrôlées, visites confirmées sans faux frais et bail numérique horodaté.
              </p>
            </div>
          )}

          {utilisateur ? (
            <div className="sm:hidden pt-4 border-t border-argile-200 space-y-2.5">
              {isHome && (
                <p className="px-1 text-[11px] font-bold uppercase tracking-wider text-[#6E675C]">
                  Mon espace
                </p>
              )}
              <Button
                variant="outline"
                size="md"
                onClick={handleLogout}
                className="w-full justify-center min-h-[44px]"
                leftIcon={<LogOut className="w-4 h-4" />}
              >
                Se déconnecter
              </Button>
            </div>
          ) : (
            <div className="pt-4 border-t border-argile-200 space-y-2.5">
              {isHome && (
                <p className="px-1 text-[11px] font-bold uppercase tracking-wider text-[#6E675C]">
                  Mon espace
                </p>
              )}
              <div className="space-y-2">
                <Link
                  to="/connexion"
                  className="w-full min-h-[44px] inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full font-display font-semibold text-sm bg-terracotta-500 text-white hover:bg-terracotta-600 shadow-terracotta-glow transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-400"
                >
                  <User className="w-4 h-4" aria-hidden="true" />
                  <span>Se connecter</span>
                </Link>
                <Link
                  to="/inscription"
                  className="w-full min-h-[44px] inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full font-display font-semibold text-sm border-2 border-terracotta-500 text-terracotta-600 hover:bg-terracotta-50 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-400"
                >
                  <UserPlus className="w-4 h-4" aria-hidden="true" />
                  <span>Créer un compte</span>
                </Link>
              </div>
            </div>
          )}
        </div>
      </Drawer>
    </>
  );
};
