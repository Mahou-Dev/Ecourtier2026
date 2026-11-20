import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  MapPin,
  Pause,
  Play,
  ShieldCheck,
} from 'lucide-react';
import { useReducedMotion } from 'framer-motion';
import { HERO_SLIDES } from './homeData';

export const HomeHeroSection: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [activeIndex, setActiveIndex] = useState(0);
  const [isManuallyPaused, setIsManuallyPaused] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const bgLayerRef = useRef<HTMLDivElement>(null);
  const isAutoPaused = Boolean(shouldReduceMotion || isManuallyPaused || isHovered);

  // Subtle GPU-composited scroll parallax via direct DOM transform (zero React re-renders, disabled under reduced motion)
  useEffect(() => {
    const el = bgLayerRef.current;
    if (!el) return;
    if (shouldReduceMotion) {
      el.style.transform = '';
      return;
    }

    let rafId = 0;
    const handleScroll = () => {
      if (rafId) return;
      rafId = window.requestAnimationFrame(() => {
        const y = Math.min(window.scrollY, 480);
        const offset = Math.round(y * 0.14);
        el.style.transform = `translate3d(0, ${offset}px, 0) scale(1.04)`;
        rafId = 0;
      });
    };

    handleScroll();
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', handleScroll);
      if (rafId) window.cancelAnimationFrame(rafId);
    };
  }, [shouldReduceMotion]);

  // Automatic gentle crossfade carousel with hover & manual pause support
  useEffect(() => {
    if (isAutoPaused) return;
    const timer = window.setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % HERO_SLIDES.length);
    }, 6500);
    return () => window.clearInterval(timer);
  }, [isAutoPaused]);

  const goPrev = () => {
    setActiveIndex((prev) => (prev - 1 + HERO_SLIDES.length) % HERO_SLIDES.length);
  };

  const goNext = () => {
    setActiveIndex((prev) => (prev + 1) % HERO_SLIDES.length);
  };

  const currentSlide = HERO_SLIDES[activeIndex];

  return (
    <section
      aria-label="Vitrine immobilière E-Courtier Sénégal"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className="relative w-full min-h-[430px] sm:min-h-[465px] lg:min-h-[435px] xl:min-h-[505px] flex items-center bg-[#18211F] text-white overflow-hidden"
    >
      {/* Background Architectural Photographic Carousel Layers (zero layout shift) */}
      <div
        ref={bgLayerRef}
        className="absolute inset-0 z-0 pointer-events-none will-change-transform"
      >
        {HERO_SLIDES.map((slide, idx) => {
          const isActive = idx === activeIndex;
          return (
            <img
              key={slide.id}
              src={slide.image}
              alt={slide.alt}
              loading="eager"
              decoding="async"
              {...(idx === 0 ? ({ fetchpriority: 'high' } as Record<string, string>) : {})}
              width={1440}
              height={810}
              aria-hidden={!isActive}
              className={`absolute inset-0 w-full h-full object-cover select-none transition-opacity duration-700 ease-out ${
                slide.objectPosition
              } ${isActive ? 'opacity-100' : 'opacity-0'} ${
                isActive && !isAutoPaused ? 'home-hero-kenburns' : ''
              }`}
            />
          );
        })}

        {/* Directional dark editorial overlays guaranteeing WCAG AA/AAA text contrast */}
        <div className="absolute inset-0 bg-gradient-to-r from-[#141A18]/90 via-[#18211F]/65 to-[#141A18]/20" />
        <div className="absolute inset-0 bg-gradient-to-t from-[#141A18]/85 via-transparent to-[#141A18]/35" />
      </div>

      {/* Main Hero Foreground Content */}
      <div className="relative z-10 w-full max-w-7xl mx-auto px-4 xs:px-6 lg:px-10 pt-6 sm:pt-8 pb-16 sm:pb-20">
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 lg:gap-8">
          {/* Left Editorial Headline Block */}
          <div className="max-w-2xl space-y-4 sm:space-y-5">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/15 backdrop-blur-md border border-white/20 text-[#F5F0E8] text-xs font-medium tracking-wide">
              <ShieldCheck className="w-3.5 h-3.5 text-[#D5AD72] shrink-0" aria-hidden="true" />
              <span>Contrôle cadastral &amp; séquestre bancaire &bull; Sénégal</span>
            </div>

            <h1 className="font-editorial text-[28px] xs:text-[34px] sm:text-[44px] lg:text-[52px] font-extrabold tracking-tight text-white leading-[1.1]">
              L&rsquo;immobilier au Sénégal,{' '}
              <span className="text-[#E7BA78]">vérifié avant chaque visite.</span>
            </h1>

            <p className="text-sm sm:text-base lg:text-[17px] text-[#EFE9DF] leading-relaxed max-w-xl font-normal">
              Louez ou achetez à Dakar, Saly et Ziguinchor sans faux courtiers ni frais d&rsquo;avance
              abusifs. Chaque annonce passe un contrôle documentaire et vos dépôts sont consignés
              sous séquestre.
            </p>

            <div className="flex flex-wrap items-center gap-3 pt-1">
              <Link
                to="/recherche?statutVerification=VERIFIEE"
                className="inline-flex items-center justify-center gap-2 px-5 sm:px-6 py-3 rounded-full bg-[#B85C42] hover:bg-[#9D4A32] active:scale-[0.98] text-white text-sm font-semibold shadow-[0_12px_28px_-6px_rgba(184,92,66,0.55)] transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] focus-visible:ring-offset-2 focus-visible:ring-offset-[#18211F]"
              >
                <span>Explorer les biens vérifiés</span>
                <ArrowRight className="w-4 h-4 shrink-0" aria-hidden="true" />
              </Link>

              <a
                href="#comment-ca-marche"
                className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-full bg-white/15 hover:bg-white/25 active:scale-[0.98] backdrop-blur-md border border-white/25 text-white text-sm font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] focus-visible:ring-offset-2 focus-visible:ring-offset-[#18211F]"
              >
                <span>Découvrir le parcours sécurisé</span>
              </a>
            </div>
          </div>

          {/* Right Discrete Slide Context & Accessible Carousel Controls */}
          <div className="flex flex-col sm:flex-row lg:flex-col items-start sm:items-center lg:items-end justify-between gap-3 shrink-0">
            <div className="bg-[#18211F]/70 backdrop-blur-md border border-white/15 rounded-xl px-4 py-2.5 max-w-xs">
              <p className="flex items-center gap-1.5 text-xs font-semibold text-[#D5AD72]">
                <MapPin className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                <span>{currentSlide.location}</span>
              </p>
              <p className="text-[11px] text-[#E6DFD3] mt-0.5 leading-snug">
                {currentSlide.caption}
              </p>
            </div>

            <div
              className="flex items-center gap-1.5 bg-[#18211F]/75 backdrop-blur-md border border-white/15 rounded-full px-2.5 py-1.5"
              role="region"
              aria-label="Contrôles du diaporama architectural"
            >
              <button
                type="button"
                onClick={goPrev}
                aria-label="Diapositive précédente"
                title="Diapositive précédente"
                className="w-7 h-7 rounded-full inline-flex items-center justify-center text-white/90 hover:text-white hover:bg-white/15 active:scale-95 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72]"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>

              <div className="flex items-center gap-1.5 px-1" role="tablist" aria-label="Sélectionner une photo">
                {HERO_SLIDES.map((slide, idx) => {
                  const selected = idx === activeIndex;
                  return (
                    <button
                      key={slide.id}
                      type="button"
                      role="tab"
                      aria-selected={selected}
                      aria-label={`Afficher ${slide.location} (${idx + 1} sur ${HERO_SLIDES.length})`}
                      onClick={() => setActiveIndex(idx)}
                      className={`h-2 rounded-full transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72] ${
                        selected
                          ? 'w-6 bg-[#D5AD72]'
                          : 'w-2 bg-white/45 hover:bg-white/75'
                      }`}
                    />
                  );
                })}
              </div>

              <button
                type="button"
                onClick={goNext}
                aria-label="Diapositive suivante"
                title="Diapositive suivante"
                className="w-7 h-7 rounded-full inline-flex items-center justify-center text-white/90 hover:text-white hover:bg-white/15 active:scale-95 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72]"
              >
                <ChevronRight className="w-4 h-4" />
              </button>

              <span className="h-4 w-px bg-white/20 mx-0.5" aria-hidden="true" />

              <button
                type="button"
                onClick={() => setIsManuallyPaused((p) => !p)}
                aria-label={isManuallyPaused ? 'Reprendre le défilement' : 'Mettre en pause le défilement'}
                title={isManuallyPaused ? 'Reprendre le défilement' : 'Mettre en pause le défilement'}
                className="w-7 h-7 rounded-full inline-flex items-center justify-center text-white/90 hover:text-white hover:bg-white/15 active:scale-95 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D5AD72]"
              >
                {isManuallyPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
