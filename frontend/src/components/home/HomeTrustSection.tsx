import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarCheck2, FileSignature, MessageSquareText, ShieldCheck } from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { TRUST_INTERIOR_IMAGE } from './homeData';

const SERVICE_PILLARS = [
  {
    title: 'Contrôle des pièces justificatives',
    text: 'Chaque bien vérifié s’appuie sur un examen administratif du titre foncier, du bail précédent et de l’identité du bailleur ou de l’agent.',
    icon: ShieldCheck,
  },
  {
    title: 'Rendez-vous encadrés et confirmés',
    text: 'Demandez vos créneaux de visite directement en ligne sans verser d’avance informelle ni frais de déplacement arbitraires.',
    icon: CalendarCheck2,
  },
  {
    title: 'Dossier sous séquestre & bail PDF',
    text: 'Suivez la consignation du dossier, signez électroniquement le bail et téléchargez le contrat PDF horodaté depuis votre espace.',
    icon: FileSignature,
  },
  {
    title: 'Messagerie intégrée & Assistant Samba IA',
    text: 'Échangez en direct avec le propriétaire ou l’agent dans une conversation tracée et affinez vos recherches grâce à Samba IA.',
    icon: MessageSquareText,
  },
];

export const HomeTrustSection: React.FC = () => {
  const { utilisateur } = useAuthStore();

  const secondaryLink = utilisateur ? '/dashboard' : '/inscription';
  const secondaryLabel = utilisateur ? 'Accéder à mon tableau de bord' : 'Publier ou gérer un bien';

  return (
    <section
      id="confiance-services"
      aria-labelledby="trust-heading"
      className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-10 scroll-mt-24"
    >
      <div className="bg-white rounded-3xl border border-[#E2DACB] shadow-[0_18px_50px_-20px_rgba(37,42,41,0.12)] overflow-hidden grid grid-cols-1 lg:grid-cols-12">
        {/* Left Architectural Photography Panel */}
        <div className="lg:col-span-5 relative min-h-[260px] sm:min-h-[340px] lg:min-h-full bg-[#EAE2D3]">
          <img
            src={TRUST_INTERIOR_IMAGE}
            alt="Intérieur contemporain lumineux ouvert sur une terrasse"
            loading="lazy"
            decoding="async"
            width={960}
            height={680}
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-[#162823]/85 via-[#162823]/20 to-transparent p-6 sm:p-8 flex flex-col justify-end text-white">
            <span className="text-[11px] font-semibold uppercase tracking-widest text-[#D5AD72]">
              Engagement plateforme
            </span>
            <p className="font-editorial font-bold text-lg sm:text-xl leading-snug mt-1">
              Une expérience immobilière pensée pour réduire les litiges entre bailleurs et locataires.
            </p>
          </div>
        </div>

        {/* Right Grounded Service Pillars */}
        <div className="lg:col-span-7 p-6 sm:p-10 lg:p-12 space-y-8">
          <div className="space-y-3">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#B85C42]/10 border border-[#B85C42]/20 text-[#9D4A32] text-xs font-semibold">
              Confiance &amp; Services
            </span>
            <h2
              id="trust-heading"
              className="font-editorial text-2xl sm:text-3xl font-extrabold text-[#252A29] tracking-tight leading-tight"
            >
              Des outils concrets à chaque étape de la transaction
            </h2>
            <p className="text-sm text-[#3F4745] leading-relaxed">
              E-Courtier réunit la recherche intelligente, l’agenda de visites, la modération documentaire
              et la signature du bail sur une seule interface claire.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            {SERVICE_PILLARS.map((pillar) => {
              const Icon = pillar.icon;
              return (
                <div
                  key={pillar.title}
                  className="p-4 rounded-2xl bg-[#FAF7F2] border border-[#E8E0D2] space-y-2"
                >
                  <div className="w-9 h-9 rounded-xl bg-white border border-[#E2DACB] text-[#B85C42] flex items-center justify-center shadow-2xs">
                    <Icon className="w-4 h-4" aria-hidden="true" />
                  </div>
                  <h3 className="font-editorial font-bold text-sm text-[#252A29]">
                    {pillar.title}
                  </h3>
                  <p className="text-xs sm:text-[13px] text-[#3F4745] leading-relaxed">
                    {pillar.text}
                  </p>
                </div>
              );
            })}
          </div>

          <div className="pt-2 flex flex-wrap items-center gap-3">
            <Link
              to="/recherche"
              className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full bg-[#B85C42] hover:bg-[#9D4A32] active:scale-[0.98] text-white text-sm font-semibold shadow-sm transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-2"
            >
              <span>Parcourir les biens</span>
              <ArrowRight className="w-4 h-4" aria-hidden="true" />
            </Link>

            <Link
              to={secondaryLink}
              className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full border border-[#D8CEBC] bg-[#FAF7F2] hover:bg-[#F5F0E8] hover:border-[#B85C42]/50 text-[#252A29] text-sm font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-2"
            >
              <span>{secondaryLabel}</span>
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
};
