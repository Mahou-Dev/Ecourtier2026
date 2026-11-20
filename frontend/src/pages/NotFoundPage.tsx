import React from 'react';
import { Link } from 'react-router-dom';
import { Compass, Home, Search } from 'lucide-react';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { Button } from '../components/ui/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <section className="relative flex-1 flex items-center justify-center bg-sand-100 py-14 px-4 sm:px-6 pb-24 sm:pb-16 overflow-hidden">
      <PatternClaustra variant="terracotta" opacity={0.05} scale={1.2} />

      <div className="relative z-10 max-w-lg w-full bg-white rounded-3xl border border-argile-200 shadow-warm-card p-8 sm:p-10 text-center space-y-6">
        <div className="w-16 h-16 rounded-2xl bg-terracotta-50 border border-terracotta-200 text-terracotta-600 flex items-center justify-center mx-auto shadow-xs">
          <Compass className="w-8 h-8" aria-hidden="true" />
        </div>

        <div className="space-y-2">
          <span className="inline-flex items-center px-3 py-1 rounded-full bg-sahel-50 border border-sahel-200 text-sahel-800 text-xs font-bold uppercase tracking-wider">
            Erreur 404
          </span>
          <h1 className="font-editorial text-2xl sm:text-3xl font-extrabold text-charcoal-900 tracking-tight">
            Page introuvable
          </h1>
          <p className="text-sm text-charcoal-600 leading-relaxed">
            L’adresse demandée n’existe pas ou a été déplacée. Poursuivez votre recherche immobilière depuis le catalogue ou la page d’accueil.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3 pt-2">
          <Link to="/" className="w-full sm:w-auto">
            <Button variant="primary" size="md" className="w-full justify-center" leftIcon={<Home className="w-4 h-4" />}>
              Retour à l’accueil
            </Button>
          </Link>
          <Link to="/recherche" className="w-full sm:w-auto">
            <Button variant="outline" size="md" className="w-full justify-center" leftIcon={<Search className="w-4 h-4" />}>
              Explorer les annonces
            </Button>
          </Link>
        </div>
      </div>
    </section>
  );
};

export default NotFoundPage;
