import React from 'react';
import { CheckCircle2, FileCheck2, Lock, LucideIcon, ShieldCheck, Sparkles } from 'lucide-react';
import { PatternClaustra } from '../ui/PatternClaustra';

interface ProcessStep {
  number: string;
  title: string;
  description: string;
  guarantee: string;
  icon: LucideIcon;
}

const PROCESS_STEPS: ProcessStep[] = [
  {
    number: '01',
    title: 'Recherche & Filtre IA',
    description:
      'Recherchez par critères exacts ou en langage naturel avec Samba IA pour cibler les logements adaptés à votre budget.',
    guarantee: 'Aucun faux frais de dossier préalable',
    icon: Sparkles,
  },
  {
    number: '02',
    title: 'Contrôle Cadastral',
    description:
      'Vérification des justificatifs fournis (titre foncier, bail précédent, pièce d’identité) et identification des agents agréés avant visite.',
    guarantee: 'Pièces et profils vérifiés',
    icon: ShieldCheck,
  },
  {
    number: '03',
    title: 'Caution Séquestrée',
    description:
      'Votre dépôt de garantie est consigné dans le circuit séquestre de la plateforme et débloqué selon l’avancement contractuel validé.',
    guarantee: 'Fonds encadrés avant remise des clés',
    icon: Lock,
  },
  {
    number: '04',
    title: 'Bail Numérique Double Signature',
    description:
      'Génération du contrat PDF horodaté et numéroté avec double signature électronique du locataire et du propriétaire.',
    guarantee: 'Contrat PDF horodaté et archivé',
    icon: FileCheck2,
  },
];

export const HomeProcessSection: React.FC = () => {
  return (
    <section
      id="comment-ca-marche"
      aria-labelledby="process-heading"
      className="relative bg-[#162823] text-white py-16 sm:py-20 lg:py-24 px-4 xs:px-6 lg:px-10 overflow-hidden scroll-mt-24"
    >
      <PatternClaustra variant="ocher" opacity={0.045} scale={1.45} />

      <div className="max-w-7xl mx-auto space-y-12 relative z-10">
        {/* Header Row */}
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 border-b border-white/10 pb-8">
          <div className="max-w-2xl space-y-3">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-[#D5AD72]/15 border border-[#D5AD72]/35 text-[#E7BA78] text-xs font-semibold">
              <ShieldCheck className="w-3.5 h-3.5" aria-hidden="true" />
              <span>Parcours encadré</span>
            </div>
            <h2
              id="process-heading"
              className="font-editorial text-2xl sm:text-3xl lg:text-4xl font-extrabold text-white leading-tight tracking-tight"
            >
              Comment E-Courtier sécurise votre location ou achat en 4 étapes
            </h2>
          </div>

          <p className="text-sm text-[#E2EBE7] max-w-md leading-relaxed">
            Chaque étape — de la recherche jusqu&rsquo;à la signature du bail — laisse une trace claire et
            vérifiable entre les parties.
          </p>
        </div>

        {/* 4 Steps Editorial Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 lg:gap-6">
          {PROCESS_STEPS.map((step) => {
            const StepIcon = step.icon;
            return (
              <div
                key={step.number}
                className="group relative bg-[#1E352F] hover:bg-[#233E37] border border-white/10 hover:border-[#D5AD72]/45 rounded-2xl p-6 flex flex-col justify-between gap-6 transition-all duration-200 shadow-[0_12px_30px_-12px_rgba(0,0,0,0.35)]"
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="font-editorial font-extrabold text-3xl text-[#E38568] tracking-tight">
                      {step.number}
                    </span>
                    <div className="w-10 h-10 rounded-xl bg-white/10 border border-white/10 text-[#D5AD72] flex items-center justify-center group-hover:bg-[#B85C42] group-hover:text-white group-hover:border-[#B85C42] transition-colors">
                      <StepIcon className="w-5 h-5" aria-hidden={true} />
                    </div>
                  </div>

                  <h3 className="font-editorial font-bold text-lg text-white leading-snug">
                    {step.title}
                  </h3>

                  <p className="text-[13px] sm:text-sm text-[#DFE8E4] leading-relaxed">
                    {step.description}
                  </p>
                </div>

                <div className="pt-3.5 border-t border-white/10 flex items-center gap-2 text-xs font-medium text-[#E7BA78]">
                  <CheckCircle2 className="w-4 h-4 shrink-0 text-[#D5AD72]" aria-hidden="true" />
                  <span>{step.guarantee}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
