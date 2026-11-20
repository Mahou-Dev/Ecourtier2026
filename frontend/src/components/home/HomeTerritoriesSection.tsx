import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Compass, MapPin } from 'lucide-react';
import { HOME_NEIGHBORHOODS } from './homeData';

export const HomeTerritoriesSection: React.FC = () => {
  return (
    <section
      id="territoires-senegal"
      aria-labelledby="territories-heading"
      className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-10 space-y-8 scroll-mt-24"
    >
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-[#E2DACB] pb-5">
        <div className="space-y-2 max-w-2xl">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#1C352E]/10 border border-[#1C352E]/15 text-[#1C352E] text-xs font-semibold">
            <Compass className="w-3.5 h-3.5 text-[#B85C42]" aria-hidden="true" />
            <span>Territoires couverts</span>
          </div>
          <h2
            id="territories-heading"
            className="font-editorial text-2xl sm:text-3xl lg:text-4xl font-extrabold text-[#252A29] tracking-tight"
          >
            Quartiers recherchés au Sénégal
          </h2>
          <p className="text-sm text-[#3F4745]">
            Explorez directement les secteurs disponibles à Dakar, sur la Petite Côte et à Ziguinchor.
          </p>
        </div>

        <Link
          to="/recherche"
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#B85C42] hover:text-[#9D4A32] transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] rounded-lg px-2 py-1"
        >
          <span>Ouvrir la carte interactive</span>
          <ArrowRight className="w-4 h-4" aria-hidden="true" />
        </Link>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {HOME_NEIGHBORHOODS.map((nh) => (
          <div key={`${nh.city}-${nh.name}`}>
            <Link
              to={`/recherche?ville=${encodeURIComponent(nh.city)}&quartier=${encodeURIComponent(nh.name)}`}
              className="group relative block aspect-[4/3] sm:aspect-[16/11] rounded-2xl overflow-hidden border border-[#E2DACB] hover:border-[#B85C42]/60 shadow-[0_6px_22px_-8px_rgba(37,42,41,0.12)] hover:shadow-[0_16px_34px_-10px_rgba(37,42,41,0.2)] transition-all duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-2"
            >
              <img
                src={nh.image}
                alt={`${nh.name}, ${nh.city}`}
                loading="lazy"
                decoding="async"
                width={640}
                height={480}
                className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#141A18]/90 via-[#141A18]/35 to-transparent p-5 flex flex-col justify-between text-white">
                <div className="flex items-center justify-between">
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white/15 backdrop-blur-md border border-white/20 text-[11px] font-semibold tracking-wide text-[#F5F0E8]">
                    <MapPin className="w-3 h-3 text-[#D5AD72]" aria-hidden="true" />
                    {nh.city}
                  </span>
                </div>

                <div className="flex items-end justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="font-editorial font-bold text-lg sm:text-xl text-white group-hover:text-[#E7BA78] transition-colors">
                      {nh.name}
                    </h3>
                    <p className="text-xs text-[#F2ECE1] line-clamp-1 mt-0.5">{nh.subtitle}</p>
                  </div>
                  <span
                    aria-hidden="true"
                    className="w-8 h-8 rounded-full bg-white/15 group-hover:bg-[#B85C42] backdrop-blur-sm flex items-center justify-center shrink-0 transition-colors duration-200"
                  >
                    <ArrowRight className="w-4 h-4 text-white" />
                  </span>
                </div>
              </div>
            </Link>
          </div>
        ))}
      </div>
    </section>
  );
};
