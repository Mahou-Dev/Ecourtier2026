import React from 'react';
import {
  Wifi,
  Car,
  Wind,
  Waves,
  ShieldCheck,
  Zap,
  Droplet,
  GraduationCap,
  ShoppingBag,
  HeartPulse,
  Bus,
  Sun,
  Store,
  BookOpen,
  CheckCircle,
} from 'lucide-react';

export const getEquipementMeta = (code: string) => {
  switch (code) {
    case 'WIFI':
      return { label: 'Wi-Fi', icon: Wifi };
    case 'PARKING':
      return { label: 'Parking', icon: Car };
    case 'CLIMATISATION':
      return { label: 'Climatisation', icon: Wind };
    case 'PISCINE':
      return { label: 'Piscine', icon: Waves };
    case 'GARDIEN':
      return { label: 'Gardien / Sécurité', icon: ShieldCheck };
    case 'GROUPE_ELECTROGENE':
      return { label: 'Groupe électrogène', icon: Zap };
    case 'RESERVE_EAU':
      return { label: "Réserve d'eau", icon: Droplet };
    default:
      return { label: code, icon: CheckCircle };
  }
};

export const getProximiteMeta = (code: string) => {
  switch (code) {
    case 'ECOLE':
      return { label: 'École', icon: GraduationCap };
    case 'MARCHE':
      return { label: 'Marché', icon: ShoppingBag };
    case 'HOPITAL':
      return { label: 'Hôpital / Santé', icon: HeartPulse };
    case 'TRANSPORT':
      return { label: 'Transports (BRT/Bus)', icon: Bus };
    case 'PLAGE':
      return { label: 'Plage / Mer', icon: Sun };
    case 'COMMERCE':
      return { label: 'Commerces', icon: Store };
    case 'UNIVERSITE':
      return { label: 'Université', icon: BookOpen };
    default:
      return { label: code, icon: CheckCircle };
  }
};

export const EquipementBadge: React.FC<{ code: string; className?: string }> = ({
  code,
  className = '',
}) => {
  const meta = getEquipementMeta(code);
  const Icon = meta.icon;
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-sand-100 border border-sand-300 text-xs font-medium text-charcoal-700 ${className}`}
    >
      <Icon className="w-3.5 h-3.5 text-terracotta-500 shrink-0" />
      <span>{meta.label}</span>
    </span>
  );
};

export const ProximiteBadge: React.FC<{ code: string; className?: string }> = ({
  code,
  className = '',
}) => {
  const meta = getProximiteMeta(code);
  const Icon = meta.icon;
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-clay-50 border border-clay-200 text-xs font-medium text-sahel-800 ${className}`}
    >
      <Icon className="w-3.5 h-3.5 text-sahel-600 shrink-0" />
      <span>{meta.label}</span>
    </span>
  );
};
