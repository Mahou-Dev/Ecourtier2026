import React from 'react';
import { Home, Heart } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-sahel-900 text-sand-200 border-t border-sahel-800 py-12 px-4 xs:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-8 pb-8 border-b border-sahel-800/80">
        
        {/* Brand Info */}
        <div className="space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-terracotta-500 flex items-center justify-center text-white">
              <Home className="w-4 h-4" />
            </div>
            <span className="font-display font-extrabold text-xl text-white tracking-tight">
              E-Courtier
            </span>
          </div>
          <p className="text-sm text-sahel-200/80 max-w-sm leading-relaxed">
            La première plateforme sénégalaise qui sécurise chaque étape du bail immobilier : de la vérification IA de l'annonce jusqu'au paiement sous séquestre.
          </p>
        </div>

        {/* Pillars */}
        <div className="space-y-3">
          <h4 className="font-display text-xs font-bold uppercase tracking-wider text-ocher-400">
            Nos Engagements
          </h4>
          <ul className="space-y-2 text-sm text-sahel-200/90">
            <li>✔ Annonces certifiées anti-arnaques</li>
            <li>✔ Caution bloquée sous séquestre bancaire</li>
            <li>✔ Signature de bail conforme au code sénégalais</li>
            <li>✔ Assistant IA de recherche personnalisé</li>
          </ul>
        </div>

        {/* Contact / Location */}
        <div className="space-y-3">
          <h4 className="font-display text-xs font-bold uppercase tracking-wider text-ocher-400">
            Zone d'intervention
          </h4>
          <p className="text-sm text-sahel-200/90">
            Dakar (Almadies, Mermoz, Plateau, Ngor), Thiès, Petite Côte (Saly, Somone) et région de Saint-Louis.
          </p>
          <p className="text-xs text-sahel-300">
            Contact : <span className="underline decoration-terracotta-400">contact@e-courtier.sn</span>
          </p>
        </div>

      </div>

      <div className="max-w-7xl mx-auto pt-6 flex flex-col sm:flex-row items-center justify-between text-xs text-sahel-300/80 gap-3">
        <p>© {new Date().getFullYear()} E-Courtier SN. Tous droits réservés.</p>
        <p className="flex items-center gap-1">
          <span>Conçu avec</span>
          <Heart className="w-3.5 h-3.5 text-terracotta-400 fill-terracotta-400" />
          <span>pour le marché immobilier sénégalais</span>
        </p>
      </div>
    </footer>
  );
};
