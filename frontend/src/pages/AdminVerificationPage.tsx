import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse, VerificationDecisionDTO } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, CheckCircle2, XCircle, FileText, Eye } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { Skeleton } from '../components/ui/Skeleton';
import { Modal } from '../components/ui/Modal';
import { toast } from 'sonner';

export const AdminVerificationPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur } = useAuthStore();

  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [selectedAnnonceId, setSelectedAnnonceId] = useState<string | null>(null);
  const [motifRejet, setMotifRejet] = useState('');

  // Access Control: check role === 'ADMIN'
  React.useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion');
    } else if (utilisateur.role !== 'ADMIN') {
      toast.error('Accès refusé : espace réservé aux administrateurs E-Courtier.');
      navigate('/dashboard');
    }
  }, [utilisateur, navigate]);

  // Fetch pending listings
  const { data, isLoading } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['admin-pending-annonces'],
    queryFn: () => apiFetch<PageResponse<AnnonceDTO>>('/api/v1/admin/annonces/en-attente-verification?page=0&size=20'),
    enabled: utilisateur?.role === 'ADMIN',
  });

  const pendingAnnonces = data?.content || [];

  // Verification Decision mutation
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
          : 'Annonce rejetée.'
      );
      queryClient.invalidateQueries({ queryKey: ['admin-pending-annonces'] });
      setRejectModalOpen(false);
      setSelectedAnnonceId(null);
      setMotifRejet('');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Échec de la validation de la vérification');
    },
  });

  const handleApprove = (annonceId: string) => {
    verifyMutation.mutate({
      id: annonceId,
      decision: { statutVerification: 'VERIFIEE' },
    });
  };

  const openRejectModal = (annonceId: string) => {
    setSelectedAnnonceId(annonceId);
    setRejectModalOpen(true);
  };

  const handleConfirmReject = () => {
    if (!selectedAnnonceId) return;
    verifyMutation.mutate({
      id: selectedAnnonceId,
      decision: {
        statutVerification: 'REJETEE',
        motifRejet: motifRejet || 'Pièce justificative non conforme',
      },
    });
  };

  if (utilisateur?.role !== 'ADMIN') {
    return null;
  }

  return (
    <div className="min-h-screen bg-sand-100 py-10">
      <div className="max-w-7xl mx-auto px-4 space-y-8">
        
        {/* Header Title */}
        <div className="relative rounded-3xl bg-sahel-900 text-white p-8 overflow-hidden shadow-sahel-deep border border-sahel-800 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <PatternClaustra className="opacity-15 text-terracotta-500" />
          <div className="relative z-10 space-y-2">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-terracotta-500/20 border border-terracotta-500/40 text-terracotta-300 text-xs font-semibold">
              <ShieldCheck className="w-4 h-4" />
              <span>Modération & Vérification Cadastrale</span>
            </div>
            <h1 className="font-display font-bold text-2xl md:text-3xl">Espace Modération Administrateur</h1>
            <p className="text-xs text-sand-300 max-w-xl">
              Auditez les pièces justificatives (titre foncier, bail, carte d'identité) soumises par les propriétaires avant d'accorder le badge vérifié E-Courtier.
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
              Aucune annonce n'est actuellement en attente de vérification cadastrale.
            </p>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-6">
            {pendingAnnonces.map((annonce) => (
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
                      <div className="absolute top-2 left-2">
                        <Badge variant="neutral">{annonce.type}</Badge>
                      </div>
                    </div>

                    {/* Details */}
                    <div className="flex-1 space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <h3 className="font-display font-bold text-lg text-sahel-900">{annonce.titre}</h3>
                        <span className="font-display font-bold text-lg text-terracotta-700">
                          {annonce.prix.toLocaleString('fr-FR')} FCFA
                        </span>
                      </div>

                      <p className="text-xs text-sahel-600 line-clamp-2">{annonce.description}</p>

                      <div className="flex flex-wrap items-center gap-4 text-xs text-sahel-700 pt-1">
                        <span><strong>Ville:</strong> {annonce.ville} ({annonce.quartier})</span>
                        <span><strong>Propriétaire:</strong> {annonce.auteur?.prenom} {annonce.auteur?.nom} ({annonce.auteur?.email})</span>
                        <span><strong>Date:</strong> {new Date(annonce.dateCreation).toLocaleDateString('fr-FR')}</span>
                      </div>

                      {/* Documents attachment status */}
                      <div className="p-3 rounded-xl bg-sand-50 border border-sand-200 flex flex-wrap items-center justify-between gap-3 text-xs">
                        <div className="flex items-center gap-2">
                          <FileText className="w-4 h-4 text-terracotta-500" />
                          <span>
                            Documents soumis: <strong>{annonce.documentsVerification?.length || 0} fichier(s)</strong>
                          </span>
                        </div>
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
                    </div>

                    {/* Action buttons */}
                    <div className="flex lg:flex-col justify-end gap-3 shrink-0 lg:w-44 border-t lg:border-t-0 lg:border-l border-sand-300 pt-4 lg:pt-0 lg:pl-6">
                      <Button
                        variant="primary"
                        className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white"
                        isLoading={verifyMutation.isPending && selectedAnnonceId === annonce.id}
                        onClick={() => handleApprove(annonce.id)}
                      >
                        <CheckCircle2 className="w-4 h-4 mr-1.5" />
                        Valider
                      </Button>

                      <Button
                        variant="ghost"
                        className="flex-1 text-rose-700 hover:bg-rose-50 hover:text-rose-800"
                        onClick={() => openRejectModal(annonce.id)}
                      >
                        <XCircle className="w-4 h-4 mr-1.5" />
                        Rejeter
                      </Button>
                    </div>
                  </div>
                </CardBody>
              </Card>
            ))}
          </div>
        )}

        {/* Modal for rejection motif */}
        <Modal
          isOpen={rejectModalOpen}
          onClose={() => setRejectModalOpen(false)}
          title="Motif de rejet de la vérification"
          size="sm"
        >
          <div className="space-y-4">
            <p className="text-xs text-sahel-600">
              Précisez la raison pour laquelle le document est refusé afin d'informer le propriétaire.
            </p>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Raison du rejet</label>
              <textarea
                rows={3}
                value={motifRejet}
                onChange={(e) => setMotifRejet(e.target.value)}
                placeholder="Ex: Le titre foncier soumis est flou ou expiré."
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-sm focus:ring-2 focus:ring-terracotta-500 outline-none"
              />
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <Button variant="ghost" onClick={() => setRejectModalOpen(false)}>
                Annuler
              </Button>
              <Button
                variant="primary"
                className="bg-rose-600 hover:bg-rose-700 text-white"
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
