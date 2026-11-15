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
