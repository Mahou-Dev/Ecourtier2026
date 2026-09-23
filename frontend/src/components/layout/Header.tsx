import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Home, Sparkles, Search, LayoutDashboard, User, LogOut } from 'lucide-react';
import { Button } from '../ui/Button';
import { useAuthStore } from '../../store/useAuthStore';
import { toast } from 'sonner';

export const Header: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { utilisateur, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    toast.success('Déconnexion réussie');
    navigate('/');
  };

  return (
    <header className="sticky top-0 z-40 bg-sand-100/90 backdrop-blur-md border-b border-argile-200/80 transition-all">
      <div className="max-w-7xl mx-auto px-3 sm:px-6 lg:px-8 h-16 sm:h-20 flex items-center justify-between">
        
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-2 sm:gap-3 group shrink-0">
          <div className="w-9 h-9 sm:w-11 sm:h-11 rounded-xl bg-terracotta-500 flex items-center justify-center text-white shadow-terracotta-glow group-hover:scale-105 transition-transform">
            <Home className="w-4.5 h-4.5 sm:w-6 sm:h-6" />
          </div>
          <div>
            <div className="flex items-center gap-1">
              <span className="font-display font-extrabold text-lg sm:text-2xl text-charcoal-800 tracking-tight">
                E-Courtier
              </span>
              <span className="bg-ocher-500 text-charcoal-900 text-[9px] sm:text-[10px] font-extrabold px-1 py-0.5 rounded tracking-wider uppercase">
                SN
              </span>
            </div>
          </div>
        </Link>

        {/* Center Nav Links */}
        <nav className="hidden md:flex items-center gap-6 text-xs font-bold text-charcoal-700">
          <Link
            to="/recherche"
            className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors ${
              location.pathname === '/recherche' ? 'text-terracotta-600 font-extrabold' : ''
            }`}
          >
            <Search className="w-3.5 h-3.5 text-terracotta-500" />
            <span>Rechercher</span>
          </Link>

          {(utilisateur?.role === 'PROPRIETAIRE' || utilisateur?.role === 'AGENT' || utilisateur?.role === 'ADMIN') && (
            <Link
              to="/dashboard"
              className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors ${
                location.pathname === '/dashboard' ? 'text-terracotta-600 font-extrabold' : ''
              }`}
            >
              <LayoutDashboard className="w-3.5 h-3.5 text-sahel-800" />
              <span>Espace Partenaire</span>
            </Link>
          )}
        </nav>

        {/* Auth & Action Buttons */}
        <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
          {utilisateur ? (
            <div className="flex items-center gap-2">
              <div className="hidden sm:flex flex-col text-right">
                <span className="text-xs font-bold text-charcoal-900">{utilisateur.prenom} {utilisateur.nom}</span>
                <span className="text-[10px] text-terracotta-600 font-semibold">{utilisateur.role}</span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLogout}
                title="Se déconnecter"
                leftIcon={<LogOut className="w-4 h-4 text-charcoal-600" />}
              >
                <span className="hidden sm:inline">Déconnexion</span>
              </Button>
            </div>
          ) : (
            <>
              <Link to="/connexion">
                <Button variant="ghost" size="sm" leftIcon={<User className="w-4 h-4" />}>
                  <span className="hidden sm:inline">Connexion</span>
                </Button>
              </Link>
              
              <Link to="/inscription" className="hidden sm:inline-block">
                <Button variant="outline" size="sm">
                  Inscription
                </Button>
              </Link>
            </>
          )}
          
          <Link to="/recherche">
            <Button variant="primary" size="sm" leftIcon={<Sparkles className="w-4 h-4" />}>
              <span className="hidden sm:inline">Trouver un bien</span>
              <span className="sm:hidden text-xs">Trouver</span>
            </Button>
          </Link>
        </div>

      </div>
    </header>
  );
};
