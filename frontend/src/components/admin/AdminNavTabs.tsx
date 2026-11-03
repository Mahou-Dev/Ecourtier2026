import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, ShieldCheck, Flag, Users } from 'lucide-react';

export const AdminNavTabs: React.FC = () => {
  const location = useLocation();

  const items = [
    {
      to: '/admin/dashboard',
      label: "Vue d'ensemble",
      icon: LayoutDashboard,
      iconColor: 'text-terracotta-600',
    },
    {
      to: '/admin/utilisateurs',
      label: 'Utilisateurs',
      icon: Users,
      iconColor: 'text-sahel-700',
    },
    {
      to: '/admin/verification',
      label: 'Vérifications',
      icon: ShieldCheck,
      iconColor: 'text-emerald-600',
    },
    {
      to: '/admin/signalements',
      label: 'Signalements',
      icon: Flag,
      iconColor: 'text-ocher-600',
    },
  ];

  return (
    <nav
      aria-label="Navigation administration"
      className="flex flex-wrap items-center gap-1.5 bg-white/90 p-1.5 rounded-2xl border border-argile-200/80 shadow-xs"
    >
      {items.map((item) => {
        const Icon = item.icon;
        const active = location.pathname === item.to;
        return (
          <Link
            key={item.to}
            to={item.to}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
              active
                ? 'bg-terracotta-500 text-white shadow-xs'
                : 'text-charcoal-700 hover:bg-sand-100'
            }`}
          >
            <Icon className={`w-4 h-4 ${active ? 'text-white' : item.iconColor}`} />
            <span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
};
