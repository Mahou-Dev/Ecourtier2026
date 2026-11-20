import React, { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { apiFetch } from '../../services/api';
import { AnnonceDTO } from '../../types';
import { toast } from 'sonner';
import { useReferences } from '../../hooks/useReferences';
import { LocationPickerMap } from '../map/LocationPickerMap';
import { isInsideSenegal } from '../../lib/geo';
import { getEquipementMeta, getProximiteMeta } from '../../lib/referenceMeta';
import { getErrorMessage, getErrorStatus } from '../../lib/errors';
import { Check } from 'lucide-react';
import { EditAnnonceMediaSection } from './EditAnnonceMediaSection';

interface EditAnnonceModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  annonce: AnnonceDTO | null;
}

export const EditAnnonceModal: React.FC<EditAnnonceModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  annonce,
}) => {
  if (!isOpen || !annonce) return null;

  return (
    <EditAnnonceForm
      key={`${annonce.id}-${annonce.version ?? 'nv'}`}
      isOpen={isOpen}
      onClose={onClose}
      onSuccess={onSuccess}
      annonce={annonce}
    />
  );
};

interface EditAnnonceFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  annonce: AnnonceDTO;
}

const EditAnnonceForm: React.FC<EditAnnonceFormProps> = ({
  isOpen,
  onClose,
  onSuccess,
  annonce,
}) => {
  const { data: references } = useReferences();

  const [currentVersion, setCurrentVersion] = useState<number | undefined>(annonce.version);
  const [titre, setTitre] = useState(annonce.titre || '');
  const [description, setDescription] = useState(annonce.description || '');
  const [prix, setPrix] = useState<number>(annonce.prix || 0);
  const [superficie, setSuperficie] = useState<number>(annonce.superficie || 0);
  const [nombrePieces, setNombrePieces] = useState<number>(annonce.nombrePieces || 1);
  const [etage, setEtage] = useState<number | undefined>(annonce.etage);
  const [quartier, setQuartier] = useState(annonce.quartier || '');
  const [ville, setVille] = useState(annonce.ville || 'Dakar');
  const [type, setType] = useState<'STUDIO' | 'APPARTEMENT' | 'VILLA' | 'CHAMBRE'>(annonce.type || 'APPARTEMENT');
  const [modeTransaction, setModeTransaction] = useState<'LOCATION' | 'VENTE'>(annonce.modeTransaction || 'LOCATION');
  const [latitude, setLatitude] = useState<number>(annonce.latitude || 14.7167);
  const [longitude, setLongitude] = useState<number>(annonce.longitude || -17.4677);
  const [meuble, setMeuble] = useState(annonce.meuble || false);
  const [animauxAcceptes, setAnimauxAcceptes] = useState(annonce.animauxAcceptes || false);
  const [confirmationAutomatique, setConfirmationAutomatique] = useState(annonce.confirmationAutomatique ?? true);
  const [lienVisite360, setLienVisite360] = useState(annonce.lienVisite360 || '');
  const [selectedEquipements, setSelectedEquipements] = useState<string[]>(annonce.equipements || []);
  const [selectedProximites, setSelectedProximites] = useState<string[]>(annonce.proximites || []);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isMediaBusy, setIsMediaBusy] = useState(false);

  const hasValidVersion = typeof currentVersion === 'number' && Number.isFinite(currentVersion);

  const toggleEquipement = (code: string) => {
    setSelectedEquipements((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]
    );
  };

  const toggleProximite = (code: string) => {
    setSelectedProximites((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!hasValidVersion) {
      toast.error("Version de l'annonce absente : veuillez recharger les données avant toute modification.");
      onSuccess();
      return;
    }
    if (titre.trim().length < 5) {
      toast.error('Le titre doit faire au moins 5 caractères');
      return;
    }
    if (!(prix > 0)) {
      toast.error('Le prix doit être strictement positif');
      return;
    }
    if (!(superficie > 0)) {
      toast.error('La superficie doit être positive');
      return;
    }
    if (!isInsideSenegal(latitude, longitude)) {
      toast.error('Les coordonnées géographiques doivent se situer au Sénégal');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: Record<string, unknown> = {
        titre,
        description,
        prix,
        superficie,
        nombrePieces,
        etage,
        quartier,
        ville,
        type,
        modeTransaction,
        latitude,
        longitude,
        meuble,
        animauxAcceptes,
        confirmationAutomatique,
        equipements: selectedEquipements,
        proximites: selectedProximites,
        version: currentVersion,
      };

      if (lienVisite360.trim()) {
        payload.lienVisite360 = lienVisite360.trim();
      }

      await apiFetch<AnnonceDTO>(`/api/v1/annonces/${annonce.id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      });

      toast.success('Annonce mise à jour avec succès');
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const status = getErrorStatus(err);
      if (status === 409) {
        toast.error(
          getErrorMessage(
            err,
            "Conflit de modification (409) : l'annonce a été modifiée par une autre session. Rechargement des dernières données..."
          )
        );
        onSuccess();
        onClose();
      } else if (status === 400) {
        toast.error(getErrorMessage(err, "Données invalides ou version de l'annonce manquante."));
      } else {
        toast.error(getErrorMessage(err, "Erreur lors de la modification de l'annonce"));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Modifier l'annonce" size="xl">
      <form onSubmit={handleSubmit} className="space-y-6">
        {!hasValidVersion && (
          <div
            role="alert"
            className="p-3 rounded-xl border border-rose-300 bg-rose-50 text-rose-900 text-xs font-medium"
          >
            La version de contrôle optimiste de cette annonce est absente. Fermez cette fenêtre et rechargez la liste avant d&apos;enregistrer.
          </div>
        )}
        
        {/* 1. Informations générales */}
        <div className="space-y-4">
          <h4 className="font-display text-sm font-bold text-sahel-900 border-b border-sand-300 pb-2">
            1. Informations générales
          </h4>
          <Input label="Titre de l'annonce" value={titre} onChange={(e) => setTitre(e.target.value)} />

          <div>
            <label htmlFor="edit-description" className="block text-xs font-bold text-sahel-800 mb-1">
              Description détaillée
            </label>
            <textarea
              id="edit-description"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="edit-type-bien" className="block text-xs font-bold text-sahel-800 mb-1">Type de bien</label>
              <select
                id="edit-type-bien"
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
                value={type}
                onChange={(e) =>
                  setType(e.target.value as 'STUDIO' | 'APPARTEMENT' | 'VILLA' | 'CHAMBRE')
                }
              >
                {(references?.typesBien || [
                  { valeur: 'APPARTEMENT', libelle: 'Appartement' },
                  { valeur: 'VILLA', libelle: 'Villa' },
                  { valeur: 'STUDIO', libelle: 'Studio' },
                  { valeur: 'CHAMBRE', libelle: 'Chambre' },
                ]).map((t) => (
                  <option key={t.valeur} value={t.valeur}>{t.libelle}</option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="edit-mode-transaction" className="block text-xs font-bold text-sahel-800 mb-1">Transaction</label>
              <select
                id="edit-mode-transaction"
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
                value={modeTransaction}
                onChange={(e) => setModeTransaction(e.target.value as 'LOCATION' | 'VENTE')}
              >
                {(references?.modesTransaction || [
                  { valeur: 'LOCATION', libelle: 'Location' },
                  { valeur: 'VENTE', libelle: 'Vente' },
                ]).map((m) => (
                  <option key={m.valeur} value={m.valeur}>{m.libelle}</option>
                ))}
              </select>
            </div>

            <Input
              label="Prix (FCFA)"
              type="number"
              value={prix}
              onChange={(e) => setPrix(Number(e.target.value))}
            />
          </div>

          <div className="grid grid-cols-1 xs:grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 sm:gap-4">
            <Input
              label="Superficie (m²)"
              type="number"
              value={superficie}
              onChange={(e) => setSuperficie(Number(e.target.value))}
            />
            <Input
              label="Pièces"
              type="number"
              value={nombrePieces}
              onChange={(e) => setNombrePieces(Number(e.target.value))}
            />
            <Input
              label="Étage"
              type="number"
              value={etage !== undefined ? etage : ''}
              onChange={(e) => setEtage(e.target.value ? Number(e.target.value) : undefined)}
            />
            <Input
              label="Quartier"
              value={quartier}
              onChange={(e) => setQuartier(e.target.value)}
            />
            <Input
              label="Ville"
              value={ville}
              onChange={(e) => setVille(e.target.value)}
            />
          </div>

          <Input
            label="Lien Visite Virtuelle 360° (Optionnel — URL Matterport, YouTube 360, etc.)"
            placeholder="https://my.matterport.com/show/?m=..."
            value={lienVisite360}
            onChange={(e) => setLienVisite360(e.target.value)}
          />

          <div className="flex flex-wrap items-center gap-6 pt-2">
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" checked={meuble} onChange={(e) => setMeuble(e.target.checked)} className="rounded text-terracotta-500" />
              <span>Meublé</span>
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" checked={animauxAcceptes} onChange={(e) => setAnimauxAcceptes(e.target.checked)} className="rounded text-terracotta-500" />
              <span>Animaux acceptés</span>
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input
                type="checkbox"
                data-testid="confirmation-automatique-edit"
                checked={confirmationAutomatique}
                onChange={(e) => setConfirmationAutomatique(e.target.checked)}
                className="rounded text-terracotta-500"
              />
              <span>Confirmation automatique des visites</span>
            </label>
          </div>
        </div>

        {/* 2. Photos & Documents de vérification (ANO-08) */}
        <div className="space-y-4 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900 border-b border-sand-300 pb-2">
            2. Photos et Documents de vérification
          </h4>
          <EditAnnonceMediaSection
            annonce={annonce}
            documentTypes={references?.typesDocument}
            onMediaChanged={onSuccess}
            onVersionSync={(newVersion) => setCurrentVersion(newVersion)}
            onBusyChange={(busy) => setIsMediaBusy(busy)}
          />
        </div>

        {/* 3. Localisation Interactive Leaflet (F4) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            3. Localisation géographique (Sénégal)
          </h4>
          <LocationPickerMap
            latitude={latitude}
            longitude={longitude}
            onChange={(lat, lng) => {
              setLatitude(lat);
              setLongitude(lng);
            }}
          />
        </div>

        {/* 4. Équipements (F5) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            4. Équipements disponibles
          </h4>
          <div className="grid grid-cols-1 xs:grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
            {(references?.equipements || [
              { valeur: 'WIFI', libelle: 'Wi-Fi' },
              { valeur: 'PARKING', libelle: 'Parking' },
              { valeur: 'CLIMATISATION', libelle: 'Climatisation' },
              { valeur: 'PISCINE', libelle: 'Piscine' },
              { valeur: 'GARDIEN', libelle: 'Gardien / Sécurité' },
              { valeur: 'GROUPE_ELECTROGENE', libelle: 'Groupe électrogène' },
              { valeur: 'RESERVE_EAU', libelle: "Réserve d'eau" },
            ]).map((eq) => {
              const meta = getEquipementMeta(eq.valeur);
              const Icon = meta.icon;
              const isChecked = selectedEquipements.includes(eq.valeur);
              return (
                <button
                  key={eq.valeur}
                  type="button"
                  onClick={() => toggleEquipement(eq.valeur)}
                  className={`flex items-center gap-2 px-3 py-2 rounded-xl border text-xs font-medium text-left transition-all ${
                    isChecked
                      ? 'bg-terracotta-50 border-terracotta-500 text-terracotta-800 ring-1 ring-terracotta-400'
                      : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                  }`}
                >
                  <Icon className={`w-4 h-4 shrink-0 ${isChecked ? 'text-terracotta-600' : 'text-charcoal-500'}`} />
                  <span className="truncate">{eq.libelle}</span>
                  {isChecked && <Check className="w-3.5 h-3.5 text-terracotta-600 ml-auto shrink-0" />}
                </button>
              );
            })}
          </div>
        </div>

        {/* 5. Proximités (F5) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            5. Proximités & Commodités
          </h4>
          <div className="grid grid-cols-1 xs:grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
            {(references?.proximites || [
              { valeur: 'ECOLE', libelle: 'École' },
              { valeur: 'MARCHE', libelle: 'Marché' },
              { valeur: 'HOPITAL', libelle: 'Hôpital / Santé' },
              { valeur: 'TRANSPORT', libelle: 'Transports (BRT/Bus)' },
              { valeur: 'PLAGE', libelle: 'Plage / Mer' },
              { valeur: 'COMMERCE', libelle: 'Commerces' },
              { valeur: 'UNIVERSITE', libelle: 'Université' },
            ]).map((prox) => {
              const meta = getProximiteMeta(prox.valeur);
              const Icon = meta.icon;
              const isChecked = selectedProximites.includes(prox.valeur);
              return (
                <button
                  key={prox.valeur}
                  type="button"
                  onClick={() => toggleProximite(prox.valeur)}
                  className={`flex items-center gap-2 px-3 py-2 rounded-xl border text-xs font-medium text-left transition-all ${
                    isChecked
                      ? 'bg-clay-50 border-sahel-600 text-sahel-900 ring-1 ring-sahel-500'
                      : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                  }`}
                >
                  <Icon className={`w-4 h-4 shrink-0 ${isChecked ? 'text-sahel-700' : 'text-charcoal-500'}`} />
                  <span className="truncate">{prox.libelle}</span>
                  {isChecked && <Check className="w-3.5 h-3.5 text-sahel-700 ml-auto shrink-0" />}
                </button>
              );
            })}
          </div>
        </div>

        <div className="flex flex-col-reverse sm:flex-row justify-end gap-2 sm:gap-3 pt-4 border-t border-sand-300">
          <Button variant="ghost" type="button" onClick={onClose} disabled={isSubmitting || isMediaBusy} className="w-full sm:w-auto justify-center">
            Annuler
          </Button>
          <Button variant="primary" type="submit" isLoading={isSubmitting} disabled={isMediaBusy} className="w-full sm:w-auto justify-center">
            Enregistrer les modifications
          </Button>
        </div>
      </form>
    </Modal>
  );
};
