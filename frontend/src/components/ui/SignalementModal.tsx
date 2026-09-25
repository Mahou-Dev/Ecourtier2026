import React, { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';
import { Select } from './Select';
import { apiFetch } from '../../services/api';
import { MotifSignalement } from '../../types';
import { toast } from 'sonner';

interface SignalementModalProps {
  annonceId: string;
  annonceTitre: string;
  isOpen: boolean;
  onClose: () => void;
}

export const SignalementModal: React.FC<SignalementModalProps> = ({
  annonceId,
  annonceTitre,
  isOpen,
  onClose,
}) => {
  const [motif, setMotif] = useState<MotifSignalement>('FAUSSE_ANNONCE');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await apiFetch(`/api/v1/annonces/${annonceId}/signalements`, {
        method: 'POST',
        body: JSON.stringify({ motif, description }),
      });
      toast.success('Signalement transmis aux équipes de modération E-Courtier');
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Erreur lors de la transmission du signalement');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Signaler cette annonce" size="sm">
      <div className="space-y-4">
        <div className="flex items-center gap-3 p-3 rounded-xl bg-rose-50 border border-rose-200">
          <div className="w-9 h-9 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center shrink-0">
            <AlertTriangle className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-sahel-900 truncate">{annonceTitre}</p>
            <p className="text-[11px] text-sahel-600">Formulaire de modération</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Select
            label="Motif du signalement"
            value={motif}
            onChange={(val) => setMotif(val as MotifSignalement)}
            options={[
              { value: 'FAUSSE_ANNONCE', label: 'Fausse annonce ou photos usurpées' },
              { value: 'PRIX_TROMPEUR', label: 'Prix trompeur ou frais occultes' },
              { value: 'PROPRIETAIRE_INJOIGNABLE', label: 'Propriétaire / Agent injoignable' },
              { value: 'ARNAQUE_SUSPECTEE', label: 'Tentative d\'arnaque ou virement suspect' },
              { value: 'AUTRE', label: 'Autre raison' },
            ]}
          />

          <div>
            <label className="block text-xs font-semibold text-charcoal-800 mb-1">
              Détails complémentaires (optionnel)
            </label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Expliquez brièvement les faits ou anomalies constatées..."
              className="w-full text-xs p-3 rounded-xl border border-argile-300 focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 resize-none"
            />
          </div>

          <div className="flex items-center gap-3 pt-2">
            <Button variant="outline" size="sm" type="button" className="flex-1" onClick={onClose}>
              Annuler
            </Button>
            <Button variant="primary" size="sm" type="submit" className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-none" isLoading={isSubmitting}>
              Envoyer le signalement
            </Button>
          </div>
        </form>
      </div>
    </Modal>
  );
};
