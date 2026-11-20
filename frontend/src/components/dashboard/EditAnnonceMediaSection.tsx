import React, { useRef, useState } from 'react';
import { toast } from 'sonner';
import { ArrowDown, ArrowUp, FileText, ImagePlus, Trash2, Upload } from 'lucide-react';
import { apiFetch, fetchProtectedBlob } from '../../services/api';
import { getErrorMessage } from '../../lib/errors';
import type { AnnonceDTO, DocumentVerificationDTO, PhotoAnnonceDTO, ReferenceItem } from '../../types';
import { PropertyVisual } from '../property/PropertyVisual';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';

/** Limites annoncées par le backend (AnnonceService / AnnonceController). */
export const MAX_PHOTOS_PER_ANNONCE = 10;
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
const PHOTO_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const DOCUMENT_MIME_TYPES = [...PHOTO_MIME_TYPES, 'application/pdf'];

type DocumentType = DocumentVerificationDTO['typeDocument'];

const DOCUMENT_STATUS: Record<DocumentVerificationDTO['statut'], { label: string; variant: 'warning' | 'success' | 'rose' }> = {
  EN_ATTENTE: { label: 'En attente de contrôle', variant: 'warning' },
  VALIDE: { label: 'Validé', variant: 'success' },
  REJETE: { label: 'Rejeté', variant: 'rose' },
};

const DEFAULT_DOCUMENT_TYPES: ReferenceItem[] = [
  { valeur: 'TITRE_FONCIER', libelle: 'Titre foncier / attestation de propriété' },
  { valeur: 'BAIL_PRECEDENT', libelle: "Bail précédent / facture d'électricité" },
  { valeur: 'PIECE_IDENTITE', libelle: "Photo du propriétaire avec sa pièce d'identité" },
];

function sortPhotos(photos: PhotoAnnonceDTO[]): PhotoAnnonceDTO[] {
  return [...photos].sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0));
}

interface UploadOutcome {
  succeeded: number;
  failures: { name: string; message: string }[];
}

interface EditAnnonceMediaSectionProps {
  annonce: AnnonceDTO;
  documentTypes?: ReferenceItem[];
  /** Appelé après chaque opération média réussie (le parent rafraîchira la liste à la fermeture). */
  onMediaChanged: () => void;
  /** Appelé pour synchroniser la version optimiste après une opération média. */
  onVersionSync: (version: number) => void;
  onBusyChange: (busy: boolean) => void;
}

/**
 * Gestion des médias d'une annonce existante : photos (ajout, suppression, ordre) et documents
 * de vérification (ajout, consultation). Chaque opération appelle un endpoint existant et ne
 * confirme le succès qu'après la réponse du serveur ; les envois multiples sont faits fichier
 * par fichier afin de rendre compte précisément des échecs partiels.
 */
export const EditAnnonceMediaSection: React.FC<EditAnnonceMediaSectionProps> = ({
  annonce,
  documentTypes,
  onMediaChanged,
  onVersionSync,
  onBusyChange,
}) => {
  const [photos, setPhotos] = useState<PhotoAnnonceDTO[]>(() => sortPhotos(annonce.photos ?? []));
  const [documents, setDocuments] = useState<DocumentVerificationDTO[]>(annonce.documentsVerification ?? []);
  const [busy, setBusy] = useState<null | 'photos' | 'order' | 'delete' | 'documents'>(null);
  const [photoToDelete, setPhotoToDelete] = useState<string | null>(null);
  const [documentType, setDocumentType] = useState<DocumentType>('TITRE_FONCIER');
  const [report, setReport] = useState<{ kind: 'photos' | 'documents'; outcome: UploadOutcome } | null>(null);
  const photoInputRef = useRef<HTMLInputElement>(null);
  const documentInputRef = useRef<HTMLInputElement>(null);

  const types = documentTypes && documentTypes.length > 0 ? documentTypes : DEFAULT_DOCUMENT_TYPES;
  const remainingSlots = Math.max(0, MAX_PHOTOS_PER_ANNONCE - photos.length);

  const withBusy = async (kind: NonNullable<typeof busy>, task: () => Promise<void>) => {
    setBusy(kind);
    onBusyChange(true);
    try {
      await task();
    } finally {
      setBusy(null);
      onBusyChange(false);
    }
  };

  /** Relit l'annonce pour récupérer la version et la liste de médias faisant foi. */
  const syncFromServer = async (): Promise<AnnonceDTO | null> => {
    try {
      const fresh = await apiFetch<AnnonceDTO>(`/api/v1/annonces/${annonce.id}`);
      if (typeof fresh?.version === 'number') onVersionSync(fresh.version);
      return fresh;
    } catch {
      return null;
    }
  };

  const handlePhotoSelection = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (files.length === 0) return;

    const accepted: File[] = [];
    const rejected: { name: string; message: string }[] = [];
    files.forEach((file) => {
      if (!PHOTO_MIME_TYPES.includes(file.type)) {
        rejected.push({ name: file.name, message: 'Format non pris en charge (JPG, PNG ou WEBP).' });
      } else if (file.size > MAX_PHOTO_BYTES) {
        rejected.push({ name: file.name, message: 'Fichier supérieur à 5 Mo.' });
      } else if (accepted.length >= remainingSlots) {
        rejected.push({ name: file.name, message: `Limite de ${MAX_PHOTOS_PER_ANNONCE} photos atteinte.` });
      } else {
        accepted.push(file);
      }
    });

    const outcome: UploadOutcome = { succeeded: 0, failures: [...rejected] };
    if (accepted.length > 0) {
      await withBusy('photos', async () => {
        const added: PhotoAnnonceDTO[] = [];
        for (const file of accepted) {
          try {
            const body = new FormData();
            body.append('files', file);
            const created = await apiFetch<PhotoAnnonceDTO[]>(`/api/v1/annonces/${annonce.id}/photos`, {
              method: 'POST',
              body,
            });
            if (Array.isArray(created)) added.push(...created);
            outcome.succeeded += 1;
          } catch (err: unknown) {
            outcome.failures.push({ name: file.name, message: getErrorMessage(err, "Échec de l'envoi") });
          }
        }
        if (outcome.succeeded > 0) {
          const fresh = await syncFromServer();
          setPhotos(sortPhotos(fresh?.photos ?? [...photos, ...added]));
          onMediaChanged();
        }
      });
    }

    setReport({ kind: 'photos', outcome });
    if (outcome.succeeded > 0 && outcome.failures.length === 0) {
      toast.success(`${outcome.succeeded} photo${outcome.succeeded > 1 ? 's' : ''} ajoutée${outcome.succeeded > 1 ? 's' : ''}.`);
    } else if (outcome.succeeded > 0) {
      toast.warning(`${outcome.succeeded} photo(s) ajoutée(s), ${outcome.failures.length} échec(s). Voir le détail ci-dessous.`);
    } else {
      toast.error('Aucune photo n’a pu être ajoutée. Voir le détail ci-dessous.');
    }
  };

  const movePhoto = async (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= photos.length) return;
    const reordered = [...photos];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    await withBusy('order', async () => {
      try {
        const response = await apiFetch<PhotoAnnonceDTO[]>(`/api/v1/annonces/${annonce.id}/photos/ordre`, {
          method: 'PUT',
          body: JSON.stringify({ photoIdsEnOrdre: reordered.map((photo) => photo.id) }),
        });
        // L'ordre affiché n'est mis à jour qu'après confirmation du serveur.
        setPhotos(Array.isArray(response) && response.length > 0 ? sortPhotos(response) : reordered.map((photo, i) => ({ ...photo, ordre: i })));
        await syncFromServer();
        onMediaChanged();
        toast.success('Ordre des photos mis à jour.');
      } catch (err: unknown) {
        toast.error(getErrorMessage(err, "Impossible de modifier l'ordre des photos."));
      }
    });
  };

  const deletePhoto = async (photoId: string) => {
    await withBusy('delete', async () => {
      try {
        await apiFetch(`/api/v1/annonces/${annonce.id}/photos/${photoId}`, { method: 'DELETE' });
        setPhotos((current) => current.filter((photo) => photo.id !== photoId));
        setPhotoToDelete(null);
        await syncFromServer();
        onMediaChanged();
        toast.success('Photo supprimée.');
      } catch (err: unknown) {
        toast.error(getErrorMessage(err, 'Impossible de supprimer la photo.'));
      }
    });
  };

  const handleDocumentSelection = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (files.length === 0) return;

    const outcome: UploadOutcome = { succeeded: 0, failures: [] };
    await withBusy('documents', async () => {
      const added: DocumentVerificationDTO[] = [];
      for (const file of files) {
        if (!DOCUMENT_MIME_TYPES.includes(file.type)) {
          outcome.failures.push({ name: file.name, message: 'Format non pris en charge (JPG, PNG, WEBP ou PDF).' });
          continue;
        }
        if (file.size > MAX_DOCUMENT_BYTES) {
          outcome.failures.push({ name: file.name, message: 'Fichier supérieur à 10 Mo.' });
          continue;
        }
        try {
          const body = new FormData();
          body.append('file', file);
          const created = await apiFetch<DocumentVerificationDTO>(
            `/api/v1/annonces/${annonce.id}/documents?typeDocument=${encodeURIComponent(documentType)}`,
            { method: 'POST', body }
          );
          if (created?.id) added.push(created);
          outcome.succeeded += 1;
        } catch (err: unknown) {
          outcome.failures.push({ name: file.name, message: getErrorMessage(err, "Échec de l'envoi") });
        }
      }
      if (outcome.succeeded > 0) {
        const fresh = await syncFromServer();
        setDocuments(fresh?.documentsVerification ?? [...documents, ...added]);
        onMediaChanged();
      }
    });

    setReport({ kind: 'documents', outcome });
    if (outcome.succeeded > 0 && outcome.failures.length === 0) {
      toast.success(`${outcome.succeeded} document${outcome.succeeded > 1 ? 's' : ''} envoyé${outcome.succeeded > 1 ? 's' : ''} pour vérification.`);
    } else if (outcome.succeeded > 0) {
      toast.warning(`${outcome.succeeded} document(s) envoyé(s), ${outcome.failures.length} échec(s). Voir le détail ci-dessous.`);
    } else {
      toast.error('Aucun document n’a pu être envoyé. Voir le détail ci-dessous.');
    }
  };

  const openDocument = async (doc: DocumentVerificationDTO) => {
    try {
      const { blobUrl } = await fetchProtectedBlob(`/api/v1/annonces/${annonce.id}/documents/${doc.id}/fichier`);
      window.open(blobUrl, '_blank', 'noopener,noreferrer');
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Impossible de consulter ce document.'));
    }
  };

  const isBusy = busy !== null;

  return (
    <div className="space-y-6">
      <p className="text-xs text-sahel-600 bg-sand-100 border border-sand-300 rounded-xl p-3">
        Les photos et documents sont enregistrés <strong>immédiatement</strong> lors de chaque action. Les autres
        champs, dont le lien 360°, sont enregistrés avec « Enregistrer les modifications ».
      </p>

      {/* Photos */}
      <div className="space-y-3" aria-busy={busy === 'photos' || busy === 'order' || busy === 'delete'}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h5 className="text-xs font-bold text-sahel-900">
            Photos ({photos.length}/{MAX_PHOTOS_PER_ANNONCE})
          </h5>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-[44px]"
            disabled={isBusy || remainingSlots === 0}
            isLoading={busy === 'photos'}
            onClick={() => photoInputRef.current?.click()}
            leftIcon={<ImagePlus className="w-4 h-4" aria-hidden="true" />}
          >
            Ajouter des photos
          </Button>
          <input
            ref={photoInputRef}
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            tabIndex={-1}
            aria-label="Sélectionner des photos à ajouter"
            data-testid="edit-photos-input"
            onChange={handlePhotoSelection}
          />
        </div>
        <p className="text-[11px] text-sahel-500">JPG, PNG ou WEBP — 5 Mo maximum par photo. La première photo sert de visuel principal.</p>

        {photos.length === 0 ? (
          <p className="text-xs text-sahel-600 border border-dashed border-sand-400 rounded-xl p-4 text-center">
            Aucune photo pour le moment. Sans photo, un visuel illustratif est affiché aux visiteurs.
          </p>
        ) : (
          <ul className="grid grid-cols-1 xs:grid-cols-2 md:grid-cols-3 gap-3" aria-label="Photos de l'annonce">
            {photos.map((photo, index) => {
              const confirming = photoToDelete === photo.id;
              return (
                <li key={photo.id} className="rounded-xl border border-sand-300 bg-white overflow-hidden" data-testid="edit-photo-item">
                  <div className="relative">
                    <PropertyVisual
                      mode="thumbnail"
                      rawUrl={photo.url}
                      listingId={annonce.id}
                      type={annonce.type}
                      title={`${annonce.titre} — photo ${index + 1}`}
                      quartier={annonce.quartier}
                      className="w-full aspect-[4/3]"
                      slotKey={photo.id}
                    />
                    {index === 0 && (
                      <Badge variant="terracotta" size="sm" className="absolute top-2 left-2">
                        Principale
                      </Badge>
                    )}
                  </div>
                  <div className="p-2 space-y-2">
                    {confirming ? (
                      <div className="space-y-2" role="group" aria-label={`Confirmer la suppression de la photo ${index + 1}`}>
                        <p className="text-[11px] text-rose-800 font-medium">Supprimer définitivement cette photo ?</p>
                        <div className="flex gap-2">
                          <Button
                            type="button"
                            size="sm"
                            className="min-h-[44px] flex-1 bg-rose-600 hover:bg-rose-700 text-white focus:ring-rose-400"
                            isLoading={busy === 'delete'}
                            disabled={isBusy}
                            onClick={() => void deletePhoto(photo.id)}
                          >
                            Supprimer
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            className="min-h-[44px] flex-1"
                            disabled={isBusy}
                            onClick={() => setPhotoToDelete(null)}
                          >
                            Garder
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex items-center justify-between gap-1">
                        <div className="flex gap-1">
                          <button
                            type="button"
                            onClick={() => void movePhoto(index, -1)}
                            disabled={isBusy || index === 0}
                            aria-label={`Déplacer la photo ${index + 1} vers le début`}
                            className="w-11 h-11 rounded-lg border border-sand-300 flex items-center justify-center text-sahel-700 hover:bg-sand-100 disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-terracotta-400"
                          >
                            <ArrowUp className="w-4 h-4" aria-hidden="true" />
                          </button>
                          <button
                            type="button"
                            onClick={() => void movePhoto(index, 1)}
                            disabled={isBusy || index === photos.length - 1}
                            aria-label={`Déplacer la photo ${index + 1} vers la fin`}
                            className="w-11 h-11 rounded-lg border border-sand-300 flex items-center justify-center text-sahel-700 hover:bg-sand-100 disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-terracotta-400"
                          >
                            <ArrowDown className="w-4 h-4" aria-hidden="true" />
                          </button>
                        </div>
                        <button
                          type="button"
                          onClick={() => setPhotoToDelete(photo.id)}
                          disabled={isBusy}
                          aria-label={`Supprimer la photo ${index + 1}`}
                          className="w-11 h-11 rounded-lg border border-rose-200 flex items-center justify-center text-rose-600 hover:bg-rose-50 disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-rose-400"
                        >
                          <Trash2 className="w-4 h-4" aria-hidden="true" />
                        </button>
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* Documents */}
      <div className="space-y-3" aria-busy={busy === 'documents'}>
        <h5 className="text-xs font-bold text-sahel-900">Documents de vérification ({documents.length})</h5>
        {documents.length === 0 ? (
          <p className="text-xs text-sahel-600 border border-dashed border-sand-400 rounded-xl p-4 text-center">
            Aucun document envoyé. Un justificatif permet d'obtenir le badge « vérifié ».
          </p>
        ) : (
          <ul className="space-y-2" aria-label="Documents de vérification">
            {documents.map((doc) => {
              const status = DOCUMENT_STATUS[doc.statut] ?? { label: doc.statut, variant: 'warning' as const };
              const typeLabel = types.find((t) => t.valeur === doc.typeDocument)?.libelle ?? doc.typeDocument;
              return (
                <li key={doc.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-xl border border-sand-300 bg-white p-3">
                  <div className="min-w-0 flex items-start gap-2">
                    <FileText className="w-4 h-4 text-sahel-500 shrink-0 mt-0.5" aria-hidden="true" />
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-sahel-900 break-words">{typeLabel}</p>
                      {doc.nomFichier && <p className="text-[11px] text-sahel-500 break-all">{doc.nomFichier}</p>}
                      {doc.statut === 'REJETE' && doc.motifRejet && (
                        <p className="text-[11px] text-rose-700">Motif du rejet : {doc.motifRejet}</p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <Badge variant={status.variant} size="sm">{status.label}</Badge>
                    <Button type="button" variant="ghost" size="sm" className="min-h-[44px]" onClick={() => void openDocument(doc)}>
                      Consulter
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        <div className="flex flex-col sm:flex-row gap-2 sm:items-end">
          <div className="flex-1 min-w-0">
            <label htmlFor="edit-document-type" className="block text-[11px] font-bold text-sahel-700 mb-1">
              Type du nouveau document
            </label>
            <select
              id="edit-document-type"
              value={documentType}
              onChange={(event) => setDocumentType(event.target.value as DocumentType)}
              disabled={isBusy}
              className="w-full min-h-[44px] px-3 rounded-xl border border-sand-300 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-terracotta-500"
            >
              {types.map((t) => (
                <option key={t.valeur} value={t.valeur}>
                  {t.libelle}
                </option>
              ))}
            </select>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-[44px]"
            disabled={isBusy}
            isLoading={busy === 'documents'}
            onClick={() => documentInputRef.current?.click()}
            leftIcon={<Upload className="w-4 h-4" aria-hidden="true" />}
          >
            Ajouter un document
          </Button>
          <input
            ref={documentInputRef}
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp,application/pdf"
            className="sr-only"
            tabIndex={-1}
            aria-label="Sélectionner des documents à envoyer"
            data-testid="edit-documents-input"
            onChange={handleDocumentSelection}
          />
        </div>
        <p className="text-[11px] text-sahel-500">
          JPG, PNG, WEBP ou PDF — 10 Mo maximum. Un document déjà envoyé ne peut pas être supprimé depuis l'interface
          (aucune suppression n'est proposée par le service).
        </p>
      </div>

      {report && report.outcome.failures.length > 0 && (
        <div role="alert" className="rounded-xl border border-amber-300 bg-amber-50 p-3 space-y-1" data-testid="edit-media-report">
          <p className="text-xs font-bold text-amber-900">
            {report.kind === 'photos' ? 'Photos' : 'Documents'} : {report.outcome.succeeded} envoyé(s),{' '}
            {report.outcome.failures.length} en échec
          </p>
          <ul className="text-[11px] text-amber-900 list-disc pl-4 space-y-0.5">
            {report.outcome.failures.map((failure, index) => (
              <li key={`${failure.name}-${index}`}>
                <strong className="break-all">{failure.name}</strong> : {failure.message}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};
