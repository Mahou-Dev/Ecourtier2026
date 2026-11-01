import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { apiFetch } from '../../services/api';
import { AnnonceDTO } from '../../types';
import { toast } from 'sonner';
import { Upload, X, FileText, Image as ImageIcon, Plus, Check } from 'lucide-react';
import { useReferences } from '../../hooks/useReferences';
import { LocationPickerMap, isInsideSenegal } from '../map/LocationPickerMap';
import { getEquipementMeta, getProximiteMeta } from '../ui/ReferenceIcons';

const annonceSchema = z.object({
  titre: z.string().min(5, 'Le titre doit faire au moins 5 caractères'),
  description: z.string().min(15, 'La description doit faire au moins 15 caractères'),
  type: z.enum(['STUDIO', 'APPARTEMENT', 'VILLA']),
  modeTransaction: z.enum(['LOCATION', 'VENTE']),
  prix: z.coerce.number().positive('Le prix doit être strictement positif'),
  superficie: z.coerce.number().positive('La superficie doit être positive'),
  nombrePieces: z.coerce.number().int().min(1, 'Au moins 1 pièce'),
  etage: z.coerce.number().int().optional(),
  quartier: z.string().min(2, 'Le quartier est obligatoire'),
  ville: z.string().min(2, 'La ville est obligatoire'),
  latitude: z.coerce.number(),
  longitude: z.coerce.number(),
  meuble: z.boolean().default(false),
  animauxAcceptes: z.boolean().default(false),
  confirmationAutomatique: z.boolean().default(true),
  lienVisite360: z.string().optional(),
});

type AnnonceFormData = z.infer<typeof annonceSchema>;

interface CreateAnnonceModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface QueuedDocFile {
  type: 'TITRE_FONCIER' | 'BAIL_PRECEDENT' | 'PIECE_IDENTITE';
  file: File;
}

export const CreateAnnonceModal: React.FC<CreateAnnonceModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { data: references } = useReferences();
  const [selectedPhotos, setSelectedPhotos] = useState<File[]>([]);
  const [queuedDocs, setQueuedDocs] = useState<QueuedDocFile[]>([]);
  const [selectedDocType, setSelectedDocType] = useState<'TITRE_FONCIER' | 'BAIL_PRECEDENT' | 'PIECE_IDENTITE'>('TITRE_FONCIER');
  const [selectedEquipements, setSelectedEquipements] = useState<string[]>([]);
  const [selectedProximites, setSelectedProximites] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<AnnonceFormData>({
    resolver: zodResolver(annonceSchema),
    defaultValues: {
      type: 'APPARTEMENT',
      modeTransaction: 'LOCATION',
      ville: 'Dakar',
      quartier: 'Almadies',
      latitude: 14.745,
      longitude: -17.518,
      prix: 250000,
      superficie: 85,
      nombrePieces: 3,
      meuble: true,
      animauxAcceptes: false,
      confirmationAutomatique: true,
    },
  });

  const currentLat = watch('latitude') ?? 14.745;
  const currentLng = watch('longitude') ?? -17.518;

  const handlePhotoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const filesArr = Array.from(e.target.files);
      if (selectedPhotos.length + filesArr.length > 10) {
        toast.error('Vous ne pouvez pas ajouter plus de 10 photos');
        return;
      }
      setSelectedPhotos((prev) => [...prev, ...filesArr]);
    }
  };

  const removePhoto = (index: number) => {
    setSelectedPhotos((prev) => prev.filter((_, i) => i !== index));
  };

  const handleDocAdd = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const filesArr = Array.from(e.target.files);
      const newDocs: QueuedDocFile[] = filesArr.map((f) => ({
        type: selectedDocType,
        file: f,
      }));
      setQueuedDocs((prev) => [...prev, ...newDocs]);
      toast.success(`${filesArr.length} document(s) ajouté(s) à la liste`);
      e.target.value = '';
    }
  };

  const removeQueuedDoc = (index: number) => {
    setQueuedDocs((prev) => prev.filter((_, i) => i !== index));
  };

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

  const onSubmit = async (data: AnnonceFormData) => {
    if (!isInsideSenegal(data.latitude, data.longitude)) {
      toast.error('Les coordonnées géographiques doivent se situer au Sénégal');
      return;
    }

    setIsSubmitting(true);
    try {
      // Step 1: Create listing with equipements and proximites
      const payload = {
        ...data,
        equipements: selectedEquipements,
        proximites: selectedProximites,
      };

      const createdAnnonce = await apiFetch<AnnonceDTO>('/api/v1/annonces', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      // Step 2: Upload photos if any
      if (selectedPhotos.length > 0) {
        const formData = new FormData();
        selectedPhotos.forEach((file) => formData.append('files', file));
        await apiFetch(`/api/v1/annonces/${createdAnnonce.id}/photos`, {
          method: 'POST',
          body: formData,
        });
      }

      // Step 3: Upload all queued verification documents
      if (queuedDocs.length > 0) {
        for (const doc of queuedDocs) {
          const docFormData = new FormData();
          docFormData.append('file', doc.file);
          await apiFetch(`/api/v1/annonces/${createdAnnonce.id}/documents?typeDocument=${doc.type}`, {
            method: 'POST',
            body: docFormData,
          });
        }
      }

      toast.success('Annonce créée avec succès !');
      reset();
      setSelectedPhotos([]);
      setQueuedDocs([]);
      setSelectedEquipements([]);
      setSelectedProximites([]);
      onSuccess();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Erreur lors de la création de l\'annonce');
    } finally {
      setIsSubmitting(false);
    }
  };

  const docTypesList = references?.typesDocument || [
    { valeur: 'TITRE_FONCIER', libelle: 'Titre Foncier / Attestation de propriété' },
    { valeur: 'BAIL_PRECEDENT', libelle: 'Bail précédent / Facture d\'électricité' },
    { valeur: 'PIECE_IDENTITE', libelle: 'Photo du propriétaire avec sa pièce d\'identité' },
  ];

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Publier une nouvelle annonce" size="lg">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        
        {/* Main Details */}
        <div className="space-y-4">
          <h4 className="font-display text-sm font-bold text-sahel-900 border-b border-sand-300 pb-2">
            1. Informations générales
          </h4>
          
          <Input
            label="Titre de l'annonce"
            placeholder="Ex: Bel appartement F3 de standing aux Almadies"
            error={errors.titre?.message}
            {...register('titre')}
          />

          <div>
            <label className="block text-xs font-bold text-sahel-800 mb-1">Description détaillée</label>
            <textarea
              rows={3}
              className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 focus:border-terracotta-500 outline-none transition-all"
              placeholder="Décrivez les atouts de votre bien (sécurité, climatisation, réserve d'eau...)"
              {...register('description')}
            />
            {errors.description && <p className="text-xs text-rose-600 mt-1">{errors.description.message}</p>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Type de bien</label>
              <select
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
                {...register('type')}
              >
                {(references?.typesBien || [
                  { valeur: 'APPARTEMENT', libelle: 'Appartement' },
                  { valeur: 'VILLA', libelle: 'Villa' },
                  { valeur: 'STUDIO', libelle: 'Studio' },
                ]).map((t) => (
                  <option key={t.valeur} value={t.valeur}>{t.libelle}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Transaction</label>
              <select
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
                {...register('modeTransaction')}
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
              placeholder="250000"
              error={errors.prix?.message}
              {...register('prix')}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
            <Input
              label="Superficie (m²)"
              type="number"
              error={errors.superficie?.message}
              {...register('superficie')}
            />
            <Input
              label="Pièces"
              type="number"
              error={errors.nombrePieces?.message}
              {...register('nombrePieces')}
            />
            <Input
              label="Étage (ex: 2, 0 pour RDC)"
              type="number"
              error={errors.etage?.message}
              {...register('etage')}
            />
            <Input
              label="Quartier"
              placeholder="Almadies, Mermoz..."
              error={errors.quartier?.message}
              {...register('quartier')}
            />
            <Input
              label="Ville"
              error={errors.ville?.message}
              {...register('ville')}
            />
          </div>

          <Input
            label="Lien Visite Virtuelle 360° (Optionnel - URL Matterport, YouTube 360, etc.)"
            placeholder="https://my.matterport.com/show/..."
            error={errors.lienVisite360?.message}
            {...register('lienVisite360')}
          />

          <div className="flex flex-wrap items-center gap-6 pt-2">
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" className="rounded text-terracotta-500 focus:ring-terracotta-500" {...register('meuble')} />
              <span>Meublé</span>
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" className="rounded text-terracotta-500 focus:ring-terracotta-500" {...register('animauxAcceptes')} />
              <span>Animaux acceptés</span>
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input
                type="checkbox"
                data-testid="confirmation-automatique-create"
                className="rounded text-terracotta-500 focus:ring-terracotta-500"
                {...register('confirmationAutomatique')}
              />
              <span>Confirmation automatique des visites</span>
            </label>
          </div>
          <p className="text-[11px] text-sahel-600">
            Si cochée, une demande de visite est confirmée immédiatement lorsqu'aucune autre visite confirmée n'existe à ±60 min.
          </p>
        </div>

        {/* Localisation Interactive Leaflet (F4) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            2. Localisation géographique (Sénégal)
          </h4>
          <LocationPickerMap
            latitude={currentLat}
            longitude={currentLng}
            onChange={(lat, lng) => {
              setValue('latitude', lat, { shouldValidate: true });
              setValue('longitude', lng, { shouldValidate: true });
            }}
          />
        </div>

        {/* Équipements (F5) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            3. Équipements disponibles
          </h4>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
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
                  {isChecked && <Check className="w-3.5 h-3.5 text-terracotta-600 ml-auto" />}
                </button>
              );
            })}
          </div>
        </div>

        {/* Proximités (F5) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900">
            4. Proximités & Commodités
          </h4>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
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
                  {isChecked && <Check className="w-3.5 h-3.5 text-sahel-700 ml-auto" />}
                </button>
              );
            })}
          </div>
        </div>

        {/* Photo Upload Section */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900 flex items-center gap-2">
            <ImageIcon className="w-4 h-4 text-terracotta-500" />
            <span>5. Photos du bien (max 10)</span>
          </h4>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {selectedPhotos.map((file, idx) => (
              <div key={idx} className="relative group rounded-xl overflow-hidden border border-sand-300 h-24 bg-sand-200">
                <img src={URL.createObjectURL(file)} alt="preview" className="w-full h-full object-cover" />
                <button
                  type="button"
                  onClick={() => removePhoto(idx)}
                  className="absolute top-1 right-1 p-1 bg-charcoal-900/70 text-white rounded-full hover:bg-rose-600 transition-colors"
                >
                  <X className="w-3 h-3" />
                </button>
              </div>
            ))}

            {selectedPhotos.length < 10 && (
              <label className="border-2 border-dashed border-sand-300 hover:border-terracotta-500 rounded-xl h-24 flex flex-col items-center justify-center cursor-pointer transition-colors text-sahel-600 hover:text-terracotta-600">
                <Plus className="w-6 h-6 mb-1" />
                <span className="text-[11px] font-semibold">Ajouter photo</span>
                <input type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" multiple onChange={handlePhotoSelect} className="hidden" />
              </label>
            )}
          </div>
          <p className="text-[11px] text-sahel-600">
            Formats acceptés : Images uniquement (JPG, JPEG, PNG, WEBP — Max 5 Mo).
          </p>
        </div>

        {/* Verification Document Upload Section (F14) */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900 flex items-center gap-2">
            <FileText className="w-4 h-4 text-terracotta-500" />
            <span>6. Pièces justificatives de vérification (F14)</span>
          </h4>
          <p className="text-xs text-sahel-600">
            Soumettez vos preuves pour obtenir le badge <strong>Annonce vérifiée</strong>. Vous pouvez ajouter plusieurs fichiers pour chaque type (titre foncier, bail précédent, photo avec pièce d'identité).
          </p>

          <div className="p-3 bg-amber-50 rounded-xl border border-amber-200 text-[11px] text-amber-800">
            <strong>Formats acceptés par le serveur :</strong> Images uniquement (JPG, JPEG, PNG, WEBP — Max 5 Mo par fichier).
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-end">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Type de document</label>
              <select
                value={selectedDocType}
                onChange={(e) => setSelectedDocType(e.target.value as any)}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
              >
                {docTypesList.map((dt) => (
                  <option key={dt.valeur} value={dt.valeur}>{dt.libelle}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="border border-sand-300 hover:border-terracotta-500 rounded-xl px-4 py-2.5 flex items-center justify-between cursor-pointer text-xs font-semibold text-sahel-800 bg-sand-50 transition-colors">
                <span className="truncate">Ajouter fichier(s)</span>
                <Upload className="w-4 h-4 text-terracotta-500 shrink-0 ml-2" />
                <input
                  type="file"
                  accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                  multiple
                  onChange={handleDocAdd}
                  className="hidden"
                />
              </label>
            </div>
          </div>

          {queuedDocs.length > 0 && (
            <div className="space-y-2 pt-2">
              <p className="text-xs font-semibold text-sahel-800">Fichiers prêts à être soumis ({queuedDocs.length}) :</p>
              <div className="space-y-1.5 max-h-40 overflow-y-auto">
                {queuedDocs.map((item, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between p-2 rounded-lg bg-sand-100 border border-sand-300 text-xs text-charcoal-800"
                  >
                    <div className="flex items-center gap-2 truncate">
                      <FileText className="w-3.5 h-3.5 text-terracotta-600 shrink-0" />
                      <span className="font-semibold text-terracotta-800">[{item.type}]</span>
                      <span className="truncate">{item.file.name}</span>
                      <span className="text-[10px] text-charcoal-500">({(item.file.size / 1024).toFixed(0)} Ko)</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeQueuedDoc(idx)}
                      className="p-1 hover:text-rose-600 transition-colors"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-6 border-t border-sand-300">
          <Button variant="ghost" type="button" onClick={onClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button variant="primary" type="submit" isLoading={isSubmitting}>
            Publier l'annonce
          </Button>
        </div>
      </form>
    </Modal>
  );
};
