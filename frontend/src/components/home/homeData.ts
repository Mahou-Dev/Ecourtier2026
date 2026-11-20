import heroAlmadies from '../../assets/home/hero-almadies.webp';
import heroSaly from '../../assets/home/hero-saly.webp';
import heroPlateau from '../../assets/home/hero-plateau.webp';
import trustInterior from '../../assets/home/trust-interior.webp';
import quartierAlmadies from '../../assets/home/quartier-almadies.webp';
import quartierPlateau from '../../assets/home/quartier-plateau.webp';
import quartierSacreCoeur from '../../assets/home/quartier-sacre-coeur.webp';
import quartierSaly from '../../assets/home/quartier-saly.webp';
import quartierNema from '../../assets/home/quartier-nema.webp';
import quartierBoucotte from '../../assets/home/quartier-boucotte.webp';

export interface HeroSlide {
  id: string;
  image: string;
  alt: string;
  location: string;
  caption: string;
  objectPosition: string;
}

export interface NeighborhoodItem {
  name: string;
  city: string;
  subtitle: string;
  image: string;
}

export const HERO_SLIDES: HeroSlide[] = [
  {
    id: 'almadies-dakar',
    image: heroAlmadies,
    alt: 'Villa contemporaine en pierre de travertin face à l’océan Atlantique aux Almadies, Dakar',
    location: 'Almadies, Dakar',
    caption: 'Villas littorales avec contrôle cadastral avant visite',
    objectPosition: 'object-[68%_center] sm:object-center',
  },
  {
    id: 'saly-petite-cote',
    image: heroSaly,
    alt: 'Patio architectural lumineux avec pergolas bois ouvert sur la mer à Saly, Petite Côte',
    location: 'Saly, Petite Côte',
    caption: 'Résidences côtières & séquestre de garantie sécurisé',
    objectPosition: 'object-center',
  },
  {
    id: 'plateau-dakar',
    image: heroPlateau,
    alt: 'Immeuble résidentiel contemporain en bord de mer à Dakar Plateau',
    location: 'Plateau, Dakar',
    caption: 'Appartements urbains & bail numérique à double signature',
    objectPosition: 'object-[56%_32%] sm:object-[center_35%]',
  },
];

export const HOME_NEIGHBORHOODS: NeighborhoodItem[] = [
  {
    name: 'Almadies',
    city: 'Dakar',
    subtitle: 'Villas littorales & appartements haut standing',
    image: quartierAlmadies,
  },
  {
    name: 'Plateau',
    city: 'Dakar',
    subtitle: 'Résidences urbaines au cœur des affaires',
    image: quartierPlateau,
  },
  {
    name: 'Sacré-Cœur',
    city: 'Dakar',
    subtitle: 'Quartier résidentiel central et connecté',
    image: quartierSacreCoeur,
  },
  {
    name: 'Saly Station',
    city: 'Saly',
    subtitle: 'Maisons de vacances & résidences Petite Côte',
    image: quartierSaly,
  },
  {
    name: 'Nema',
    city: 'Ziguinchor',
    subtitle: 'Logements familiaux au sud du Sénégal',
    image: quartierNema,
  },
  {
    name: 'Boucotte',
    city: 'Ziguinchor',
    subtitle: 'Studios et appartements proches du centre',
    image: quartierBoucotte,
  },
];

export const TRUST_INTERIOR_IMAGE = trustInterior;

export const HOME_CITIES = ['Dakar', 'Saly', 'Ziguinchor', 'Thiès', 'Mbour'] as const;

export const BUDGET_OPTIONS_LOCATION = [
  { label: 'Tous budgets', value: '' },
  { label: 'Max 150 000 FCFA / mois', value: '150000' },
  { label: 'Max 300 000 FCFA / mois', value: '300000' },
  { label: 'Max 500 000 FCFA / mois', value: '500000' },
  { label: 'Max 1 000 000 FCFA / mois', value: '1000000' },
] as const;

export const BUDGET_OPTIONS_VENTE = [
  { label: 'Tous budgets', value: '' },
  { label: 'Max 50 000 000 FCFA', value: '50000000' },
  { label: 'Max 85 000 000 FCFA', value: '85000000' },
  { label: 'Max 150 000 000 FCFA', value: '150000000' },
] as const;

export const SEARCH_SUGGESTIONS = [
  { label: 'Vérifiés à Dakar', ville: 'Dakar', mode: 'TOUS' as const, q: '', verified: true },
  { label: 'F3 Nema < 160k', ville: 'Ziguinchor', mode: 'LOCATION' as const, q: 'Nema', budgetMax: '160000' },
  { label: 'Appartements Mermoz', ville: 'Dakar', mode: 'LOCATION' as const, type: 'APPARTEMENT', q: 'Mermoz' },
  { label: 'Villas à Saly', ville: 'Saly', mode: 'VENTE' as const, type: 'VILLA', q: '' },
] as const;
