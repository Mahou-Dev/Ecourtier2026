import React, { useState } from 'react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { 
  ShieldCheck, 
  MapPin, 
  CheckCircle2, 
  ArrowLeft,
  Calendar,
  Clock,
  FileCheck
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, RendezVousResponseDTO, TransactionResponseDTO } from '../types';
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

  // Fetch listing details
  const { data: property, isLoading, isError } = useQuery<AnnonceDTO>({
    queryKey: ['annonce', id],
    queryFn: () => apiFetch<AnnonceDTO>(`/api/v1/annonces/${id}`),
    enabled: !!id,
  });

  // Fetch user visits to check if an EFFECTUE visit exists for this listing
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

  // Initiate transaction handler
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
      
      {/* NAVIGATION BAR */}
      <div className="flex items-center justify-between">
        <Link
          to="/recherche"
          className="inline-flex items-center gap-2 text-xs font-semibold text-sahel-700 hover:text-terracotta-600 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Retour aux résultats</span>
        </Link>
      </div>

      {/* HEADER TITLE & BADGES */}
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
        </div>

        <h1 className="font-display font-extrabold text-2xl md:text-4xl text-charcoal-900 tracking-tight">
          {property.titre}
        </h1>

        <div className="flex items-center gap-2 text-xs font-semibold text-charcoal-600">
          <MapPin className="w-4 h-4 text-terracotta-500" />
          <span>{property.quartier}, {property.ville}</span>
        </div>
      </div>

      {/* GALLERY GRID */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-3 h-[380px] rounded-3xl overflow-hidden shadow-warm-card">
        <div className="md:col-span-2 h-full cursor-pointer relative group" onClick={() => { setSelectedPhotoIndex(0); setIsLightboxOpen(true); }}>
          <img src={photosList[0]} alt="Principal" className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300" />
        </div>
        <div className="hidden md:grid grid-cols-1 gap-3 h-full col-span-2">
          <div className="grid grid-cols-2 gap-3 h-full">
            {photosList.slice(1, 5).map((photo, idx) => (
              <div key={idx} className="h-full cursor-pointer overflow-hidden relative group" onClick={() => { setSelectedPhotoIndex(idx + 1); setIsLightboxOpen(true); }}>
                <img src={photo} alt={`Photo ${idx + 2}`} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* DETAILS GRID */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* LEFT COLUMN */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Key Specifications */}
          <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Superficie</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.superficie} m²</p>
            </div>
            <div>
              <p className="text-xs text-charcoal-500 font-medium">Nombre de pièces</p>
              <p className="font-display font-bold text-lg text-charcoal-900">{property.nombrePieces}</p>
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

          {/* Landlord Info */}
          {property.auteur && (
            <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card space-y-4">
              <h3 className="font-display font-bold text-base text-charcoal-900">Proposé par un propriétaire identifié</h3>
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
            </div>
          )}

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
