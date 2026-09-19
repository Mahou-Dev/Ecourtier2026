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
import { Upload, X, FileText, Image as ImageIcon, Plus } from 'lucide-react';

const annonceSchema = z.object({
  titre: z.string().min(5, 'Le titre doit faire au moins 5 caractères'),
  description: z.string().min(15, 'La description doit faire au moins 15 caractères'),
  type: z.enum(['STUDIO', 'APPARTEMENT', 'VILLA']),
  modeTransaction: z.enum(['LOCATION', 'VENTE']),
  prix: z.coerce.number().positive('Le prix doit être strictement positif'),
  superficie: z.coerce.number().positive('La superficie doit être positive'),
  nombrePieces: z.coerce.number().int().min(1, 'Au moins 1 pièce'),
  quartier: z.string().min(2, 'Le quartier est obligatoire'),
  ville: z.string().min(2, 'La ville est obligatoire'),
  latitude: z.coerce.number().default(14.7167),
  longitude: z.coerce.number().default(-17.4677),
  meuble: z.boolean().default(false),
  animauxAcceptes: z.boolean().default(false),
  lienVisite360: z.string().optional(),
});

type AnnonceFormData = z.infer<typeof annonceSchema>;

interface CreateAnnonceModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const CreateAnnonceModal: React.FC<CreateAnnonceModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [selectedPhotos, setSelectedPhotos] = useState<File[]>([]);
  const [docVerificationFile, setDocVerificationFile] = useState<File | null>(null);
  const [docType, setDocType] = useState<'TITRE_FONCIER' | 'BAIL_PRECEDENT' | 'PIECE_IDENTITE'>('TITRE_FONCIER');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
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
    },
  });

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

  const handleDocSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setDocVerificationFile(e.target.files[0]);
    }
  };

  const onSubmit = async (data: AnnonceFormData) => {
    setIsSubmitting(true);
    try {
      // Step 1: Create listing
      const createdAnnonce = await apiFetch<AnnonceDTO>('/api/v1/annonces', {
        method: 'POST',
        body: JSON.stringify(data),
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

      // Step 3: Upload verification document if provided
      if (docVerificationFile) {
        const docFormData = new FormData();
        docFormData.append('file', docVerificationFile);
        await apiFetch(`/api/v1/annonces/${createdAnnonce.id}/documents?typeDocument=${docType}`, {
          method: 'POST',
          body: docFormData,
        });
      }

      toast.success('Annonce créée avec succès !');
      reset();
      setSelectedPhotos([]);
      setDocVerificationFile(null);
      onSuccess();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Erreur lors de la création de l\'annonce');
    } finally {
      setIsSubmitting(false);
    }
  };

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
                <option value="STUDIO">Studio</option>
                <option value="APPARTEMENT">Appartement</option>
                <option value="VILLA">Villa</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Transaction</label>
              <select
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
                {...register('modeTransaction')}
              >
                <option value="LOCATION">Location</option>
                <option value="VENTE">Vente</option>
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

          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
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

          <div className="flex items-center gap-6 pt-2">
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" className="rounded text-terracotta-500 focus:ring-terracotta-500" {...register('meuble')} />
              <span>Meublé</span>
            </label>
            <label className="flex items-center gap-2 text-xs font-semibold text-sahel-800 cursor-pointer">
              <input type="checkbox" className="rounded text-terracotta-500 focus:ring-terracotta-500" {...register('animauxAcceptes')} />
              <span>Animaux acceptés</span>
            </label>
          </div>
        </div>

        {/* Photo Upload Section */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900 flex items-center gap-2">
            <ImageIcon className="w-4 h-4 text-terracotta-500" />
            <span>2. Photos du bien (max 10)</span>
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
                <input type="file" accept="image/*" multiple onChange={handlePhotoSelect} className="hidden" />
              </label>
            )}
          </div>
        </div>

        {/* Verification Document Upload Section */}
        <div className="space-y-3 pt-4 border-t border-sand-300">
          <h4 className="font-display text-sm font-bold text-sahel-900 flex items-center gap-2">
            <FileText className="w-4 h-4 text-terracotta-500" />
            <span>3. Document de vérification cadastrale (Optionnel mais recommandé)</span>
          </h4>
          <p className="text-xs text-sahel-600">
            Soumettez un titre foncier ou bail pour demander le contrôle et l'attribution du badge vérifié par nos équipes.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-end">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Type de document</label>
              <select
                value={docType}
                onChange={(e) => setDocType(e.target.value as any)}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
              >
                <option value="TITRE_FONCIER">Titre Foncier / Attestation de propriété</option>
                <option value="BAIL_PRECEDENT">Bail enregistré / Contrat</option>
                <option value="PIECE_IDENTITE">Pièce d'identité propriétaire</option>
              </select>
            </div>

            <div>
              <label className="border border-sand-300 hover:border-terracotta-500 rounded-xl px-4 py-2.5 flex items-center justify-between cursor-pointer text-xs font-semibold text-sahel-800 bg-sand-50 transition-colors">
                <span className="truncate">{docVerificationFile ? docVerificationFile.name : 'Choisir un fichier (PDF/Image)'}</span>
                <Upload className="w-4 h-4 text-terracotta-500 shrink-0 ml-2" />
                <input type="file" accept="image/*,.pdf" onChange={handleDocSelect} className="hidden" />
              </label>
            </div>
          </div>
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
