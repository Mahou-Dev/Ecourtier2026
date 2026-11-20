import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Heart, CheckCircle2, Mail, ArrowUpRight } from 'lucide-react';

export const Footer: React.FC = () => {
  const location = useLocation();
  const pathname = location.pathname;
  const isHome = pathname === '/';

  // Full-viewport map/list interactive workspace has no editorial footer underneath
  if (pathname === '/recherche') {
    return null;
  }

  // Dense workspace routes (/dashboard, /admin/*) render a compact workspace footer bar
  // preserving tool height while guaranteeing bottom-right clearance for the Samba IA launcher
  if (pathname === '/dashboard' || pathname.startsWith('/admin')) {
    return (
      <footer className="bg-[#162823] text-[#D0C9BD] border-t border-white/10 py-3.5 pl-4 pr-16 xs:pl-6 sm:pr-44 lg:pl-8 lg:pr-44 text-xs">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="font-editorial font-bold text-white">E-Courtier SN</span>
            <span className="text-[#8FA09A]" aria-hidden="true">&bull;</span>
            <span>&copy; {new Date().getFullYear()} Plateforme immobilière sécurisée</span>
          </div>
          <a
            href="mailto:contact@e-courtier.sn"
            className="inline-flex items-center gap-1.5 text-[#F5F0E8] hover:text-[#D5AD72] underline decoration-[#B85C42] underline-offset-4 transition-colors"
          >
            <Mail className="w-3.5 h-3.5 text-[#D5AD72]" aria-hidden="true" />
            <span>contact@e-courtier.sn</span>
          </a>
        </div>
      </footer>
    );
  }

  if (isHome) {
    return (
      <footer className="bg-[#182220] text-[#F5F0E8] border-t border-white/10 pt-14 pb-24 sm:py-14 px-4 xs:px-6 lg:px-10">
        <div className="max-w-7xl mx-auto grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-12 gap-8 lg:gap-10 pb-10 border-b border-white/10">
          {/* Brand Info */}
          <div className="lg:col-span-4 space-y-4">
            <Link
              to="/"
              className="inline-flex items-center gap-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] rounded-lg"
            >
              <div className="w-9 h-9 rounded-xl bg-[#B85C42] flex items-center justify-center text-white shadow-sm">
                <Home className="w-4 h-4" aria-hidden="true" />
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-editorial font-extrabold text-xl text-white tracking-tight">
                  E-Courtier
                </span>
                <span className="bg-[#D5AD72] text-[#182220] text-[10px] font-extrabold px-1.5 py-0.5 rounded uppercase tracking-wider">
                  SN
                </span>
              </div>
            </Link>
            <p className="text-sm text-[#D6D0C5] max-w-sm leading-relaxed">
              La plateforme sénégalaise qui encadre chaque étape du bail immobilier : contrôle
              documentaire de l&rsquo;annonce, visite planifiée, séquestre et bail numérique.
            </p>
          </div>

          {/* Quick Real Links */}
          <div className="lg:col-span-2 space-y-3">
            <h4 className="font-editorial text-xs font-bold uppercase tracking-wider text-[#D5AD72]">
              Navigation
            </h4>
            <ul className="space-y-2.5 text-sm text-[#E7E1D7]">
              <li>
                <Link
                  to="/recherche"
                  className="hover:text-[#D5AD72] transition-colors inline-flex items-center gap-1 focus-visible:outline-none focus-visible:underline"
                >
                  <span>Rechercher un bien</span>
                </Link>
              </li>
              <li>
                <Link
                  to="/recherche?statutVerification=VERIFIEE"
                  className="hover:text-[#D5AD72] transition-colors inline-flex items-center gap-1 focus-visible:outline-none focus-visible:underline"
                >
                  <span>Annonces vérifiées</span>
                </Link>
              </li>
              <li>
                <Link
                  to="/connexion"
                  className="hover:text-[#D5AD72] transition-colors inline-flex items-center gap-1 focus-visible:outline-none focus-visible:underline"
                >
                  <span>Connexion</span>
                </Link>
              </li>
              <li>
                <Link
                  to="/inscription"
                  className="hover:text-[#D5AD72] transition-colors inline-flex items-center gap-1 focus-visible:outline-none focus-visible:underline"
                >
                  <span>Créer un compte</span>
                  <ArrowUpRight className="w-3.5 h-3.5 opacity-75" aria-hidden="true" />
                </Link>
              </li>
            </ul>
          </div>

          {/* Pillars */}
          <div className="lg:col-span-3 space-y-3">
            <h4 className="font-editorial text-xs font-bold uppercase tracking-wider text-[#D5AD72]">
              Nos Engagements
            </h4>
            <ul className="space-y-2.5 text-sm text-[#D6D0C5]">
              {[
                'Annonces vérifiées anti-arnaques',
                'Caution bloquée sous séquestre bancaire',
                'Bail numérique avec double signature électronique',
                'Assistant IA de recherche personnalisé',
              ].map((item) => (
                <li key={item} className="flex items-start gap-2">
                  <CheckCircle2
                    className="w-4 h-4 text-[#D5AD72] shrink-0 mt-0.5"
                    aria-hidden="true"
                  />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Contact / Location */}
          <div className="lg:col-span-3 space-y-3">
            <h4 className="font-editorial text-xs font-bold uppercase tracking-wider text-[#D5AD72]">
              Zone d&rsquo;intervention
            </h4>
            <p className="text-sm text-[#D6D0C5] leading-relaxed">
              Dakar (Almadies, Mermoz, Plateau, Ngor), Thiès, Petite Côte (Saly, Somone) et région de
              Saint-Louis.
            </p>
            <a
              href="mailto:contact@e-courtier.sn"
              className="inline-flex items-center gap-2 text-xs font-medium text-[#F5F0E8] hover:text-[#D5AD72] underline decoration-[#B85C42] underline-offset-4 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] rounded"
            >
              <Mail className="w-3.5 h-3.5 text-[#D5AD72]" aria-hidden="true" />
              <span>contact@e-courtier.sn</span>
            </a>
          </div>
        </div>

        <div className="max-w-7xl mx-auto pt-6 flex flex-col sm:flex-row items-center justify-between text-xs text-[#C8C1B5] gap-3 sm:pr-44">
          <p>&copy; {new Date().getFullYear()} E-Courtier SN. Tous droits réservés.</p>
          <p className="flex items-center gap-1">
            <span>Conçu avec</span>
            <Heart className="w-3.5 h-3.5 text-[#D87458] fill-[#D87458]" aria-hidden="true" />
            <span>pour le marché immobilier sénégalais</span>
          </p>
        </div>
      </footer>
    );
  }

  return (
    <footer className="bg-[#182220] text-[#F5F0E8] border-t border-white/10 pt-10 pb-20 sm:py-12 pl-4 pr-4 xs:px-6 sm:pr-44 lg:pl-8 lg:pr-44">
      <div className="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-8 pb-8 border-b border-white/10">
        
        {/* Brand Info */}
        <div className="space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#B85C42] flex items-center justify-center text-white">
              <Home className="w-4 h-4" />
            </div>
            <span className="font-editorial font-extrabold text-xl text-white tracking-tight">
              E-Courtier
            </span>
            <span className="bg-[#D5AD72] text-[#182220] text-[10px] font-extrabold px-1.5 py-0.5 rounded uppercase tracking-wider">
              SN
            </span>
          </div>
          <p className="text-sm text-[#D6D0C5] max-w-sm leading-relaxed">
            La plateforme sénégalaise qui sécurise chaque étape du bail immobilier : contrôle documentaire de l&rsquo;annonce, visite planifiée, séquestre et bail numérique.
          </p>
        </div>

        {/* Pillars */}
        <div className="space-y-3">
          <h4 className="font-editorial text-xs font-bold uppercase tracking-wider text-[#D5AD72]">
            Nos Engagements
          </h4>
          <ul className="space-y-2 text-sm text-[#D6D0C5]">
            {[
              'Annonces vérifiées anti-arnaques',
              'Caution bloquée sous séquestre bancaire',
              'Bail numérique avec double signature électronique',
              'Assistant IA de recherche personnalisé',
            ].map((item) => (
              <li key={item} className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#D5AD72] shrink-0 mt-0.5" aria-hidden="true" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* Contact / Location */}
        <div className="space-y-3">
          <h4 className="font-editorial text-xs font-bold uppercase tracking-wider text-[#D5AD72]">
            Zone d&rsquo;intervention
          </h4>
          <p className="text-sm text-[#D6D0C5]">
            Dakar (Almadies, Mermoz, Plateau, Ngor), Thiès, Petite Côte (Saly, Somone) et région de Saint-Louis.
          </p>
          <a
            href="mailto:contact@e-courtier.sn"
            className="inline-flex items-center gap-2 text-xs font-medium text-[#F5F0E8] hover:text-[#D5AD72] underline decoration-[#B85C42] underline-offset-4 transition-colors"
          >
            <Mail className="w-3.5 h-3.5 text-[#D5AD72]" aria-hidden="true" />
            <span>contact@e-courtier.sn</span>
          </a>
        </div>

      </div>

      <div className="max-w-7xl mx-auto pt-6 flex flex-col sm:flex-row items-center justify-between text-xs text-[#C8C1B5] gap-3">
        <p>&copy; {new Date().getFullYear()} E-Courtier SN. Tous droits réservés.</p>
        <p className="flex items-center gap-1">
          <span>Conçu avec</span>
          <Heart className="w-3.5 h-3.5 text-[#D87458] fill-[#D87458]" aria-hidden="true" />
          <span>pour le marché immobilier sénégalais</span>
        </p>
      </div>
    </footer>
  );
};
