import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Sparkles, Search, LayoutDashboard, User } from 'lucide-react';
import { Button } from '../ui/Button';

export const Header: React.FC = () => {
  const location = useLocation();

  return (
    <header className="sticky top-0 z-40 bg-sand-100/90 backdrop-blur-md border-b border-argile-200/80 transition-all">
      <div className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-8 h-18 sm:h-20 flex items-center justify-between">
        
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-3 group">
          <div className="w-10 h-10 sm:w-11 sm:h-11 rounded-xl bg-terracotta-500 flex items-center justify-center text-white shadow-terracotta-glow group-hover:scale-105 transition-transform">
            <Home className="w-5 h-5 sm:w-6 sm:h-6" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-display font-extrabold text-xl sm:text-2xl text-charcoal-800 tracking-tight">
                E-Courtier
              </span>
              <span className="bg-ocher-500 text-charcoal-900 text-[10px] font-extrabold px-1.5 py-0.5 rounded tracking-wider uppercase">
                SN
              </span>
            </div>
            <p className="text-[11px] text-charcoal-500 font-medium hidden xs:block">
              Immobilier Intelligent au Sénégal
            </p>
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

          <Link
            to="/dashboard"
            className={`flex items-center gap-1.5 hover:text-terracotta-600 transition-colors ${
              location.pathname === '/dashboard' ? 'text-terracotta-600 font-extrabold' : ''
            }`}
          >
            <LayoutDashboard className="w-3.5 h-3.5 text-sahel-800" />
            <span>Espace Partenaire</span>
          </Link>
        </nav>

        {/* Auth & Action Buttons */}
        <div className="flex items-center gap-2 xs:gap-3">
          <Link to="/connexion">
            <Button variant="ghost" size="sm" leftIcon={<User className="w-4 h-4" />}>
              Connexion
            </Button>
          </Link>
          
          <Link to="/recherche">
            <Button variant="primary" size="sm" leftIcon={<Sparkles className="w-4 h-4" />}>
              Trouver un bien
            </Button>
          </Link>
        </div>

      </div>
    </header>
  );
};
