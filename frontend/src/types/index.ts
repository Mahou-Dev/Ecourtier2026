export interface HealthCheckResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
  environment: string;
}

export interface UtilisateurDTO {
  id: string;
  nom: string;
  prenom: string;
  email: string;
  telephone: string;
  role: 'ADMIN' | 'AGENT' | 'PROPRIETAIRE' | 'LOCATAIRE';
  dateCreation?: string;
  actif?: boolean;
  photoProfilUrl?: string;
}

export interface AuthResponseDTO {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  utilisateur: UtilisateurDTO;
}

export interface ConnexionRequestDTO {
  email: string;
  motDePasse: string;
}

export interface InscriptionRequestDTO {
  nom: string;
  prenom: string;
  email: string;
  motDePasse: string;
  telephone: string;
  role: 'LOCATAIRE' | 'PROPRIETAIRE' | 'AGENT';
}

export interface PhotoAnnonceDTO {
  id: string;
  url: string;
  ordre: number;
}

export interface AnnonceDTO {
  id: string;
  titre: string;
  description: string;
  type: 'STUDIO' | 'APPARTEMENT' | 'VILLA';
  modeTransaction: 'LOCATION' | 'VENTE';
  prix: number;
  superficie: number;
  nombrePieces: number;
  etage?: number;
  quartier: string;
  ville: string;
  latitude: number;
  longitude: number;
  meuble: boolean;
  animauxAcceptes: boolean;
  lienVisite360?: string;
  statutVerification: 'EN_ATTENTE' | 'VERIFIEE' | 'REJETEE';
  statutPublication: 'DISPONIBLE' | 'RESERVEE' | 'LOUEE' | 'VENDUE' | 'SUSPENDUE';
  dateCreation: string;
  auteur: UtilisateurDTO;
  photos: PhotoAnnonceDTO[];
  equipements: string[];
  proximites: string[];
  documentsVerification?: any[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface RendezVousRequestDTO {
  dateHeure: string;
}

export interface RendezVousResponseDTO {
  id: string;
  annonceId: string;
  annonceTitre: string;
  demandeurId: string;
  demandeurNomComplet: string;
  demandeurEmail: string;
  dateHeure: string;
  statut: 'DEMANDE' | 'CONFIRME' | 'ANNULE' | 'EFFECTUE';
  motifAnnulation?: string;
  rappelEnvoye: boolean;
  dateCreation: string;
}

export interface MessageIAResponseDTO {
  id: string;
  expediteur: 'UTILISATEUR' | 'ASSISTANT';
  contenu: string;
  questionDeRelance?: string;
  criteres?: any;
  annoncesSuggeres?: AnnonceDTO[];
  dateEnvoi: string;
}

export interface ConversationIAResponseDTO {
  id: string;
  dateCreation: string;
  messages: MessageIAResponseDTO[];
}

export type StatutSequestre = 'INITIALISE' | 'BLOQUE' | 'LIBERE' | 'REMBOURSE';
export type StatutBail = 'NON_SIGNE' | 'SIGNE_LOCATAIRE' | 'SIGNE_PROPRIETAIRE' | 'SIGNE_COMPLET';

export interface TransactionResponseDTO {
  id: string;
  annonceId: string;
  annonceTitre: string;
  locataireId: string;
  locataireNomComplet: string;
  proprietaireId?: string;
  proprietaireNomComplet?: string;
  montantCaution: number;
  fraisDossier: number;
  statutSequestre: StatutSequestre;
  referenceWave?: string;
  statutBail: StatutBail;
  dateSignatureLocataire?: string;
  dateSignatureProprietaire?: string;
  dateLiberation?: string;
  pdfBailPath?: string;
  dateCreation: string;
}

export interface TransactionRequestDTO {
  montantCaution: number;
}

export interface AnnonceCreateInput {
  titre: string;
  description: string;
  type: 'STUDIO' | 'APPARTEMENT' | 'VILLA';
  modeTransaction: 'LOCATION' | 'VENTE';
  prix: number;
  superficie: number;
  nombrePieces: number;
  etage?: number;
  quartier: string;
  ville: string;
  latitude: number;
  longitude: number;
  meuble?: boolean;
  animauxAcceptes?: boolean;
  lienVisite360?: string;
  equipements?: string[];
  proximites?: string[];
}

export interface VerificationDecisionDTO {
  statutVerification: 'VERIFIEE' | 'REJETEE';
  motifRejet?: string;
}

export interface AvisDTO {
  id: string;
  annonceId: string;
  note: number;
  commentaire?: string;
  auteurId: string;
  auteurPrenom: string;
  auteurNom: string;
  dateCreation: string;
}

export interface AvisSummaryDTO {
  noteMoyenne?: number;
  nombreAvis: number;
  avis: AvisDTO[];
}

export interface ReputationDTO {
  utilisateurId: string;
  noteMoyenne?: number;
  nombreAvis: number;
}

export type MotifSignalement =
  | 'FAUSSE_ANNONCE'
  | 'PRIX_TROMPEUR'
  | 'PROPRIETAIRE_INJOIGNABLE'
  | 'ARNAQUE_SUSPECTEE'
  | 'AUTRE';

export type StatutSignalement = 'EN_ATTENTE' | 'TRAITE' | 'REJETE';

export interface SignalementDTO {
  id: string;
  annonceId: string;
  annonceTitre: string;
  auteurId: string;
  auteurEmail: string;
  motif: MotifSignalement;
  description?: string;
  statut: StatutSignalement;
  dateCreation: string;
}

export interface TraiterSignalementInput {
  statut: StatutSignalement;
  suspendreAnnonce?: boolean;
}
