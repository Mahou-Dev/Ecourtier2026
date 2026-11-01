import React, { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { apiFetch } from '../../services/api';
import { AnnonceDTO } from '../../types';
import { toast } from 'sonner';

interface EditAnnonceModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  annonce: AnnonceDTO | null;
}

/**
 * Écran de modification d'une annonce (PUT /api/v1/annonces/{id}).
 * Permet notamment d'activer/désactiver la confirmation automatique des visites (F16).
 */
export const EditAnnonceModal: React.FC<EditAnnonceModalProps> = ({ isOpen, onClose, onSuccess, annonce }) => {
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [prix, setPrix] = useState<number>(0);
  const [meuble, setMeuble] = useState(false);
  const [animauxAcceptes, setAnimauxAcceptes] = useState(false);
  const [confirmationAutomatique, setConfirmationAutomatique] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (annonce) {
      setTitre(annonce.titre);
      setDescription(annonce.description);
      setPrix(annonce.prix);
      setMeuble(annonce.meuble);
      setAnimauxAcceptes(annonce.animauxAcceptes);
      setConfirmationAutomatique(annonce.confirmationAutomatique ?? true);
    }
  }, [annonce]);

  if (!annonce) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (titre.trim().length < 5) {
      toast.error('Le titre doit faire au moins 5 caractères');
      return;
    }
    if (!(prix > 0)) {
      toast.error('Le prix doit être strictement positif');
      return;
    }
    setIsSubmitting(true);
    try {
      await apiFetch<AnnonceDTO>(`/api/v1/annonces/${annonce.id}`, {
        method: 'PUT',
        body: JSON.stringify({ titre, description, prix, meuble, animauxAcceptes, confirmationAutomatique }),
      });
      toast.success('Annonce mise à jour');
      onSuccess();
      onClose();
    } catch (err: any) {
      toast.error(err.message || "Erreur lors de la modification de l'annonce");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Modifier l'annonce" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Titre de l'annonce" value={titre} onChange={(e) => setTitre(e.target.value)} />

        <div>
          <label htmlFor="edit-description" className="block text-xs font-bold text-sahel-800 mb-1">Description détaillée</label>
          <textarea
            id="edit-description"
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
          />
        </div>

        <Input
          label="Prix (FCFA)"
          type="number"
          value={prix}
          onChange={(e) => setPrix(Number(e.target.value))}
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
        <p className="text-[11px] text-sahel-600">
          Décochée : chaque demande de visite reste « Demandée » jusqu'à votre confirmation manuelle.
        </p>

        <div className="flex justify-end gap-3 pt-4 border-t border-sand-300">
          <Button variant="ghost" type="button" onClick={onClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button variant="primary" type="submit" isLoading={isSubmitting}>
            Enregistrer
          </Button>
        </div>
      </form>
    </Modal>
  );
};
