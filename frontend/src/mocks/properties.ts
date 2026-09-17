export interface PropertyOwner {
  id: string;
  name: string;
  avatar: string;
  role: 'PROPRIETAIRE' | 'COURTIER_CERTIFIE';
  phone: string;
  rating: number;
  reviewCount: number;
  responseTime: string;
}

export interface VerificationDetails {
  cadastralNumber: string;
  videoVisitVerified: boolean;
  titleProofDate: string;
  verificationBadgeLabel: string;
}

export interface Property {
  id: string;
  title: string;
  type: 'APPARTEMENT' | 'VILLA' | 'STUDIO' | 'DUPLEX' | 'TERRAIN';
  transactionType: 'LOCATION' | 'VENTE';
  price: number; // en FCFA
  pricePeriod?: string; // ex: '/ mois'
  city: 'Dakar' | 'Ziguinchor' | 'Saly' | 'Thiès';
  neighborhood: string;
  address: string;
  lat: number;
  lng: number;
  surface: number; // en m²
  rooms: number;
  bedrooms: number;
  bathrooms: number;
  isVerified: boolean;
  verificationLevel: 1 | 2 | 3;
  verificationDetails: VerificationDetails;
  isEscrowGuaranteed: boolean;
  isFurnished: boolean;
  hasGenerator: boolean; // Groupe électrogène
  hasWaterTank: boolean;  // Réserve d'eau + surpresseur
  hasSecurityGuard: boolean; // Gardiennage 24/7
  hasAirConditioning: boolean;
  hasParking: boolean;
  hasWifi: boolean;
  hasPool?: boolean;
  images: string[];
  description: string;
  owner: PropertyOwner;
  createdAt: string;
  nearbyPlaces: string[];
}

export const MOCK_PROPERTIES: Property[] = [
  {
    id: 'prop-1',
    title: 'Appartement F3 Moderne avec balcon à Nema',
    type: 'APPARTEMENT',
    transactionType: 'LOCATION',
    price: 150000,
    pricePeriod: '/ mois',
    city: 'Ziguinchor',
    neighborhood: 'Nema',
    address: 'Rue de la Grande Mosquée, Nema 2, Ziguinchor',
    lat: 12.5684,
    lng: -16.2719,
    surface: 85,
    rooms: 3,
    bedrooms: 2,
    bathrooms: 2,
    isVerified: true,
    verificationLevel: 3,
    verificationDetails: {
      cadastralNumber: 'ZIG-NEMA-0482-2024',
      videoVisitVerified: true,
      titleProofDate: '14 Janvier 2026',
      verificationBadgeLabel: 'Cadastre & Visite Vidéo Validés'
    },
    isEscrowGuaranteed: true,
    isFurnished: false,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: true,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1484154218962-a197022b5858?auto=format&fit=crop&w=1200&q=80',
    ],
    description: 'Magnifique appartement F3 au 1er étage d’un immeuble récent à Nema Ziguinchor. Grand salon très lumineux, cuisine fermée équipée avec buanderie, 2 chambres climatisées avec placards intégrés. Équipé d’un groupe électrogène automatique et d’une réserve d’eau avec surpresseur.',
    owner: {
      id: 'owner-1',
      name: 'Ousmane Badji',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80',
      role: 'COURTIER_CERTIFIE',
      phone: '+221 77 450 12 89',
      rating: 4.9,
      reviewCount: 38,
      responseTime: 'Moins de 30 min'
    },
    createdAt: '2026-03-10',
    nearbyPlaces: ['Marché de Boucotte (5 min)', 'Université Assane Seck', 'Hôpital Régional']
  },
  {
    id: 'prop-2',
    title: 'Studio Meublé Standing à Boucotte',
    type: 'STUDIO',
    transactionType: 'LOCATION',
    price: 95000,
    pricePeriod: '/ mois',
    city: 'Ziguinchor',
    neighborhood: 'Boucotte',
    address: 'Avenue Chérif Samsidine Aïdara, Boucotte',
    lat: 12.5732,
    lng: -16.2765,
    surface: 38,
    rooms: 1,
    bedrooms: 1,
    bathrooms: 1,
    isVerified: true,
    verificationLevel: 2,
    verificationDetails: {
      cadastralNumber: 'ZIG-BOU-0129-2025',
      videoVisitVerified: true,
      titleProofDate: '02 Février 2026',
      verificationBadgeLabel: 'Identité & Titre Vérifiés'
    },
    isEscrowGuaranteed: true,
    isFurnished: true,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: false,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1554995207-c18c203602cb?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Charmant studio entièrement meublé et décoré avec goût à Boucotte. Lit double Queen Size, téléviseur Smart TV, climatiseur Inverter économique, kitchenette équipée. Idéal pour jeune professionnel ou enseignant.',
    owner: {
      id: 'owner-2',
      name: 'Aïssatou Diop',
      avatar: 'https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?auto=format&fit=crop&w=200&q=80',
      role: 'PROPRIETAIRE',
      phone: '+221 78 120 88 44',
      rating: 4.8,
      reviewCount: 19,
      responseTime: 'Moins d’1 heure'
    },
    createdAt: '2026-03-12',
    nearbyPlaces: ['Gare Routière (3 min)', 'Supermarché', 'Port de Ziguinchor']
  },
  {
    id: 'prop-3',
    title: 'Grande Villa Familiale avec Jardin à Diabir',
    type: 'VILLA',
    transactionType: 'LOCATION',
    price: 250000,
    pricePeriod: '/ mois',
    city: 'Ziguinchor',
    neighborhood: 'Diabir',
    address: 'Route de l’Aéroport, Diabir, Ziguinchor',
    lat: 12.5510,
    lng: -16.2620,
    surface: 240,
    rooms: 5,
    bedrooms: 4,
    bathrooms: 3,
    isVerified: true,
    verificationLevel: 3,
    verificationDetails: {
      cadastralNumber: 'ZIG-DIA-0891-2023',
      videoVisitVerified: true,
      titleProofDate: '20 Décembre 2025',
      verificationBadgeLabel: 'Bail Légal & Cadastre Validés'
    },
    isEscrowGuaranteed: true,
    isFurnished: false,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: true,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Superbe villa avec jardin ombragé et grande terrasse à Diabir. 4 chambres spacieuses dont une suite parentale avec dressing, grand salon-salle à manger, garage fermé pour 2 véhicules et logement gardien autonome.',
    owner: {
      id: 'owner-3',
      name: 'Mamadou Lamine Sané',
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80',
      role: 'COURTIER_CERTIFIE',
      phone: '+221 76 890 33 21',
      rating: 5.0,
      reviewCount: 42,
      responseTime: 'Immédiat'
    },
    createdAt: '2026-03-08',
    nearbyPlaces: ['Aéroport de Ziguinchor (4 min)', 'École Internationale', 'Hôtel Kadiandoumagne']
  },
  {
    id: 'prop-4',
    title: 'Appartement Haut Standing F4 avec Vue Mer aux Almadies',
    type: 'APPARTEMENT',
    transactionType: 'LOCATION',
    price: 750000,
    pricePeriod: '/ mois',
    city: 'Dakar',
    neighborhood: 'Almadies',
    address: 'Route des Almadies, près de la Pointe des Almadies, Dakar',
    lat: 14.7460,
    lng: -17.5180,
    surface: 140,
    rooms: 4,
    bedrooms: 3,
    bathrooms: 3,
    isVerified: true,
    verificationLevel: 3,
    verificationDetails: {
      cadastralNumber: 'DKR-ALM-1029-2024',
      videoVisitVerified: true,
      titleProofDate: '01 Mars 2026',
      verificationBadgeLabel: 'Titre Foncier & Vidéo 360° Certifiés'
    },
    isEscrowGuaranteed: true,
    isFurnished: true,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: true,
    hasWifi: true,
    hasPool: true,
    images: [
      'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1502005229762-cf1b2da7c5d6?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Luxueux appartement F4 meublé situé au 3ème étage avec ascenseur aux Almadies. Vue panoramique sur l’océan Atlantique, piscine en rooftop, salle de sport privée dans la résidence, sécurité incendie et gardiennage 24/7.',
    owner: {
      id: 'owner-4',
      name: 'Cabinet Immobilier Ndiaye & Co',
      avatar: 'https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=200&q=80',
      role: 'COURTIER_CERTIFIE',
      phone: '+221 33 820 00 11',
      rating: 4.9,
      reviewCount: 94,
      responseTime: 'Moins de 15 min'
    },
    createdAt: '2026-03-14',
    nearbyPlaces: ['Restaurants des Almadies (2 min)', 'Ambassades', 'Nautilus Club']
  },
  {
    id: 'prop-5',
    title: 'Appartement F3 Neuf et Lumineux à Sacré-Cœur 3',
    type: 'APPARTEMENT',
    transactionType: 'LOCATION',
    price: 320000,
    pricePeriod: '/ mois',
    city: 'Dakar',
    neighborhood: 'Sacré-Cœur',
    address: 'Sacré-Cœur 3 VDN, près du Collège Sacré-Cœur, Dakar',
    lat: 14.7180,
    lng: -17.4720,
    surface: 95,
    rooms: 3,
    bedrooms: 2,
    bathrooms: 2,
    isVerified: true,
    verificationLevel: 2,
    verificationDetails: {
      cadastralNumber: 'DKR-SAC-0552-2025',
      videoVisitVerified: true,
      titleProofDate: '10 Février 2026',
      verificationBadgeLabel: 'Annonce Contrôlée & Certifiée'
    },
    isEscrowGuaranteed: true,
    isFurnished: false,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: true,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Bel appartement F3 sur la VDN à Sacré-Cœur 3. Finitions haut de gamme, carrelage d’importation, cuisine aménagée avec placards, suite parentale avec salle de bain privée et chauffe-eau solaire.',
    owner: {
      id: 'owner-5',
      name: 'Cheikh Tidiane Sy',
      avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=200&q=80',
      role: 'PROPRIETAIRE',
      phone: '+221 77 631 22 90',
      rating: 4.7,
      reviewCount: 15,
      responseTime: 'Moins de 2 heures'
    },
    createdAt: '2026-03-11',
    nearbyPlaces: ['Auchan VDN (3 min)', 'Boulangerie L’Abonnée', 'Station Elton']
  },
  {
    id: 'prop-6',
    title: 'Charmante Maison Traditionnelle Rénovée à la Médina',
    type: 'VILLA',
    transactionType: 'LOCATION',
    price: 220000,
    pricePeriod: '/ mois',
    city: 'Dakar',
    neighborhood: 'Médina',
    address: 'Rue 11 x Rue 14, Médina, Dakar',
    lat: 14.6850,
    lng: -17.4480,
    surface: 110,
    rooms: 4,
    bedrooms: 3,
    bathrooms: 2,
    isVerified: true,
    verificationLevel: 2,
    verificationDetails: {
      cadastralNumber: 'DKR-MED-0311-2024',
      videoVisitVerified: true,
      titleProofDate: '15 Janvier 2026',
      verificationBadgeLabel: 'Identité & Bail COCC Validés'
    },
    isEscrowGuaranteed: true,
    isFurnished: false,
    hasGenerator: false,
    hasWaterTank: true,
    hasSecurityGuard: false,
    hasAirConditioning: true,
    hasParking: false,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1570129477492-45c003edd2be?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Maison de ville pleine de charme avec patio intérieur à la Médina. Entièrement rénovée en conservant l’architecture dakaroise authentique. À 5 minutes du centre-ville Plateau.',
    owner: {
      id: 'owner-6',
      name: 'Fatou Bintou Sow',
      avatar: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=200&q=80',
      role: 'PROPRIETAIRE',
      phone: '+221 70 443 11 00',
      rating: 4.9,
      reviewCount: 27,
      responseTime: 'Moins de 30 min'
    },
    createdAt: '2026-03-09',
    nearbyPlaces: ['Marché Tilène (4 min)', 'Corniche Ouest', 'Hôpital Abass Ndao']
  },
  {
    id: 'prop-7',
    title: 'Villa d’Architecte avec Piscine et Jardin à Saly Niakh Niakhal',
    type: 'VILLA',
    transactionType: 'VENTE',
    price: 165000000,
    pricePeriod: '',
    city: 'Saly',
    neighborhood: 'Saly Niakh Niakhal',
    address: 'Saly Niakh Niakhal, Petite Côte',
    lat: 14.4420,
    lng: -17.0180,
    surface: 350,
    rooms: 6,
    bedrooms: 4,
    bathrooms: 4,
    isVerified: true,
    verificationLevel: 3,
    verificationDetails: {
      cadastralNumber: 'THS-SAL-0044-2023',
      videoVisitVerified: true,
      titleProofDate: '05 Février 2026',
      verificationBadgeLabel: 'Titre Foncier Définitif Vérifié'
    },
    isEscrowGuaranteed: true,
    isFurnished: true,
    hasGenerator: true,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: true,
    hasParking: true,
    hasWifi: true,
    hasPool: true,
    images: [
      'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Somptueuse villa contemporaine à 200 mètres de la plage à Saly. Grand séjour ouvert sur terrasse et piscine privée de 10x4m, 4 suites parentales climatisées, cuisine américaine équipée, panneaux solaires en appoint.',
    owner: {
      id: 'owner-7',
      name: 'Teranga Immobilier Saly',
      avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80',
      role: 'COURTIER_CERTIFIE',
      phone: '+221 33 957 12 34',
      rating: 5.0,
      reviewCount: 61,
      responseTime: 'Immédiat'
    },
    createdAt: '2026-03-01',
    nearbyPlaces: ['Plage de Saly (3 min)', 'Golf de Saly', 'Centre Commercial Saly Center']
  },
  {
    id: 'prop-8',
    title: 'Studio Cosy Proche Université à Diabir',
    type: 'STUDIO',
    transactionType: 'LOCATION',
    price: 75000,
    pricePeriod: '/ mois',
    city: 'Ziguinchor',
    neighborhood: 'Diabir',
    address: 'Face Cité Universitaire, Diabir, Ziguinchor',
    lat: 12.5480,
    lng: -16.2590,
    surface: 30,
    rooms: 1,
    bedrooms: 1,
    bathrooms: 1,
    isVerified: true,
    verificationLevel: 1,
    verificationDetails: {
      cadastralNumber: 'ZIG-DIA-0201-2025',
      videoVisitVerified: false,
      titleProofDate: '28 Janvier 2026',
      verificationBadgeLabel: 'Identité Propriétaire Vérifiée'
    },
    isEscrowGuaranteed: true,
    isFurnished: true,
    hasGenerator: false,
    hasWaterTank: true,
    hasSecurityGuard: true,
    hasAirConditioning: false,
    hasParking: true,
    hasWifi: true,
    images: [
      'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80'
    ],
    description: 'Studio idéal pour étudiant ou chercheur à côté de l’Université Assane Seck de Ziguinchor. Équipé d’un lit 1 place, bureau de travail, ventilateur de plafond, kitchenette et connexion internet haut débit.',
    owner: {
      id: 'owner-1',
      name: 'Ousmane Badji',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80',
      role: 'COURTIER_CERTIFIE',
      phone: '+221 77 450 12 89',
      rating: 4.9,
      reviewCount: 38,
      responseTime: 'Moins de 30 min'
    },
    createdAt: '2026-03-13',
    nearbyPlaces: ['Campus UASSZ (2 min)', 'Bibliothèque Universitaire', 'Arrêt de Bus']
  }
];

export const MOCK_NEIGHBORHOODS = [
  { name: 'Nema', city: 'Ziguinchor', propertyCount: 14, avgPriceRent: '140 000 FCFA', image: 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=600&q=80' },
  { name: 'Almadies', city: 'Dakar', propertyCount: 42, avgPriceRent: '650 000 FCFA', image: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=600&q=80' },
  { name: 'Boucotte', city: 'Ziguinchor', propertyCount: 19, avgPriceRent: '95 000 FCFA', image: 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=600&q=80' },
  { name: 'Sacré-Cœur', city: 'Dakar', propertyCount: 31, avgPriceRent: '320 000 FCFA', image: 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=600&q=80' },
  { name: 'Diabir', city: 'Ziguinchor', propertyCount: 11, avgPriceRent: '180 000 FCFA', image: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80' },
  { name: 'Médina', city: 'Dakar', propertyCount: 28, avgPriceRent: '210 000 FCFA', image: 'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=600&q=80' },
];
