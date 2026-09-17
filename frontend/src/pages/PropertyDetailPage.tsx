import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { 
  ShieldCheck, 
  MapPin, 
  Sparkles, 
  CheckCircle2, 
  Zap, 
  Droplet, 
  Shield, 
  Wind, 
  Car, 
  Wifi, 
  ArrowLeft,
  Share2,
  Heart
} from 'lucide-react';
import { MOCK_PROPERTIES } from '../mocks/properties';
import { LeafletMap } from '../components/map/LeafletMap';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { Avatar } from '../components/ui/Avatar';
import Lightbox from 'yet-another-react-lightbox';
import 'yet-another-react-lightbox/styles.css';

export const PropertyDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const property = MOCK_PROPERTIES.find((p) => p.id === id) || MOCK_PROPERTIES[0];

  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const [selectedPhotoIndex, setSelectedPhotoIndex] = useState(0);
  const [isProofModalOpen, setIsProofModalOpen] = useState(false);
  const [isVisitModalOpen, setIsVisitModalOpen] = useState(false);
  const [visitSubmitted, setVisitSubmitted] = useState(false);

  return (
    <div className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-8 py-6 space-y-8 pb-24">
      
      {/* Top Back Nav & Share */}
      <div className="flex items-center justify-between">
        <Link to="/recherche" className="inline-flex items-center gap-2 text-xs font-bold text-charcoal-700 hover:text-terracotta-600 transition-colors">
          <ArrowLeft className="w-4 h-4" />
          <span>Retour à la recherche</span>
        </Link>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" leftIcon={<Share2 className="w-4 h-4" />}>Partager</Button>
          <Button variant="ghost" size="sm" leftIcon={<Heart className="w-4 h-4 text-terracotta-500" />}>Favoris</Button>
        </div>
      </div>

      {/* AIRBNB-STYLE ASYMMETRICAL PHOTO GALLERY */}
      <div className="space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 h-[360px] sm:h-[460px] rounded-3xl overflow-hidden border border-argile-200 shadow-warm-card">
          
          {/* Main Large Photo */}
          <div
            className="md:col-span-2 relative cursor-pointer group overflow-hidden bg-sand-200"
            onClick={() => {
              setSelectedPhotoIndex(0);
              setIsLightboxOpen(true);
            }}
          >
            <img
              src={property.images[0]}
              alt={property.title}
              className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-charcoal-900/10 group-hover:bg-transparent transition-colors" />
          </div>

          {/* Secondary Photos */}
          <div className="hidden md:grid col-span-2 grid-cols-2 gap-3">
            {property.images.slice(1, 5).map((img, idx) => (
              <div
                key={idx}
                className="relative cursor-pointer group overflow-hidden bg-sand-200 h-full"
                onClick={() => {
                  setSelectedPhotoIndex(idx + 1);
                  setIsLightboxOpen(true);
                }}
              >
                <img
                  src={img}
                  alt={`${property.title} - photo ${idx + 2}`}
                  className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
                {idx === 3 && property.images.length > 4 && (
                  <div className="absolute inset-0 bg-charcoal-900/60 flex items-center justify-center text-white font-display font-bold text-sm">
                    +{property.images.length - 4} photos
                  </div>
                )}
              </div>
            ))}
          </div>

        </div>
      </div>

      {/* MAIN CONTENT GRID (LEFT DETAILS + RIGHT STICKY BOOKING CARD) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* LEFT COLUMN: PROPERTY INFORMATION */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Title, Badge & Location Header */}
          <div className="space-y-3 border-b border-argile-200 pb-6">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="terracotta" className="text-xs">
                <ShieldCheck className="w-4 h-4 mr-1 inline" />
                Vérifié Niveau {property.verificationLevel}
              </Badge>
              <Badge variant="sahel">Caution Séquestre Garantie</Badge>
              {property.isFurnished && <Badge variant="ocher">Meublé</Badge>}
            </div>

            <h1 className="font-display font-extrabold text-2xl sm:text-3xl text-charcoal-900 leading-tight">
              {property.title}
            </h1>

            <div className="flex items-center gap-2 text-sm text-sahel-800 font-semibold">
              <MapPin className="w-4 h-4 text-terracotta-500" />
              <span>{property.address} ({property.neighborhood}, {property.city})</span>
            </div>
          </div>

          {/* VERIFICATION & PROOF BADGE BLOCK */}
          <div className="p-5 rounded-2xl bg-sahel-50 border border-sahel-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-sahel-800 text-white flex items-center justify-center shrink-0">
                <ShieldCheck className="w-6 h-6 text-ocher-400" />
              </div>
              <div>
                <h4 className="font-display font-bold text-sm text-sahel-900">
                  {property.verificationDetails.verificationBadgeLabel}
                </h4>
                <p className="text-xs text-sahel-700">
                  Titre foncier cadastré N° <span className="font-mono font-bold">{property.verificationDetails.cadastralNumber}</span>
                </p>
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              className="bg-white border-sahel-300 text-sahel-900 hover:bg-sahel-100 shrink-0"
              onClick={() => setIsProofModalOpen(true)}
            >
              Voir la preuve cadastrale
            </Button>
          </div>

          {/* KEY AMENITIES & SPECS GRID */}
          <div className="space-y-4">
            <h3 className="font-display font-bold text-lg text-charcoal-900">
              Caractéristiques du bien
            </h3>
            
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-semibold text-charcoal-800">
              <div className="p-3.5 rounded-xl bg-sand-200/80 border border-argile-200">
                <span className="text-charcoal-500 block text-[11px]">Surface</span>
                <span className="text-base font-bold text-charcoal-900">{property.surface} m²</span>
              </div>
              <div className="p-3.5 rounded-xl bg-sand-200/80 border border-argile-200">
                <span className="text-charcoal-500 block text-[11px]">Pièces</span>
                <span className="text-base font-bold text-charcoal-900">{property.rooms} pièces</span>
              </div>
              <div className="p-3.5 rounded-xl bg-sand-200/80 border border-argile-200">
                <span className="text-charcoal-500 block text-[11px]">Chambres</span>
                <span className="text-base font-bold text-charcoal-900">{property.bedrooms}</span>
              </div>
              <div className="p-3.5 rounded-xl bg-sand-200/80 border border-argile-200">
                <span className="text-charcoal-500 block text-[11px]">Salles de bain</span>
                <span className="text-base font-bold text-charcoal-900">{property.bathrooms}</span>
              </div>
            </div>
          </div>

          {/* SENEGAL CONTEXT EQUIPMENT ICONS BLOCK */}
          <div className="space-y-4 border-t border-argile-200 pt-6">
            <h3 className="font-display font-bold text-lg text-charcoal-900">
              Équipements & Confort au Sénégal
            </h3>
            
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-xs">
              {property.hasGenerator && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center">
                    <Zap className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Groupe Électrogène</span>
                    <span className="text-[11px] text-charcoal-500">Relais automatique en cas de délestage</span>
                  </div>
                </div>
              )}

              {property.hasWaterTank && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center">
                    <Droplet className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Réserve d'eau & Surpresseur</span>
                    <span className="text-[11px] text-charcoal-500">Pression constante garantie</span>
                  </div>
                </div>
              )}

              {property.hasSecurityGuard && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center">
                    <Shield className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Gardiennage 24/7</span>
                    <span className="text-[11px] text-charcoal-500">Sécurité de la résidence</span>
                  </div>
                </div>
              )}

              {property.hasAirConditioning && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-cyan-100 text-cyan-700 flex items-center justify-center">
                    <Wind className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Climatisation Inverter</span>
                    <span className="text-[11px] text-charcoal-500">Dans toutes les chambres</span>
                  </div>
                </div>
              )}

              {property.hasParking && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-purple-100 text-purple-700 flex items-center justify-center">
                    <Car className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Parking Sécurisé</span>
                    <span className="text-[11px] text-charcoal-500">Emplacement réservé</span>
                  </div>
                </div>
              )}

              {property.hasWifi && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-white border border-argile-200">
                  <div className="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center">
                    <Wifi className="w-4 h-4" />
                  </div>
                  <div>
                    <span className="font-bold text-charcoal-900 block">Fibre Optique Wifi</span>
                    <span className="text-[11px] text-charcoal-500">Haut débit prêt</span>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* DESCRIPTION */}
          <div className="space-y-3 border-t border-argile-200 pt-6">
            <h3 className="font-display font-bold text-lg text-charcoal-900">Description du logement</h3>
            <p className="text-sm text-charcoal-700 leading-relaxed whitespace-pre-line">
              {property.description}
            </p>
          </div>

          {/* LOCALISATION & NEIGHBORHOOD MAP */}
          <div className="space-y-3 border-t border-argile-200 pt-6">
            <h3 className="font-display font-bold text-lg text-charcoal-900">Emplacement & Quartier</h3>
            <p className="text-xs text-charcoal-600">
              Proximité immédiate : {property.nearbyPlaces.join(' • ')}
            </p>
            <div className="h-72 rounded-2xl overflow-hidden border border-argile-200 shadow-sm">
              <LeafletMap
                properties={[property]}
                selectedPropertyId={property.id}
                center={[property.lat, property.lng]}
                zoom={14}
              />
            </div>
          </div>

          {/* AGENT / LANDLORD PROFILE SECTION */}
          <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card space-y-4">
            <h3 className="font-display font-bold text-base text-charcoal-900">Proposé par un partenaire certifié</h3>
            <div className="flex items-center gap-4">
              <Avatar src={property.owner.avatar} name={property.owner.name} size="lg" isVerified />
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h4 className="font-display font-bold text-base text-charcoal-900">{property.owner.name}</h4>
                  <Badge variant="sahel">{property.owner.role === 'COURTIER_CERTIFIE' ? 'Courtier Agréé' : 'Propriétaire Direct'}</Badge>
                </div>
                <div className="flex items-center gap-3 text-xs text-charcoal-600">
                  <span>⭐ {property.owner.rating} ({property.owner.reviewCount} avis certifiés)</span>
                  <span>⚡ Réponse : {property.owner.responseTime}</span>
                </div>
              </div>
            </div>
          </div>

        </div>

        {/* RIGHT COLUMN: STICKY BOOKING & TRANSACTION CARD */}
        <div className="lg:col-span-1">
          <div className="sticky top-24 space-y-4">
            
            <Card className="border-terracotta-200 shadow-warm-hover">
              <CardBody className="space-y-5">
                
                {/* Price Display */}
                <div>
                  <span className="text-xs font-semibold text-charcoal-500 uppercase tracking-wider">Loyer Mensuel</span>
                  <div className="flex items-baseline gap-1 mt-0.5">
                    <span className="font-display font-extrabold text-3xl text-terracotta-600">
                      {property.price.toLocaleString('fr-FR')} FCFA
                    </span>
                    <span className="text-xs text-charcoal-500">{property.pricePeriod}</span>
                  </div>
                  <p className="text-[11px] text-sahel-800 font-semibold mt-1 flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600" />
                    <span>Caution 100% sécurisée sous séquestre bancaire</span>
                  </p>
                </div>

                {/* Primary Action Buttons */}
                <div className="space-y-3 pt-2">
                  <Button
                    variant="primary"
                    size="lg"
                    className="w-full justify-center"
                    onClick={() => setIsVisitModalOpen(true)}
                  >
                    Demander une visite certifiée
                  </Button>
                  
                  <Button
                    variant="secondary"
                    size="lg"
                    className="w-full justify-center"
                    leftIcon={<Sparkles className="w-4 h-4 text-ocher-400" />}
                  >
                    Discuter avec l'IA E-Courtier
                  </Button>
                </div>

                <div className="text-[11px] text-charcoal-500 space-y-1.5 pt-2 border-t border-argile-200">
                  <p>✔ Aucun frais de visite à l'avance</p>
                  <p>✔ Déblocage des fonds après état des lieux signé</p>
                  <p>✔ Bail généré conforme au code sénégalais</p>
                </div>

              </CardBody>
            </Card>

          </div>
        </div>

      </div>

      {/* MOBILE STICKY BOTTOM BAR */}
      <div className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-white border-t border-argile-200 p-3 px-4 shadow-warm-hover flex items-center justify-between gap-3">
        <div>
          <span className="text-[10px] text-charcoal-500 font-semibold block">Loyer Mensuel</span>
          <span className="font-display font-extrabold text-lg text-terracotta-600">
            {property.price.toLocaleString('fr-FR')} F
          </span>
        </div>
        <Button variant="primary" size="md" onClick={() => setIsVisitModalOpen(true)}>
          Réserver une visite
        </Button>
      </div>

      {/* MODAL: PROOF AUDIT DETAILS */}
      <Modal
        isOpen={isProofModalOpen}
        onClose={() => setIsProofModalOpen(false)}
        title="Audit & Preuves de Vérification Cadastrale"
      >
        <div className="space-y-4">
          <div className="p-4 rounded-xl bg-sahel-50 border border-sahel-200 space-y-2 text-xs">
            <h4 className="font-bold text-sahel-900 text-sm">Extrait du Registre Cadastral</h4>
            <p><strong>Numéro de Parcelle :</strong> {property.verificationDetails.cadastralNumber}</p>
            <p><strong>Propriétaire Légitime :</strong> {property.owner.name}</p>
            <p><strong>Date d'Audit :</strong> {property.verificationDetails.titleProofDate}</p>
          </div>
          <p className="text-xs text-charcoal-600">
            Cette annonce a fait l'objet d'un contrôle rigoureux. Les documents de propriété ainsi que l'identité de l'agent mandaté ont été validés par l'équipe juridique d'E-Courtier.
          </p>
        </div>
      </Modal>

      {/* MODAL: VISIT REQUEST FORM */}
      <Modal
        isOpen={isVisitModalOpen}
        onClose={() => setIsVisitModalOpen(false)}
        title="Planifier une Visite du Logement"
      >
        {!visitSubmitted ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              setVisitSubmitted(true);
            }}
            className="space-y-4"
          >
            <p className="text-xs text-charcoal-600">
              Choisissez le créneau qui vous convient. Un agent vous contactera pour confirmer le rendez-vous sur place à {property.neighborhood}.
            </p>
            
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-charcoal-800">Date souhaitée</label>
              <input type="date" required className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900" />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-charcoal-800">Votre Téléphone (WhatsApp)</label>
              <input type="tel" required defaultValue="+221 " className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900" />
            </div>

            <Button type="submit" variant="primary" size="md" className="w-full justify-center">
              Confirmer la demande de visite
            </Button>
          </form>
        ) : (
          <div className="text-center py-6 space-y-3">
            <div className="w-12 h-12 rounded-full bg-sahel-100 text-sahel-700 flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <h3 className="font-display font-bold text-base text-charcoal-900">Demande de visite transmise !</h3>
            <p className="text-xs text-charcoal-600">
              L'agent {property.owner.name} va valider votre rendez-vous sous 30 minutes via WhatsApp.
            </p>
            <Button variant="outline" size="sm" onClick={() => setIsVisitModalOpen(false)}>Fermer</Button>
          </div>
        )}
      </Modal>

      {/* YET ANOTHER REACT LIGHTBOX */}
      <Lightbox
        open={isLightboxOpen}
        close={() => setIsLightboxOpen(false)}
        index={selectedPhotoIndex}
        slides={property.images.map((src) => ({ src }))}
      />

    </div>
  );
};
