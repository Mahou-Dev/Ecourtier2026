import React from 'react';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

interface ConfirmActionModalProps {
  isOpen: boolean;
  title: string;
  description: React.ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  /** Message d'erreur renvoyé par une tentative précédente (la modale reste ouverte). */
  errorMessage?: string | null;
  isLoading?: boolean;
  destructive?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Confirmation explicite avant une action irréversible : état de traitement,
 * erreur affichée dans la modale, fermeture bloquée pendant l'appel réseau.
 */
export const ConfirmActionModal: React.FC<ConfirmActionModalProps> = ({
  isOpen,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Retour',
  errorMessage,
  isLoading = false,
  destructive = true,
  onConfirm,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={() => {
      if (!isLoading) onClose();
    }}
    title={title}
    size="sm"
  >
    <div className="space-y-4">
      <div className="text-sm text-charcoal-700 space-y-3">{description}</div>
      {errorMessage && (
        <p role="alert" className="text-xs font-medium text-rose-800 bg-rose-50 border border-rose-200 rounded-xl p-3">
          {errorMessage}
        </p>
      )}
      <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 pt-2">
        <Button
          variant="ghost"
          type="button"
          onClick={onClose}
          disabled={isLoading}
          className="min-h-[44px] w-full sm:w-auto"
        >
          {cancelLabel}
        </Button>
        <Button
          variant="primary"
          type="button"
          onClick={onConfirm}
          isLoading={isLoading}
          className={`min-h-[44px] w-full sm:w-auto ${destructive ? 'bg-rose-600 hover:bg-rose-700 text-white focus:ring-rose-400' : ''}`}
        >
          {confirmLabel}
        </Button>
      </div>
    </div>
  </Modal>
);
