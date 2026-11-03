import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch, fetchProtectedBlob, getPhotoUrl } from '../services/api';
import { AnnonceDTO, DocumentVerificationDTO, PageResponse, VerificationDecisionDTO } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';
import {
  ShieldCheck,
  CheckCircle2,
  XCircle,
  FileText,
  Eye,
  AlertTriangle,
  ExternalLink,
  Loader2,
} from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { Skeleton } from '../components/ui/Skeleton';
import { Modal } from '../components/ui/Modal';
import { AdminNavTabs } from '../components/admin/AdminNavTabs';
import { useReferences } from '../hooks/useReferences';
import { toast } from 'sonner';

interface PreviewState {
  doc: DocumentVerificationDTO;
  annonceTitre: string;
  blobUrl: string;
  contentType: string;
}

export const AdminVerificationPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur } = useAuthStore();
  const { data: references } = useReferences();

  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [selectedAnnonceId, setSelectedAnnonceId] = useState<string | null>(null);
  const [motifRejet, setMotifRejet] = useState('');

  // Authenticated blob viewer state
  const [preview, setPreview] = useState<PreviewState | null>(null);
  const [loadingDocId, setLoadingDocId] = useState<string | null>(null);

  useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion');
    } else if (utilisateur.role !== 'ADMIN') {
      toast.error('Accès refusé : espace réservé aux administrateurs E-Courtier.');
      navigate('/dashboard');
    }
  }, [utilisateur, navigate]);

  // Revoke object URL on unmount or preview close
  useEffect(() => {
    return () => {
      if (preview?.blobUrl) {
        window.URL.revokeObjectURL(preview.blobUrl);
      }
    };
  }, [preview]);

  const closePreview = () => {
    if (preview?.blobUrl) {
      window.URL.revokeObjectURL(preview.blobUrl);
    }
    setPreview(null);
  };

  const { data, isLoading } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['admin-pending-annonces'],
    queryFn: () => apiFetch<PageResponse<AnnonceDTO>>('/api/v1/admin/annonces/en-attente-verification?page=0&size=20'),
    enabled: utilisateur?.role === 'ADMIN',
  });

  const pendingAnnonces = data?.content || [];

  const getDocLabel = (code: string): string => {
    const found = references?.typesDocument?.find((d) => d.valeur === code);
    if (found) return found.libelle;
    switch (code) {
      case 'PIECE_IDENTITE':
        return "Photo du propriétaire avec sa pièce d'identité";
      case 'TITRE_FONCIER':
        return 'Titre foncier / Bail';
      case 'BAIL_PRECEDENT':
        return 'Bail précédent / Facture Senelec';
      default:
        return code;
    }
  };

  const getMissingDocLabels = (docs?: DocumentVerificationDTO[]): string[] => {
    const present = new Set((docs || []).map((d) => d.typeDocument));
    const missing: string[] = [];
    if (!present.has('PIECE_IDENTITE')) {
      missing.push(`${getDocLabel('PIECE_IDENTITE')} (PIECE_IDENTITE)`);
    }
    if (!present.has('TITRE_FONCIER') && !present.has('BAIL_PRECEDENT')) {
      missing.push(`${getDocLabel('TITRE_FONCIER')} ou ${getDocLabel('BAIL_PRECEDENT')} (TITRE_FONCIER / BAIL_PRECEDENT)`);
    }
    return missing;
  };

  const handleOpenPreview = async (doc: DocumentVerificationDTO, annonceTitre: string) => {
    setLoadingDocId(doc.id);
    try {
      const { blobUrl, contentType } = await fetchProtectedBlob(doc.url);
      if (preview?.blobUrl) {
        window.URL.revokeObjectURL(preview.blobUrl);
      }
      setPreview({ doc, annonceTitre, blobUrl, contentType });
    } catch (err: any) {
      toast.error(err.message || 'Impossible de charger la pièce justificative');
    } finally {
      setLoadingDocId(null);
    }
  };

  const verifyMutation = useMutation({
    mutationFn: async ({ id, decision }: { id: string; decision: VerificationDecisionDTO }) => {
      return apiFetch<AnnonceDTO>(`/api/v1/admin/annonces/${id}/verifier`, {
        method: 'PUT',
        body: JSON.stringify(decision),
      });
    },
    onSuccess: (_, variables) => {
      toast.success(
        variables.decision.statutVerification === 'VERIFIEE'
          ? 'Annonce validée ! Le badge de vérification a été attribué.'
          : 'Annonce rejetée avec notification du motif.'
      );
      queryClient.invalidateQueries({ queryKey: ['admin-pending-annonces'] });
      setRejectModalOpen(false);
      setSelectedAnnonceId(null);
      setMotifRejet('');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Échec de la décision de vérification');
    },
  });

  const handleApprove = (annonceId: string) => {
    setSelectedAnnonceId(annonceId);
    verifyMutation.mutate({
      id: annonceId,
      decision: { statutVerification: 'VERIFIEE' },
    });
  };

  const openRejectModal = (annonceId: string) => {
    setSelectedAnnonceId(annonceId);
    setMotifRejet('');
    setRejectModalOpen(true);
  };

  const handleConfirmReject = () => {
    if (!selectedAnnonceId) return;
    if (motifRejet.trim().length < 10) {
      toast.error('Le motif de rejet doit contenir au moins 10 caractères');
      return;
    }
    verifyMutation.mutate({
      id: selectedAnnonceId,
      decision: {
        statutVerification: 'REJETEE',
        motifRejet: motifRejet.trim(),
      },
    });
  };

  if (utilisateur?.role !== 'ADMIN') {
    return null;
  }

  const isPreviewPdf =
    preview &&
    (preview.contentType.toLowerCase().includes('pdf') ||
      (preview.doc.nomFichier || '').toLowerCase().endsWith('.pdf'));

  return (
    <div className="min-h-screen bg-sand-100 py-8">
      <div className="max-w-7xl mx-auto px-4 space-y-6">
        <div className="flex justify-end">
          <AdminNavTabs />
        </div>

        {/* Header Title */}
        <div className="relative rounded-3xl bg-sahel-900 text-white p-8 overflow-hidden shadow-sahel-deep border border-sahel-800 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <PatternClaustra className="opacity-15 text-terracotta-500" />
          <div className="relative z-10 space-y-2">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-terracotta-500/20 border border-terracotta-500/40 text-terracotta-300 text-xs font-semibold">
              <ShieldCheck className="w-4 h-4" />
              <span>Modération & Vérification des Pièces</span>
            </div>
            <h1 className="font-display font-bold text-2xl md:text-3xl">Espace Modération Administrateur</h1>
            <p className="text-xs text-sand-300 max-w-2xl">
              Inspectez chaque pièce justificative (images et PDF protégés) avant d'attribuer le badge vérifié. La validation requiert obligatoirement la pièce d'identité avec photo du propriétaire ainsi qu'un titre foncier ou un bail précédent.
            </p>
          </div>
          <div className="relative z-10 shrink-0">
            <Badge variant="warning" size="lg" className="text-sm px-4 py-2">
              {pendingAnnonces.length} bien(s) en attente
            </Badge>
          </div>
        </div>

        {/* Content list */}
        {isLoading ? (
          <div className="space-y-4">
            <Skeleton variant="card" className="h-32" />
            <Skeleton variant="card" className="h-32" />
            <Skeleton variant="card" className="h-32" />
          </div>
        ) : pendingAnnonces.length === 0 ? (
          <Card className="p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
            <CheckCircle2 className="w-12 h-12 text-emerald-600 mx-auto" />
            <h3 className="font-display font-bold text-lg text-sahel-900">Toutes les demandes ont été traitées !</h3>
            <p className="text-xs text-sahel-600 max-w-md mx-auto">
              Aucune annonce n'est actuellement en attente de vérification.
            </p>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-6">
            {pendingAnnonces.map((annonce) => {
              const docs = annonce.documentsVerification || [];
              const missingTypes = getMissingDocLabels(docs);
              const isComplete = missingTypes.length === 0;

              return (
                <Card key={annonce.id} className="overflow-hidden border border-sand-300 hover:shadow-warm-hover transition-all">
                  <CardBody className="p-6">
                    <div className="flex flex-col lg:flex-row gap-6">
                      {/* Thumbnail */}
                      <div className="w-full lg:w-56 h-40 rounded-xl overflow-hidden bg-sand-200 shrink-0 relative">
                        <img
                          src={getPhotoUrl(annonce.photos?.[0]?.url)}
                          alt={annonce.titre}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute top-2 left-2 flex gap-1">
                          <Badge variant="neutral">{annonce.type}</Badge>
                          <Badge variant="terracotta">{annonce.modeTransaction}</Badge>
                        </div>
                      </div>

                      {/* Details & Documents */}
                      <div className="flex-1 space-y-4">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <h3 className="font-display font-bold text-lg text-sahel-900">{annonce.titre}</h3>
                            <div className="flex flex-wrap items-center gap-3 text-xs text-sahel-700 mt-1">
                              <span><strong>Ville :</strong> {annonce.ville} ({annonce.quartier})</span>
                              <span>
                                <strong>Auteur :</strong> {annonce.auteur?.prenom} {annonce.auteur?.nom} ({annonce.auteur?.email})
                                {(annonce.auteurRole === 'AGENT' || annonce.auteur?.role === 'AGENT') && (
                                  <span className="ml-1.5 text-[10px] font-bold px-2 py-0.5 rounded bg-ocher-100 text-ocher-900">
                                    AGENT
                                  </span>
                                )}
                              </span>
                              <span><strong>Publiée le :</strong> {new Date(annonce.dateCreation).toLocaleDateString('fr-FR')}</span>
                            </div>
                          </div>
                          <span className="font-display font-bold text-lg text-terracotta-700">
                            {annonce.prix.toLocaleString('fr-FR')} FCFA
                          </span>
                        </div>

                        {/* Missing Required Types Alert */}
                        {missingTypes.length > 0 ? (
                          <div className="p-3 rounded-xl bg-amber-50 border border-amber-300 text-xs text-amber-900 space-y-1">
                            <div className="font-bold flex items-center gap-1.5">
                              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                              <span>Dossier incomplet — Types de pièces manquants :</span>
                            </div>
                            <ul className="list-disc list-inside pl-1 text-[11px] space-y-0.5">
                              {missingTypes.map((m, idx) => (
                                <li key={idx}>{m}</li>
                              ))}
                            </ul>
                          </div>
                        ) : (
                          <div className="px-3 py-2 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 font-semibold flex items-center gap-1.5">
                            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                            <span>Toutes les catégories obligatoires de pièces ont été transmises.</span>
                          </div>
                        )}

                        {/* Submitted Documents List with Authenticated Preview */}
                        <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 space-y-2.5">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-bold text-sahel-900 flex items-center gap-1.5">
                              <FileText className="w-4 h-4 text-terracotta-500" />
                              Pièces justificatives soumises ({docs.length})
                            </span>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => navigate(`/annonce/${annonce.id}`)}
                              className="text-xs"
                            >
                              <Eye className="w-3.5 h-3.5 mr-1" />
                              Voir la fiche produit
                            </Button>
                          </div>

                          {docs.length === 0 ? (
                            <p className="text-xs text-charcoal-500 italic">
                              Aucune pièce justificative n'a encore été téléversée pour cette annonce.
                            </p>
                          ) : (
                            <div className="divide-y divide-sand-200 bg-white rounded-xl border border-sand-200 overflow-hidden">
                              {docs.map((doc) => {
                                const fileName = doc.nomFichier || `${doc.typeDocument.toLowerCase()}-${doc.id.slice(0, 8)}`;
                                const isPdf = fileName.toLowerCase().endsWith('.pdf');
                                return (
                                  <div
                                    key={doc.id}
                                    className="p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs"
                                  >
                                    <div className="space-y-0.5 min-w-0">
                                      <div className="flex flex-wrap items-center gap-2">
                                        <span className="font-bold text-charcoal-900">
                                          {getDocLabel(doc.typeDocument)}
                                        </span>
                                        <Badge variant={isPdf ? 'ocher' : 'sahel'}>
                                          {isPdf ? 'PDF' : 'Image'}
                                        </Badge>
                                      </div>
                                      <div className="text-[11px] text-charcoal-600 truncate">
                                        <strong>Fichier :</strong> {fileName} &bull;{' '}
                                        <strong>Envoyé le :</strong>{' '}
                                        {new Date(doc.dateCreation).toLocaleString('fr-FR')}
                                      </div>
                                    </div>

                                    <Button
                                      variant="outline"
                                      size="sm"
                                      onClick={() => handleOpenPreview(doc, annonce.titre)}
                                      disabled={loadingDocId === doc.id}
                                      leftIcon={
                                        loadingDocId === doc.id ? (
                                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                                        ) : (
                                          <Eye className="w-3.5 h-3.5 text-terracotta-600" />
                                        )
                                      }
                                      className="shrink-0"
                                    >
                                      Aperçu
                                    </Button>
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Action buttons */}
                      <div className="flex lg:flex-col justify-end gap-3 shrink-0 lg:w-44 border-t lg:border-t-0 lg:border-l border-sand-300 pt-4 lg:pt-0 lg:pl-6">
                        <Button
                          variant="primary"
                          disabled={!isComplete}
                          title={!isComplete ? 'Impossible de valider tant que les pièces obligatoires sont manquantes' : 'Valider et attribuer le badge'}
                          className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-50"
                          isLoading={verifyMutation.isPending && selectedAnnonceId === annonce.id}
                          onClick={() => handleApprove(annonce.id)}
                        >
                          <CheckCircle2 className="w-4 h-4 mr-1.5" />
                          Valider
                        </Button>

                        <Button
                          variant="ghost"
                          className="flex-1 text-rose-700 hover:bg-rose-50 hover:text-rose-800 border border-rose-200"
                          onClick={() => openRejectModal(annonce.id)}
                        >
                          <XCircle className="w-4 h-4 mr-1.5" />
                          Rejeter
                        </Button>
                      </div>
                    </div>
                  </CardBody>
                </Card>
              );
            })}
          </div>
        )}

        {/* MODAL: DOCUMENT PREVIEW (IMAGE OR PDF BLOB) */}
        <Modal
          isOpen={!!preview}
          onClose={closePreview}
          title={preview ? `${getDocLabel(preview.doc.typeDocument)} — ${preview.annonceTitre}` : 'Aperçu du justificatif'}
          size="lg"
        >
          {preview && (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-charcoal-600 bg-sand-100 p-3 rounded-xl border border-sand-300">
                <div>
                  <strong>Fichier :</strong> {preview.doc.nomFichier || preview.doc.id} &bull;{' '}
                  <strong>Envoyé le :</strong> {new Date(preview.doc.dateCreation).toLocaleString('fr-FR')}
                </div>
                <a
                  href={preview.blobUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 font-bold text-terracotta-600 hover:underline"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  <span>Ouvrir dans un nouvel onglet</span>
                </a>
              </div>

              <div className="rounded-2xl overflow-hidden border border-argile-300 bg-charcoal-900/5 flex items-center justify-center min-h-[320px]">
                {isPreviewPdf ? (
                  <iframe
                    src={preview.blobUrl}
                    title={preview.doc.nomFichier || 'Aperçu PDF'}
                    className="w-full h-[68vh] bg-white"
                  />
                ) : (
                  <img
                    src={preview.blobUrl}
                    alt={preview.doc.nomFichier || 'Pièce justificative'}
                    className="max-h-[68vh] w-auto object-contain mx-auto"
                  />
                )}
              </div>

              <div className="flex justify-end">
                <Button variant="outline" onClick={closePreview}>
                  Fermer l'aperçu
                </Button>
              </div>
            </div>
          )}
        </Modal>

        {/* Modal for rejection motif */}
        <Modal
          isOpen={rejectModalOpen}
          onClose={() => setRejectModalOpen(false)}
          title="Motif de rejet de la vérification"
          size="sm"
        >
          <div className="space-y-4">
            <p className="text-xs text-sahel-600">
              Précisez la raison pour laquelle la vérification est refusée (minimum 10 caractères) afin d'informer le propriétaire ou l'agent.
            </p>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">
                Raison du rejet ({motifRejet.trim().length}/10 caractères min.)
              </label>
              <textarea
                rows={3}
                value={motifRejet}
                onChange={(e) => setMotifRejet(e.target.value)}
                placeholder="Ex: Le titre foncier soumis est illisible ou la pièce d'identité ne correspond pas."
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
              />
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <Button variant="ghost" onClick={() => setRejectModalOpen(false)}>
                Annuler
              </Button>
              <Button
                variant="primary"
                disabled={motifRejet.trim().length < 10}
                className="bg-rose-600 hover:bg-rose-700 text-white disabled:opacity-50"
                isLoading={verifyMutation.isPending}
                onClick={handleConfirmReject}
              >
                Confirmer le rejet
              </Button>
            </div>
          </div>
        </Modal>
      </div>
    </div>
  );
};
