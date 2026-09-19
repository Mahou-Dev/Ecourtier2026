import React, { useState } from 'react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { 
  ShieldCheck, 
  MapPin, 
  CheckCircle2, 
  ArrowLeft,
  Calendar,
  Clock,
  FileCheck,
  Star,
  AlertTriangle,
  Compass,
  UserCheck
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { 
  AnnonceDTO, 
  RendezVousResponseDTO, 
  TransactionResponseDTO, 
  AvisSummaryDTO, 
  ReputationDTO 
} from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { LeafletMap } from '../components/map/LeafletMap';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { Avatar } from '../components/ui/Avatar';
import { Skeleton } from '../components/ui/Skeleton';
import Lightbox from 'yet-another-react-lightbox';
import 'yet-another-react-lightbox/styles.css';
import { toast } from 'sonner';
import { TransactionDetailsModal } from '../components/transaction/TransactionDetailsModal';
import { SignalementModal } from '../components/ui/SignalementModal';

export const PropertyDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { utilisateur } = useAuthStore();

  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const [selectedPhotoIndex, setSelectedPhotoIndex] = useState(0);
  const [isVisitModalOpen, setIsVisitModalOpen] = useState(false);
  const [visitDate, setVisitDate] = useState('');
  const [visitTime, setVisitTime] = useState('10:00');
  const [isSubmittingVisit, setIsSubmittingVisit] = useState(false);

  const [isTxModalOpen, setIsTxModalOpen] = useState(false);
  const [selectedTx, setSelectedTx] = useState<TransactionResponseDTO | null>(null);
  const [isInitiatingTx, setIsInitiatingTx] = useState(false);

  const [isSignalementModalOpen, setIsSignalementModalOpen] = useState(false);
  const [is360ModalOpen, setIs360ModalOpen] = useState(false);

  // Avis Form state
  const [newRating, setNewRating] = useState<number>(5);
  const [newComment, setNewComment] = useState('');
  const [isSubmittingAvis, setIsSubmittingAvis] = useState(false);

  // Fetch listing details
  const { data: property, isLoading, isError } = useQuery<AnnonceDTO>({
    queryKey: ['annonce', id],
    queryFn: () => apiFetch<AnnonceDTO>(`/api/v1/annonces/${id}`),
    enabled: !!id,
  });

  // Fetch listing reviews
  const { data: avisSummary, refetch: refetchAvis } = useQuery<AvisSummaryDTO>({
    queryKey: ['annonce-avis', id],
    queryFn: () => apiFetch<AvisSummaryDTO>(`/api/v1/annonces/${id}/avis`),
    enabled: !!id,
  });

  // Fetch landlord reputation
  const { data: landlordReputation } = useQuery<ReputationDTO>({
    queryKey: ['reputation-utilisateur', property?.auteur?.id],
    queryFn: () => apiFetch<ReputationDTO>(`/api/v1/utilisateurs/${property?.auteur?.id}/reputation`),
    enabled: !!property?.auteur?.id,
  });

  // Fetch user visits
  const { data: userVisits } = useQuery<RendezVousResponseDTO[]>({
    queryKey: ['my-visites'],
    queryFn: () => apiFetch<RendezVousResponseDTO[]>('/api/v1/rendez-vous/mes-rendez-vous'),
    enabled: !!utilisateur,
  });

  // Fetch user transactions
  const { data: userTransactions } = useQuery<TransactionResponseDTO[]>({
    queryKey: ['my-transactions'],
    queryFn: () => apiFetch<TransactionResponseDTO[]>('/api/v1/transactions/mes-transactions'),
    enabled: !!utilisateur,
  });

  const hasEffectueVisit = userVisits?.some(
    (v) => v.annonceId === id && v.statut === 'EFFECTUE'
  );

  const existingTx = userTransactions?.find((tx) => tx.annonceId === id);

  const handleDemanderVisiteClick = () => {
    if (!utilisateur) {
      toast.info('Veuillez vous connecter pour planifier une visite');
      navigate('/connexion', { state: { from: location.pathname } });
      return;
    }

    if (utilisateur.role !== 'LOCATAIRE') {
      toast.warning('Seuls les utilisateurs inscrits comme Locataire peuvent demander une visite');
      return;
    }

    setIsVisitModalOpen(true);
  };

  const handleConfirmVisit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!visitDate || !visitTime || !id) {
      toast.error('Veuillez sélectionner une date et une heure');
      return;
    }

    setIsSubmittingVisit(true);
    try {
      const dateHeureIso = `${visitDate}T${visitTime}:00`;
      const res = await apiFetch<RendezVousResponseDTO>(`/api/v1/annonces/${id}/rendez-vous`, {
        method: 'POST',
        body: JSON.stringify({
          dateHeure: dateHeureIso,
        }),
      });

      toast.success(`Demande de visite transmise avec succès ! (RDV #${res.id.slice(0, 8)})`);
      queryClient.invalidateQueries({ queryKey: ['my-visites'] });
      setIsVisitModalOpen(false);
    } catch (err: any) {
      toast.error(err.message || 'Échec de la réservation de visite');
    } finally {
      setIsSubmittingVisit(false);
    }
  };

  const handleInitierTransaction = async () => {
    if (!id || !property) return;
    setIsInitiatingTx(true);
    try {
      const tx = await apiFetch<TransactionResponseDTO>(`/api/v1/annonces/${id}/transactions`, {
        method: 'POST',
        body: JSON.stringify({ montantCaution: property.prix }),
      });
      toast.success('Transaction de location initiée avec succès !');
      queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
      setSelectedTx(tx);
      setIsTxModalOpen(true);
    } catch (err: any) {
      toast.error(err.message || 'Impossible d\'initier la transaction');
    } finally {
      setIsInitiatingTx(false);
    }
  };

  const handleSoumettreAvis = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setIsSubmittingAvis(true);
    try {
      await apiFetch(`/api/v1/annonces/${id}/avis`, {
        method: 'POST',
        body: JSON.stringify({ note: newRating, commentaire: newComment }),
      });
      toast.success('Avis publié avec succès ! Merci pour votre retour.');
      setNewComment('');
      refetchAvis();
    } catch (err: any) {
      if (err.status === 409 || err.message?.includes('déjà noté')) {
        toast.warning('Vous avez déjà noté cette annonce');
      } else if (err.status === 403 || err.message?.includes('visite validée')) {
        toast.error('Seuls les locataires ayant effectué une visite validée peuvent déposer un avis');
      } else {
        toast.error(err.message || 'Erreur lors de la publication de l\'avis');
      }
    } finally {
      setIsSubmittingAvis(false);
    }
  };

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-8 space-y-6">
        <Skeleton variant="rect" className="w-full h-80 rounded-3xl" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton variant="text" className="w-1/2 h-8" />
            <Skeleton variant="text" className="w-full h-24" />
          </div>
          <Skeleton variant="card" className="h-64" />
        </div>
      </div>
    );
  }

  if (isError || !property) {
    return (
      <div className="max-w-md mx-auto my-16 p-8 text-center space-y-4 bg-white rounded-3xl border border-argile-200">
        <h2 className="font-display font-bold text-xl text-charcoal-900">Annonce introuvable</h2>
        <p className="text-xs text-charcoal-600">Cette annonce n'existe plus ou a été retirée de la publication.</p>
        <Button variant="primary" onClick={() => navigate('/recherche')}>
          Retourner aux recherches
        </Button>
      </div>
    );
  }

  const photosList = property.photos && property.photos.length > 0
    ? property.photos.map(p => getPhotoUrl(p.url))
    : ['https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80'];

  return (
    <div className="max-w-7xl mx-auto px-4 py-6 space-y-8">
      
      {/* NAVIGATION BAR & DISCRETE REPORT BUTTON */}
      <div className="flex items-center justify-between">
        <Link
          to="/recherche"
          className="inline-flex items-center gap-2 text-xs font-semibold text-sahel-700 hover:text-terracotta-600 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Retour aux résultats</span>
        </Link>

        <button
          onClick={() => {
            if (!utilisateur) {
              toast.info('Veuillez vous connecter pour signaler une annonce');
              navigate('/connexion', { state: { from: location.pathname } });
              return;
            }
            setIsSignalementModalOpen(true);
          }}
          className="inline-flex items-center gap-1.5 text-xs text-charcoal-500 hover:text-rose-600 transition-colors py-1 px-2.5 rounded-lg border border-transparent hover:border-rose-200"
        >
          <AlertTriangle className="w-3.5 h-3.5 text-rose-500" />
          <span>Signaler cette annonce</span>
        </button>
      </div>

      {/* HEADER TITLE & BADGES & RATING SUMMARY */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="terracotta">{property.modeTransaction}</Badge>
          <Badge variant="sahel">{property.type}</Badge>
          {property.statutVerification === 'VERIFIEE' && (
            <Badge variant="success" className="flex items-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
              <span>Contrôle Cadastral Effectué</span>
            </Badge>
          )}

          {/* Average Rating Star Badge */}
          {avisSummary?.noteMoyenne ? (
            <div className="flex items-center gap-1 text-xs font-bold bg-amber-50 text-amber-900 border border-amber-300 px-2.5 py-1 rounded-full shadow-xs">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-500" />
              <span>{avisSummary.noteMoyenne.toFixed(1)} / 5</span>
              <span className="text-amber-700 text-[10px]">({avisSummary.nombreAvis} avis)</span>
            </div>
          ) : (
            <span className="text-[11px] text-charcoal-400 bg-sand-200 px-2.5 py-0.5 rounded-full font-medium">
              Aucun avis
            </span>
          )}
        </div>

        <h1 className="font-display font-extrabold text-2xl md:text-4xl text-charcoal-900 tracking-tight">
          {property.titre}
        </h1>

        <div className="flex flex-wrap items-center gap-4 text-xs font-semibold text-charcoal-600">
          <span className="flex items-center gap-1">
            <MapPin className="w-4 h-4 text-terracotta-500" />
            {property.quartier}, {property.ville}
          </span>

          {property.lienVisite360 && (
            <button
              onClick={() => setIs360ModalOpen(true)}
              className="flex items-center gap-1.5 text-terracotta-600 hover:text-terracotta-700 bg-terracotta-50 hover:bg-terracotta-100 px-3 py-1 rounded-full border border-terracotta-200 font-bold transition-all"
            >
              <Compass className="w-4 h-4 animate-spin-slow" />
              <span>Visite Virtuelle 360°</span>
            </button>
          )}
        </div>
      </div>

      {/* GALLERY GRID WITH LAZY LOADING */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-3 h-[380px] rounded-3xl overflow-hidden shadow-warm-card">
        <div className="md:col-span-2 h-full cursor-pointer relative group" onClick={() => { setSelectedPhotoIndex(0); setIsLightboxOpen(true); }}>
          <img src={photosList[0]} alt="Principal" loading="eager" className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300" />
        </div>
        <div className="hidden md:grid grid-cols-1 gap-3 h-full col-span-2">
          <div className="grid grid-cols-2 gap-3 h-full">
            {photosList.slice(1, 5).map((photo, idx) => (
              <div key={idx} className="h-full cursor-pointer overflow-hidden relative group" onClick={() => { setSelectedPhotoIndex(idx + 1); setIsLightboxOpen(true); }}>
                <img src={photo} alt={`Photo ${idx + 2}`} loading="lazy" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* DETAILS GRID */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* LEFT COLUMN */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Key Specifications (Including Étage) */}
          <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card grid grid-cols-2 sm:grid-cols-5 gap-4 text-center">
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Superficie</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.superficie} m²</p>
            </div>
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Nombre de pièces</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.nombrePieces}</p>
            </div>
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Étage</p>
              <p className="font-display font-bold text-lg text-charcoal-900">
                {property.etage !== undefined && property.etage !== null
                  ? property.etage === 0 ? 'RDC' : `${property.etage}e`
                  : 'N/A'}
              </p>
            </div>
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Meublé</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.meuble ? 'Oui' : 'Non'}</p>
            </div>
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Animaux</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.animauxAcceptes ? 'Acceptés' : 'Refusés'}</p>
            </div>
          </div>

          {/* Description */}
          <div className="space-y-3">
            <h3 className="font-display font-bold text-lg text-charcoal-900">Description du bien</h3>
            <p className="text-sm text-charcoal-700 leading-relaxed whitespace-pre-line">{property.description}</p>
          </div>

          {/* Location Map */}
          {property.latitude && property.longitude && (
            <div className="space-y-3 border-t border-argile-200 pt-6">
              <h3 className="font-display font-bold text-lg text-charcoal-900">Emplacement & Carte</h3>
              <div className="h-72 rounded-2xl overflow-hidden border border-argile-200 shadow-sm">
                <LeafletMap
                  properties={[property]}
                  selectedPropertyId={property.id}
                  center={[property.latitude, property.longitude]}
                  zoom={14}
                />
              </div>
            </div>
          )}

          {/* Landlord Info & Calculated Owner Reputation */}
          {property.auteur && (
            <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card space-y-4">
              <h3 className="font-display font-bold text-base text-charcoal-900">Proposé par un propriétaire identifié</h3>
              <div className="flex items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                  <Avatar src={property.auteur.photoProfilUrl} name={`${property.auteur.prenom} ${property.auteur.nom}`} size="lg" isVerified />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h4 className="font-display font-bold text-base text-charcoal-900">{property.auteur.prenom} {property.auteur.nom}</h4>
                      <Badge variant="sahel">{property.auteur.role}</Badge>
                    </div>
                    <p className="text-xs text-charcoal-600">Tel: {property.auteur.telephone}</p>
                  </div>
                </div>

                {/* Landlord Reputation Badge */}
                <div className="text-right bg-sand-100 p-3 rounded-xl border border-argile-300">
                  <p className="text-[11px] text-charcoal-500 font-semibold uppercase tracking-wider">Réputation Propriétaire</p>
                  {landlordReputation?.noteMoyenne ? (
                    <div className="flex items-center justify-end gap-1.5 mt-0.5">
                      <Star className="w-4 h-4 fill-amber-400 text-amber-500" />
                      <span className="font-display font-extrabold text-base text-charcoal-900">
                        {landlordReputation.noteMoyenne.toFixed(1)} / 5
                      </span>
                      <span className="text-xs text-charcoal-500 font-medium">
                        ({landlordReputation.nombreAvis} avis)
                      </span>
                    </div>
                  ) : (
                    <p className="text-xs text-charcoal-500 mt-0.5">Aucun avis encore</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* REVIEWS SECTION */}
          <div className="space-y-6 border-t border-argile-200 pt-6">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-display font-bold text-xl text-charcoal-900">
                  Avis et Évaluations ({avisSummary?.nombreAvis ?? 0})
                </h3>
                <p className="text-xs text-charcoal-500">
                  Avis vérifiés déposés exclusivement par des locataires ayant effectué une visite.
                </p>
              </div>
            </div>

            {/* Leave a review form if LOCATAIRE with EFFECTUE visit */}
            {utilisateur?.role === 'LOCATAIRE' && hasEffectueVisit && (
              <form onSubmit={handleSoumettreAvis} className="p-5 rounded-2xl bg-sahel-50/50 border border-sahel-200 space-y-4">
                <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
                  <UserCheck className="w-4 h-4 text-sahel-700" />
                  Laisser un avis suite à votre visite
                </h4>
                
                <div className="flex items-center gap-2">
                  <span className="text-xs text-charcoal-700 font-medium">Votre note :</span>
                  <div className="flex items-center gap-1">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => setNewRating(star)}
                        className="p-1 transition-transform hover:scale-110 focus:outline-none"
                      >
                        <Star
                          className={`w-5 h-5 ${
                            star <= newRating ? 'fill-amber-400 text-amber-500' : 'text-argile-300'
                          }`}
                        />
                      </button>
                    ))}
                  </div>
                </div>

                <textarea
                  rows={3}
                  value={newComment}
                  onChange={(e) => setNewComment(e.target.value)}
                  placeholder="Partagez votre retour d'expérience sur ce logement et le propriétaire..."
                  className="w-full text-xs p-3 rounded-xl border border-argile-300 focus:outline-none focus:ring-2 focus:ring-sahel-500/30 bg-white resize-none"
                />

                <Button variant="primary" size="sm" type="submit" isLoading={isSubmittingAvis}>
                  Publier mon avis
                </Button>
              </form>
            )}

            {/* Reviews List */}
            {avisSummary?.avis && avisSummary.avis.length > 0 ? (
              <div className="space-y-3">
                {avisSummary.avis.map((av) => (
                  <div key={av.id} className="p-4 bg-white rounded-2xl border border-argile-200 shadow-xs space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-display font-bold text-xs text-charcoal-900">
                          {av.auteurPrenom} {av.auteurNom}
                        </span>
                        <div className="flex items-center gap-0.5">
                          {[1, 2, 3, 4, 5].map((s) => (
                            <Star
                              key={s}
                              className={`w-3 h-3 ${s <= av.note ? 'fill-amber-400 text-amber-500' : 'text-argile-200'}`}
                            />
                          ))}
                        </div>
                      </div>
                      <span className="text-[10px] text-charcoal-400">
                        {new Date(av.dateCreation).toLocaleDateString('fr-FR')}
                      </span>
                    </div>

                    {av.commentaire && (
                      <p className="text-xs text-charcoal-700 leading-relaxed">
                        "{av.commentaire}"
                      </p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-charcoal-500 italic p-4 bg-white rounded-2xl border border-argile-200 text-center">
                Aucun commentaire n'a encore été déposé pour cette annonce.
              </p>
            )}
          </div>

        </div>

        {/* RIGHT COLUMN: STICKY BOOKING / TRANSACTION CARD */}
        <div className="lg:col-span-1">
          <div className="sticky top-24 space-y-4">
            
            <Card className="border-terracotta-200 shadow-warm-hover">
              <CardBody className="space-y-5">
                
                {/* Price Display */}
                <div>
                  <span className="text-xs font-semibold text-charcoal-500 uppercase tracking-wider">
                    {property.modeTransaction === 'LOCATION' ? 'Loyer Mensuel' : 'Prix de Vente'}
                  </span>
                  <div className="flex items-baseline gap-1 mt-0.5">
                    <span className="font-display font-extrabold text-3xl text-terracotta-600">
                      {property.prix.toLocaleString('fr-FR')} FCFA
                    </span>
                    <span className="text-xs text-charcoal-500">
                      {property.modeTransaction === 'LOCATION' ? '/ mois' : ''}
                    </span>
                  </div>
                  <p className="text-[11px] text-sahel-800 font-semibold mt-1 flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600" />
                    <span>Caution 100% sécurisée sous séquestre Wave</span>
                  </p>
                </div>

                {/* Primary Action Buttons */}
                <div className="space-y-3 pt-2">
                  
                  {/* Case 1: Transaction already exists */}
                  {existingTx ? (
                    <div className="space-y-2 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200">
                      <p className="text-xs font-bold text-emerald-900 flex items-center gap-1.5">
                        <FileCheck className="w-4 h-4 text-emerald-600" />
                        Dossier de transaction en cours
                      </p>
                      <Button
                        variant="primary"
                        className="w-full justify-center bg-emerald-700 hover:bg-emerald-800 text-white"
                        onClick={() => {
                          setSelectedTx(existingTx);
                          setIsTxModalOpen(true);
                        }}
                      >
                        Gérer la caution & Signer le bail
                      </Button>
                    </div>
                  ) : hasEffectueVisit ? (
                    /* Case 2: Visit is EFFECTUE -> Option to initiate transaction */
                    <div className="space-y-2 p-3.5 rounded-xl bg-sand-50 border border-terracotta-200">
                      <p className="text-xs font-bold text-sahel-900 flex items-center gap-1.5">
                        <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                        Visite effectuée avec succès !
                      </p>
                      <p className="text-[11px] text-sahel-600">
                        Vous pouvez dès maintenant réserver ce bien et consigner la caution sous séquestre.
                      </p>
                      <Button
                        variant="primary"
                        size="lg"
                        className="w-full justify-center"
                        isLoading={isInitiatingTx}
                        onClick={handleInitierTransaction}
                      >
                        Initier la location / l'achat
                      </Button>
                    </div>
                  ) : (
                    /* Case 3: Request visit */
                    <Button
                      variant="primary"
                      size="lg"
                      className="w-full justify-center"
                      onClick={handleDemanderVisiteClick}
                    >
                      Demander une visite du bien
                    </Button>
                  )}

                </div>

                <div className="text-[11px] text-charcoal-500 space-y-1.5 pt-2 border-t border-argile-200">
                  <p>✔ Aucun frais de visite préalable</p>
                  <p>✔ Séquestre bloqué via Wave sandbox</p>
                  <p>✔ Contrat de bail numérique avec double signature électronique</p>
                </div>

              </CardBody>
            </Card>

          </div>
        </div>

      </div>

      {/* MODAL: VISIT REQUEST FORM */}
      <Modal
        isOpen={isVisitModalOpen}
        onClose={() => setIsVisitModalOpen(false)}
        title="Planifier une Visite du Logement"
      >
        <form onSubmit={handleConfirmVisit} className="space-y-4">
          <p className="text-xs text-charcoal-600">
            Sélectionnez la date et l'heure souhaitées pour visiter <strong>{property.titre}</strong>.
          </p>
          
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-charcoal-800 flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5 text-terracotta-500" /> Date souhaitée
            </label>
            <input
              type="date"
              required
              min={new Date().toISOString().split('T')[0]}
              value={visitDate}
              onChange={(e) => setVisitDate(e.target.value)}
              className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900 bg-sand-50"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-charcoal-800 flex items-center gap-1">
              <Clock className="w-3.5 h-3.5 text-terracotta-500" /> Heure de visite
            </label>
            <input
              type="time"
              required
              value={visitTime}
              onChange={(e) => setVisitTime(e.target.value)}
              className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900 bg-sand-50"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            size="md"
            className="w-full justify-center"
            disabled={isSubmittingVisit}
          >
            {isSubmittingVisit ? 'Envoi en cours...' : 'Confirmer la demande de visite'}
          </Button>
        </form>
      </Modal>

      {/* MODAL: 360 VIRTUAL TOUR */}
      {property.lienVisite360 && (
        <Modal
          isOpen={is360ModalOpen}
          onClose={() => setIs360ModalOpen(false)}
          title="Visite Virtuelle 360° du Logement"
        >
          <div className="space-y-4">
            <div className="aspect-video w-full bg-charcoal-900 rounded-2xl overflow-hidden border border-argile-300 flex items-center justify-center">
              <iframe
                src={property.lienVisite360}
                title="Visite 360"
                className="w-full h-full border-0"
                allowFullScreen
              />
            </div>
            <div className="flex justify-end">
              <a
                href={property.lienVisite360}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs font-semibold text-terracotta-600 hover:text-terracotta-700 flex items-center gap-1"
              >
                Ouvrir en plein écran dans un nouvel onglet &rarr;
              </a>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: SIGNALEMENT */}
      <SignalementModal
        annonceId={property.id}
        annonceTitre={property.titre}
        isOpen={isSignalementModalOpen}
        onClose={() => setIsSignalementModalOpen(false)}
      />

      {/* MODAL: TRANSACTION DETAILS & LEASE SIGNATURE */}
      <TransactionDetailsModal
        isOpen={isTxModalOpen}
        onClose={() => setIsTxModalOpen(false)}
        transaction={selectedTx}
        onRefresh={() => {
          queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
        }}
      />

      {/* LIGHTBOX */}
      <Lightbox
        open={isLightboxOpen}
        close={() => setIsLightboxOpen(false)}
        index={selectedPhotoIndex}
        slides={photosList.map((src) => ({ src }))}
      />

    </div>
  );
};
