import React, { useState } from 'react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { 
  ShieldCheck, 
  MapPin, 
  CheckCircle2, 
  ArrowLeft,
  Calendar,
  Clock,
  FileCheck,
  Star,
  AlertTriangle,
  Compass,
  UserCheck,
  MessageSquare,
  FileText,
  Upload,
  Building2,
  ChevronLeft,
  ChevronRight,
  Images
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { API_BASE_URL, apiFetch, getPhotoUrl } from '../services/api';
import { messagerieService } from '../services/messagerie.service';
import { PropertyVisual } from '../components/property/PropertyVisual';
import { isUsablePhotoUrl, mapPropertyCategory, selectFallbackPhoto } from '../lib/propertyMedia';
import { EquipementBadge, ProximiteBadge } from '../components/ui/ReferenceIcons';
import { getErrorMessage, getErrorStatus } from '../lib/errors';
import { 
  AnnonceDTO, 
  RendezVousResponseDTO, 
  TransactionResponseDTO, 
  AvisSummaryDTO, 
  ReputationDTO,
  ConversationChatDTO
} from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { Avatar } from '../components/ui/Avatar';
import { Skeleton } from '../components/ui/Skeleton';
import { toast } from 'sonner';
import { FavoriteButton } from '../components/ui/FavoriteButton';

// Lazy-loaded heavy components (loaded on-demand / interaction only)
const LazyLeafletMap = React.lazy(() => import('../components/map/LeafletMap').then((m) => ({ default: m.LeafletMap })));
const LazyLightbox = React.lazy(() => {
  import('yet-another-react-lightbox/styles.css');
  return import('yet-another-react-lightbox');
});
const LazyTransactionDetailsModal = React.lazy(() => import('../components/transaction/TransactionDetailsModal').then((m) => ({ default: m.TransactionDetailsModal })));
const LazySignalementModal = React.lazy(() => import('../components/ui/SignalementModal').then((m) => ({ default: m.SignalementModal })));
const LazyChatDrawer = React.lazy(() => import('../components/messagerie/ChatDrawer').then((m) => ({ default: m.ChatDrawer })));

const PUBLICATION_STATUS_META: Record<
  AnnonceDTO['statutPublication'],
  { label: string; badgeVariant: 'sahel' | 'warning' | 'rose'; notice?: string }
> = {
  DISPONIBLE: { label: 'Disponible', badgeVariant: 'sahel' },
  RESERVEE: {
    label: 'Réservée',
    badgeVariant: 'warning',
    notice: "Ce bien fait actuellement l'objet d'une réservation en cours. Les nouvelles demandes sont temporairement suspendues.",
  },
  LOUEE: {
    label: 'Déjà louée',
    badgeVariant: 'rose',
    notice: "Ce logement est déjà loué et n'est plus disponible à la visite ni à la réservation.",
  },
  VENDUE: {
    label: 'Bien vendu',
    badgeVariant: 'rose',
    notice: "Ce bien a été vendu et n'est plus disponible à la visite ni à la réservation.",
  },
  SUSPENDUE: {
    label: 'Annonce suspendue',
    badgeVariant: 'rose',
    notice: "Cette annonce est actuellement suspendue et indisponible à la réservation.",
  },
};

const SPEC_SM_GRID_COLS: Record<number, string> = {
  1: 'sm:grid-cols-1',
  2: 'sm:grid-cols-2',
  3: 'sm:grid-cols-3',
  4: 'sm:grid-cols-4',
  5: 'sm:grid-cols-5',
};

// Deferred Map: loads Leaflet and tile resources strictly when scrolled into view
const DeferredMap: React.FC<{ property: AnnonceDTO }> = ({ property }) => {
  const [shouldLoad, setShouldLoad] = useState(false);
  const containerRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (!containerRef.current) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setShouldLoad(true);
          observer.disconnect();
        }
      },
      { rootMargin: '250px' }
    );
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={containerRef} className="h-72 rounded-2xl overflow-hidden border border-argile-200 shadow-sm bg-sand-200 relative">
      {shouldLoad ? (
        <React.Suspense
          fallback={
            <div className="w-full h-full flex items-center justify-center bg-sand-100 text-charcoal-500 text-xs font-semibold gap-2">
              <MapPin className="w-5 h-5 text-terracotta-500 animate-pulse" />
              <span>Chargement de la carte...</span>
            </div>
          }
        >
          <LazyLeafletMap
            properties={[property]}
            selectedPropertyId={property.id}
            center={[property.latitude, property.longitude]}
            zoom={14}
          />
        </React.Suspense>
      ) : (
        <div
          className="w-full h-full flex flex-col items-center justify-center bg-sand-100/90 text-charcoal-500 text-xs font-semibold gap-1.5 p-4 text-center cursor-pointer hover:bg-sand-200 transition-colors"
          onClick={() => setShouldLoad(true)}
        >
          <MapPin className="w-6 h-6 text-terracotta-500" />
          <span>{property.quartier ? `${property.quartier}, ` : ''}{property.ville}</span>
          <span className="text-[11px] text-charcoal-400 font-normal">Cliquer pour charger la carte interactive</span>
        </div>
      )}
    </div>
  );
};

export const PropertyDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { utilisateur, accessToken } = useAuthStore();
  const wasPrefetchedRef = React.useRef(false);

  const [galleryNav, setGalleryNav] = useState<{
    forId?: string;
    lightboxOpen: boolean;
    selectedIndex: number;
    mobileIndex: number;
  }>({ forId: id, lightboxOpen: false, selectedIndex: 0, mobileIndex: 0 });
  const [resolvedSlideUrls, setResolvedSlideUrls] = useState<Record<string, string | null>>({});

  const normalizeGalleryNav = (prev: typeof galleryNav) =>
    prev.forId === id ? prev : { forId: id, lightboxOpen: false, selectedIndex: 0, mobileIndex: 0 };

  const isLightboxOpen = galleryNav.forId === id ? galleryNav.lightboxOpen : false;
  const selectedPhotoIndex = galleryNav.forId === id ? galleryNav.selectedIndex : 0;
  const mobilePhotoIndex = galleryNav.forId === id ? galleryNav.mobileIndex : 0;

  const setIsLightboxOpen = (open: boolean) =>
    setGalleryNav((prev) => ({ ...normalizeGalleryNav(prev), forId: id, lightboxOpen: open }));
  const setSelectedPhotoIndex = (index: number) =>
    setGalleryNav((prev) => ({ ...normalizeGalleryNav(prev), forId: id, selectedIndex: index }));
  const setMobilePhotoIndex = (updater: number | ((prev: number) => number)) =>
    setGalleryNav((prev) => {
      const current = normalizeGalleryNav(prev);
      const nextIndex = typeof updater === 'function' ? updater(current.mobileIndex) : updater;
      return { ...current, forId: id, mobileIndex: nextIndex };
    });

  const handleGalleryStageChange = React.useCallback(
    (slideKey: string, url: string | null) => {
      setResolvedSlideUrls((prev) =>
        prev[slideKey] === url ? prev : { ...prev, [slideKey]: url }
      );
    },
    []
  );
  const [isVisitModalOpen, setIsVisitModalOpen] = useState(false);
  const [visitDate, setVisitDate] = useState('');
  const [visitTime, setVisitTime] = useState('10:00');
  const [isSubmittingVisit, setIsSubmittingVisit] = useState(false);

  const [isTxModalOpen, setIsTxModalOpen] = useState(false);
  const [selectedTx, setSelectedTx] = useState<TransactionResponseDTO | null>(null);
  const [isInitiatingTx, setIsInitiatingTx] = useState(false);

  const [isSignalementModalOpen, setIsSignalementModalOpen] = useState(false);
  const [is360ModalOpen, setIs360ModalOpen] = useState(false);

  // Chat Drawer state
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [activeConversation, setActiveConversation] = useState<ConversationChatDTO | null>(null);
  const [isInitiatingChat, setIsInitiatingChat] = useState(false);

  const handleContacterProprietaire = async () => {
    if (!utilisateur) {
      toast.info('Veuillez vous connecter pour contacter le propriétaire');
      navigate('/connexion', { state: { from: location } });
      return;
    }
    if (!id) return;
    try {
      setIsInitiatingChat(true);
      const conv = await messagerieService.initierConversation(id);
      setActiveConversation(conv);
      setIsChatOpen(true);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Impossible d'ouvrir la discussion"));
    } finally {
      setIsInitiatingChat(false);
    }
  };

  // Avis Form state
  const [newRating, setNewRating] = useState<number>(5);
  const [newComment, setNewComment] = useState('');
  const [isSubmittingAvis, setIsSubmittingAvis] = useState(false);

  // Verification Document upload state
  const [docTypeToUpload, setDocTypeToUpload] = useState<'TITRE_FONCIER' | 'BAIL_PRECEDENT' | 'PIECE_IDENTITE'>('TITRE_FONCIER');
  const [docFilesToUpload, setDocFilesToUpload] = useState<File[]>([]);
  const [isUploadingDoc, setIsUploadingDoc] = useState(false);

  const handleUploadDocument = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || docFilesToUpload.length === 0) {
      toast.error('Veuillez sélectionner au moins un fichier');
      return;
    }
    setIsUploadingDoc(true);
    try {
      for (const file of docFilesToUpload) {
        const formData = new FormData();
        formData.append('file', file);
        await apiFetch(`/api/v1/annonces/${id}/documents?typeDocument=${docTypeToUpload}`, {
          method: 'POST',
          body: formData,
        });
      }
      toast.success('Document(s) envoyé(s) pour vérification');
      setDocFilesToUpload([]);
      queryClient.invalidateQueries({ queryKey: ['annonce', id] });
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Erreur lors de l'envoi du document"));
    } finally {
      setIsUploadingDoc(false);
    }
  };

  // Fetch listing details with hardened prefetch JSON validation
  const { data: property, isLoading, isError, refetch: refetchProperty } = useQuery<AnnonceDTO>({
    queryKey: ['annonce', id],
    queryFn: async () => {
      interface PrefetchedWindow {
        __PREFETCHED_ANNONCE_ID__?: string;
        __PREFETCHED_ANNONCE_PROMISE__?: Promise<{ ok?: boolean; status?: number; data?: unknown }> | null;
      }

      const isValidAnnoncePayload = (payload: unknown): payload is AnnonceDTO => {
        if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return false;
        const candidate = payload as Partial<AnnonceDTO>;
        return (
          typeof candidate.id === 'string' &&
          candidate.id === id &&
          typeof candidate.titre === 'string' &&
          candidate.titre.length > 0 &&
          typeof candidate.prix === 'number'
        );
      };

      const isPrefetchConfirmedJsonResponse = (): boolean | null => {
        if (typeof window === 'undefined' || typeof performance === 'undefined') return null;
        const targetPath = `/api/v1/annonces/${id}`;
        const entries = performance.getEntriesByType('resource') as Array<
          PerformanceResourceTiming & { contentType?: string }
        >;
        for (let i = entries.length - 1; i >= 0; i--) {
          const entry = entries[i];
          try {
            const parsed = new URL(entry.name, window.location.origin);
            if (parsed.origin === window.location.origin && parsed.pathname === targetPath) {
              if (typeof entry.contentType === 'string') {
                return entry.contentType.toLowerCase().includes('json');
              }
              return null;
            }
          } catch {
            continue;
          }
        }
        return null;
      };

      const isApiCrossOrigin = (): boolean => {
        if (typeof window === 'undefined') return false;
        try {
          return new URL(API_BASE_URL || '/', window.location.origin).origin !== window.location.origin;
        } catch {
          return false;
        }
      };

      const win = typeof window !== 'undefined' ? (window as unknown as PrefetchedWindow) : null;
      if (win && win.__PREFETCHED_ANNONCE_ID__ === id && win.__PREFETCHED_ANNONCE_PROMISE__) {
        const prefetched = win.__PREFETCHED_ANNONCE_PROMISE__;
        win.__PREFETCHED_ANNONCE_PROMISE__ = null;
        try {
          const res = await prefetched;
          if (res) {
            // Case 1: Prefetch succeeded with genuine listing JSON payload
            if (res.ok && isValidAnnoncePayload(res.data)) {
              wasPrefetchedRef.current = true;
              return res.data;
            }

            // Case 2: Confirm definitive API client error ONLY when response was JSON (never Vite HTML/empty 404)
            if (res.status === 404 || res.status === 403 || res.status === 410) {
              const jsonConfirmed = isPrefetchConfirmedJsonResponse();
              const allowErrorWithoutTiming = jsonConfirmed === null && !isApiCrossOrigin();
              if (jsonConfirmed === true || allowErrorWithoutTiming) {
                const clientErr = new Error(
                  res.status === 404 ? 'Annonce introuvable ou supprimée' : `Erreur ${res.status}`
                ) as Error & { status?: number };
                clientErr.status = res.status;
                throw clientErr;
              }
            }
          }
        } catch (err: unknown) {
          const status = getErrorStatus(err);
          if (status === 404 || status === 403 || status === 410) {
            throw err;
          }
        }
      }
      return apiFetch<AnnonceDTO>(`/api/v1/annonces/${id}`);
    },
    enabled: !!id,
    retry: (failureCount, error: unknown) => {
      const status = getErrorStatus(error);
      if (status === 404 || status === 401 || status === 403 || status === 410) {
        return false;
      }
      return failureCount < 1;
    },
  });

  // Reconcile favorite status for authenticated session after initial anonymous prefetch
  React.useEffect(() => {
    if (accessToken && property && wasPrefetchedRef.current) {
      wasPrefetchedRef.current = false;
      refetchProperty();
    }
  }, [accessToken, property, refetchProperty]);

  // Fetch listing reviews
  const { data: avisSummary, refetch: refetchAvis } = useQuery<AvisSummaryDTO>({
    queryKey: ['annonce-avis', id],
    queryFn: () => apiFetch<AvisSummaryDTO>(`/api/v1/annonces/${id}/avis`),
    enabled: !!id,
  });

  // Fetch landlord reputation
  const { data: landlordReputation } = useQuery<ReputationDTO>({
    queryKey: ['reputation-utilisateur', property?.auteur?.id],
    queryFn: () => apiFetch<ReputationDTO>(`/api/v1/utilisateurs/${property?.auteur?.id}/reputation`),
    enabled: !!property?.auteur?.id,
  });

  // Fetch user visits
  const { data: userVisits } = useQuery<RendezVousResponseDTO[]>({
    queryKey: ['my-visites'],
    queryFn: () => apiFetch<RendezVousResponseDTO[]>('/api/v1/rendez-vous/mes-rendez-vous'),
    enabled: !!utilisateur,
  });

  // Fetch user transactions
  const { data: userTransactions } = useQuery<TransactionResponseDTO[]>({
    queryKey: ['my-transactions'],
    queryFn: () => apiFetch<TransactionResponseDTO[]>('/api/v1/transactions/mes-transactions'),
    enabled: !!utilisateur,
  });

  const hasEffectueVisit = userVisits?.some(
    (v) => v.annonceId === id && v.statut === 'EFFECTUE'
  );

  const existingTx = userTransactions?.find(
    (tx) =>
      tx.annonceId === id &&
      tx.dossierActif !== false &&
      (tx.statutSequestre === 'INITIALISE' ||
        tx.statutSequestre === 'BLOQUE' ||
        tx.statutSequestre === 'LIBERE')
  );

  const handleDemanderVisiteClick = () => {
    if ((property?.statutPublication ?? 'DISPONIBLE') !== 'DISPONIBLE') {
      toast.warning("Ce bien n'est plus disponible pour de nouvelles visites");
      return;
    }

    if (!utilisateur) {
      toast.info('Veuillez vous connecter pour planifier une visite');
      navigate('/connexion', { state: { from: location.pathname } });
      return;
    }

    if (utilisateur.role !== 'LOCATAIRE') {
      toast.warning('Seuls les utilisateurs inscrits comme Locataire peuvent demander une visite');
      return;
    }

    setIsVisitModalOpen(true);
  };

  const handleConfirmVisit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!visitDate || !visitTime || !id) {
      toast.error('Veuillez sélectionner une date et une heure');
      return;
    }

    setIsSubmittingVisit(true);
    try {
      const dateHeureIso = `${visitDate}T${visitTime}:00`;
      const res = await apiFetch<RendezVousResponseDTO>(`/api/v1/annonces/${id}/rendez-vous`, {
        method: 'POST',
        body: JSON.stringify({
          dateHeure: dateHeureIso,
        }),
      });

      toast.success(`Demande de visite transmise avec succès ! (RDV #${res.id.slice(0, 8)})`);
      queryClient.invalidateQueries({ queryKey: ['my-visites'] });
      setIsVisitModalOpen(false);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Échec de la réservation de visite'));
    } finally {
      setIsSubmittingVisit(false);
    }
  };

  const handleInitierTransaction = async () => {
    if (!id || !property) return;
    if ((property.statutPublication ?? 'DISPONIBLE') !== 'DISPONIBLE') {
      toast.warning("Ce bien n'est plus disponible pour initier une nouvelle transaction");
      return;
    }
    setIsInitiatingTx(true);
    try {
      const tx = await apiFetch<TransactionResponseDTO>(`/api/v1/annonces/${id}/transactions`, {
        method: 'POST',
        body: JSON.stringify({ montantCaution: property.prix }),
      });
      toast.success('Transaction de location initiée avec succès !');
      queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
      setSelectedTx(tx);
      setIsTxModalOpen(true);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Impossible d'initier la transaction"));
    } finally {
      setIsInitiatingTx(false);
    }
  };

  const handleSoumettreAvis = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return;
    setIsSubmittingAvis(true);
    try {
      await apiFetch(`/api/v1/annonces/${id}/avis`, {
        method: 'POST',
        body: JSON.stringify({ note: newRating, commentaire: newComment }),
      });
      toast.success('Avis publié avec succès ! Merci pour votre retour.');
      setNewComment('');
      refetchAvis();
    } catch (err: unknown) {
      const status = getErrorStatus(err);
      const message = getErrorMessage(err, "Erreur lors de la publication de l'avis");
      if (status === 409 || message.includes('déjà noté')) {
        toast.warning('Vous avez déjà noté cette annonce');
      } else if (status === 403 || message.includes('visite validée')) {
        toast.error('Seuls les locataires ayant effectué une visite validée peuvent déposer un avis');
      } else {
        toast.error(message);
      }
    } finally {
      setIsSubmittingAvis(false);
    }
  };

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6 space-y-8">
        {/* NAVIGATION BAR SKELETON */}
        <div className="flex items-center justify-between">
          <Skeleton variant="text" className="w-36 h-5" />
          <div className="flex items-center gap-2">
            <Skeleton variant="text" className="w-28 h-7 rounded-xl" />
            <Skeleton variant="text" className="w-16 h-6 rounded-lg" />
          </div>
        </div>

        {/* HEADER TITLE & BADGES SKELETON */}
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <Skeleton variant="text" className="w-20 h-6 rounded-full" />
            <Skeleton variant="text" className="w-24 h-6 rounded-full" />
            <Skeleton variant="text" className="w-40 h-6 rounded-full" />
          </div>
          <Skeleton variant="text" className="w-3/4 max-w-xl h-10" />
          <Skeleton variant="text" className="w-48 h-4" />
        </div>

        {/* GALLERY GRID SKELETON */}
        <Skeleton variant="rect" className="w-full h-[380px] rounded-3xl" />

        {/* DETAILS SECTION SKELETON */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 space-y-6">
            <Skeleton variant="rect" className="w-full h-24 rounded-2xl" />
            <Skeleton variant="rect" className="w-full h-48 rounded-2xl" />
          </div>
          <Skeleton variant="card" className="h-80" />
        </div>
      </div>
    );
  }

  if (isError || !property) {
    return (
      <div className="max-w-md mx-auto my-16 p-8 text-center space-y-4 bg-white rounded-3xl border border-argile-200">
        <h2 className="font-display font-bold text-xl text-charcoal-900">Annonce introuvable</h2>
        <p className="text-xs text-charcoal-600">Cette annonce n'existe plus ou a été retirée de la publication.</p>
        <Button variant="primary" onClick={() => navigate('/recherche')}>
          Retourner aux recherches
        </Button>
      </div>
    );
  }

  const rawPhotos = property.photos || [];
  const gallerySlotCount = Math.max(1, rawPhotos.length);
  const activeMobilePhotoIndex = Math.min(mobilePhotoIndex, gallerySlotCount - 1);
  const publicationStatus = property.statutPublication ?? 'DISPONIBLE';
  const statusMeta = PUBLICATION_STATUS_META[publicationStatus];
  const isAvailable = publicationStatus === 'DISPONIBLE';

  const hasSuperficie =
    typeof property.superficie === 'number' &&
    Number.isFinite(property.superficie) &&
    property.superficie > 0;
  const hasPieces =
    typeof property.nombrePieces === 'number' &&
    Number.isFinite(property.nombrePieces) &&
    property.nombrePieces > 0;
  const hasEtage = property.etage !== undefined && property.etage !== null;

  const specItems: Array<{ label: string; value: string }> = [
    ...(hasSuperficie ? [{ label: 'Superficie', value: `${property.superficie} m²` }] : []),
    ...(hasPieces
      ? [
          {
            label: 'Nombre de pièces',
            value: `${property.nombrePieces} ${property.nombrePieces === 1 ? 'pièce' : 'pièces'}`,
          },
        ]
      : []),
    ...(hasEtage ? [{ label: 'Étage', value: property.etage === 0 ? 'RDC' : `${property.etage}e` }] : []),
    { label: 'Meublé', value: property.meuble ? 'Oui' : 'Non' },
    { label: 'Animaux', value: property.animauxAcceptes ? 'Acceptés' : 'Refusés' },
  ];
  const specSmColsClass = SPEC_SM_GRID_COLS[specItems.length] ?? 'sm:grid-cols-4';
  const isOddSpecCount = specItems.length % 2 === 1;

  const galleryFallbacks = (() => {
    const used = new Set<string>();
    const category = mapPropertyCategory(property.type, property.titre);
    return Array.from({ length: gallerySlotCount }, (_, idx) =>
      selectFallbackPhoto(category, `${property.id}:gallery-${idx}`, used)
    );
  })();

  const lightboxSlides = Array.from({ length: gallerySlotCount }, (_, idx) => {
    const slideKey = `${property.id}:${idx}`;
    const resolved = resolvedSlideUrls[slideKey];
    if (resolved) return { src: resolved };
    if (resolved === null) return null;
    const rawUrl = rawPhotos[idx]?.url;
    if (isUsablePhotoUrl(rawUrl)) return { src: getPhotoUrl(rawUrl) };
    const fb = galleryFallbacks[idx];
    return fb ? { src: fb } : null;
  }).filter((item): item is { src: string } => Boolean(item));

  const secondaryCount = gallerySlotCount - 1;
  const desktopContainerCols =
    gallerySlotCount === 1
      ? 'md:grid-cols-1'
      : gallerySlotCount === 2
        ? 'md:grid-cols-2'
        : 'md:grid-cols-4';
  const desktopSecondaryGridClass =
    secondaryCount === 1
      ? 'grid-cols-1'
      : secondaryCount === 2
        ? 'grid-cols-1 grid-rows-2'
        : 'grid-cols-2 grid-rows-2';

  return (
    <div className="max-w-7xl mx-auto px-3 sm:px-4 lg:pr-[68px] min-[1380px]:px-4 py-3 sm:py-3.5 pb-24 lg:pb-8 space-y-3 sm:space-y-4">
      
      {/* NAVIGATION BAR & DISCRETE REPORT BUTTON */}
      <div className="flex items-center justify-between gap-2">
        <Link
          to="/recherche"
          aria-label="Retour aux résultats"
          className="inline-flex items-center gap-1.5 sm:gap-2 text-xs font-semibold text-sahel-700 hover:text-terracotta-600 transition-colors shrink-0"
        >
          <ArrowLeft className="w-4 h-4 shrink-0" />
          <span className="sm:hidden">Résultats</span>
          <span className="hidden sm:inline">Retour aux résultats</span>
        </Link>

        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          {id && property && (
            <div className="flex items-center gap-1.5 bg-white border border-sand-300 px-2 sm:px-3 py-0.5 sm:py-1 rounded-xl shadow-sm">
              <FavoriteButton
                annonceId={id}
                estFavori={property.estFavori || false}
                size="sm"
              />
              <span className="text-[11px] sm:text-xs font-semibold text-charcoal-700 whitespace-nowrap">
                <span className="sm:hidden">{property.estFavori ? 'En favoris' : 'Favoris'}</span>
                <span className="hidden sm:inline">{property.estFavori ? 'Dans vos favoris' : 'Ajouter aux favoris'}</span>
              </span>
            </div>
          )}

          <button
            onClick={() => {
              if (!utilisateur) {
                toast.info('Veuillez vous connecter pour signaler une annonce');
                navigate('/connexion', { state: { from: location.pathname } });
                return;
              }
              setIsSignalementModalOpen(true);
            }}
            className="inline-flex items-center gap-1 text-[11px] sm:text-xs text-charcoal-500 hover:text-rose-600 transition-colors py-1 px-2 sm:px-2.5 rounded-lg border border-transparent hover:border-rose-200 whitespace-nowrap"
          >
            <AlertTriangle className="w-3.5 h-3.5 text-rose-500 shrink-0" />
            <span>Signaler</span>
          </button>
        </div>
      </div>

      {/* HEADER TITLE & BADGES & RATING SUMMARY */}
      <div className="space-y-1.5 sm:space-y-2">
        <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
          <Badge variant="terracotta">{property.modeTransaction}</Badge>
          <Badge variant="sahel">{property.type}</Badge>
          {publicationStatus !== 'DISPONIBLE' && (
            <Badge variant={statusMeta.badgeVariant} data-testid="publication-status-badge">
              {statusMeta.label}
            </Badge>
          )}
          {property.statutVerification === 'VERIFIEE' && (
            <Badge variant="success" className="flex items-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
              <span>Contrôle Cadastral Effectué</span>
            </Badge>
          )}
          {(property.auteurRole === 'AGENT' || property.auteur?.role === 'AGENT') && (
            <Badge variant="sahel" className="flex items-center gap-1 bg-ocher-100 text-ocher-900 border border-ocher-300">
              <Building2 className="w-3.5 h-3.5 text-ocher-800" />
              <span>Publié par un agent immobilier</span>
            </Badge>
          )}

          {/* Average Rating Star Badge */}
          {avisSummary?.noteMoyenne ? (
            <div className="flex items-center gap-1 text-xs font-bold bg-ocher-50 text-ocher-900 border border-ocher-300 px-2.5 py-0.5 rounded-full shadow-sm">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-500" />
              <span>{avisSummary.noteMoyenne.toFixed(1)} / 5</span>
              <span className="text-ocher-700 text-[10px]">({avisSummary.nombreAvis} avis)</span>
            </div>
          ) : (
            <span className="text-[11px] text-charcoal-400 bg-sand-200 px-2.5 py-0.5 rounded-full font-medium">
              Aucun avis
            </span>
          )}
        </div>

        <h1 className="font-display font-extrabold text-xl xs:text-2xl xl:text-3xl text-charcoal-900 tracking-tight leading-tight">
          {property.titre}
        </h1>

        <div className="flex flex-wrap items-center gap-3 text-xs font-semibold text-charcoal-600">
          <span className="flex items-center gap-1">
            <MapPin className="w-4 h-4 text-terracotta-500" />
            {property.quartier ? `${property.quartier}, ` : ''}{property.ville}
          </span>

          {property.lienVisite360 && (
            <button
              onClick={() => setIs360ModalOpen(true)}
              className="flex items-center gap-1.5 text-terracotta-600 hover:text-terracotta-700 bg-terracotta-50 hover:bg-terracotta-100 px-3 py-0.5 rounded-full border border-terracotta-200 font-bold transition-all"
            >
              <Compass className="w-4 h-4 [animation:spin_12s_linear_infinite] motion-reduce:animate-none" />
              <span>Visite Virtuelle 360°</span>
            </button>
          )}
        </div>
      </div>

      {/* ADAPTIVE GALLERY GRID (1..10 PHOTOS, 3-TIER FALLBACK, SYNCHRONIZED LIGHTBOX) */}
      <div
        data-testid="property-gallery-grid"
        className={`grid grid-cols-1 ${desktopContainerCols} gap-3 h-56 xs:h-48 sm:h-56 md:h-64 lg:h-[235px] xl:h-[248px] min-[1400px]:h-[340px] rounded-2xl sm:rounded-3xl overflow-hidden shadow-warm-card`}
      >
        <div
          className={`${
            gallerySlotCount >= 3 ? 'md:col-span-2' : 'md:col-span-1'
          } h-full min-h-0 cursor-pointer relative group bg-sand-200`}
          onClick={() => {
            setSelectedPhotoIndex(activeMobilePhotoIndex);
            setIsLightboxOpen(true);
          }}
        >
          <PropertyVisual
            mode="gallery"
            rawUrl={rawPhotos[activeMobilePhotoIndex]?.url}
            listingId={property.id}
            type={property.type}
            title={
              activeMobilePhotoIndex === 0
                ? property.titre
                : `${property.titre} — Photo ${activeMobilePhotoIndex + 1}`
            }
            quartier={property.quartier}
            fallbackUrl={galleryFallbacks[activeMobilePhotoIndex]}
            slotKey={`gallery-${activeMobilePhotoIndex}`}
            badgePosition={gallerySlotCount > 1 ? 'bottom-left' : 'bottom-right'}
            loading="eager"
            fetchPriority="high"
            width={760}
            height={360}
            className="w-full h-full"
            imageClassName="group-hover:scale-[1.02] transition-transform duration-300"
            onStageChange={(_stage, url) =>
              handleGalleryStageChange(`${property.id}:${activeMobilePhotoIndex}`, url)
            }
          />
          {gallerySlotCount > 1 && (
            <div
              onClick={(e) => e.stopPropagation()}
              className="md:hidden absolute top-3 right-3 flex items-center gap-1 rounded-full bg-charcoal-900/75 px-1.5 py-1 text-white backdrop-blur-sm z-20 shadow-sm"
            >
              <button
                type="button"
                aria-label="Photo précédente"
                onClick={(e) => {
                  e.stopPropagation();
                  setMobilePhotoIndex((prev) => (prev - 1 + gallerySlotCount) % gallerySlotCount);
                }}
                className="w-7 h-7 rounded-full hover:bg-white/20 flex items-center justify-center transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span
                data-testid="mobile-gallery-counter"
                className="px-1.5 text-[11px] font-bold whitespace-nowrap"
              >
                {activeMobilePhotoIndex + 1} / {gallerySlotCount} photos
              </span>
              <button
                type="button"
                aria-label="Photo suivante"
                onClick={(e) => {
                  e.stopPropagation();
                  setMobilePhotoIndex((prev) => (prev + 1) % gallerySlotCount);
                }}
                className="w-7 h-7 rounded-full hover:bg-white/20 flex items-center justify-center transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>

        {gallerySlotCount > 1 && (
          <div
            className={`hidden md:grid h-full min-h-0 ${
              gallerySlotCount === 2 ? 'md:col-span-1' : 'md:col-span-2'
            } ${desktopSecondaryGridClass} gap-3`}
          >
            {Array.from({ length: Math.min(4, gallerySlotCount - 1) }, (_, offset) => {
              const idx = offset + 1;
              const isFourPhotosWideTop = secondaryCount === 3 && offset === 0;
              const isLastTileWithOverflow = offset === 3 && gallerySlotCount > 5;
              const hiddenPhotosCount = gallerySlotCount - 5;

              return (
                <div
                  key={idx}
                  data-testid={`gallery-tile-${idx}`}
                  className={`${
                    isFourPhotosWideTop ? 'col-span-2' : ''
                  } h-full min-h-0 cursor-pointer overflow-hidden relative group bg-sand-200`}
                  onClick={() => {
                    setSelectedPhotoIndex(idx);
                    setIsLightboxOpen(true);
                  }}
                >
                  <PropertyVisual
                    mode="gallery"
                    rawUrl={rawPhotos[idx]?.url}
                    listingId={property.id}
                    type={property.type}
                    title={`${property.titre} — Photo ${idx + 1}`}
                    quartier={property.quartier}
                    fallbackUrl={galleryFallbacks[idx]}
                    slotKey={`gallery-${idx}`}
                    showIllustrativeBadge={!isLastTileWithOverflow}
                    loading="lazy"
                    width={360}
                    height={185}
                    className="w-full h-full"
                    imageClassName="group-hover:scale-105 transition-transform duration-300"
                    onStageChange={(_stage, url) => handleGalleryStageChange(`${property.id}:${idx}`, url)}
                  />
                  {isLastTileWithOverflow && (
                    <div
                      data-testid="gallery-more-overlay"
                      className="absolute inset-0 bg-charcoal-900/55 group-hover:bg-charcoal-900/65 transition-colors flex flex-col items-center justify-center text-white p-2 text-center z-10"
                    >
                      <Images className="w-5 h-5 mb-1 opacity-95" aria-hidden="true" />
                      <span className="font-display font-extrabold text-base leading-tight">
                        +{hiddenPhotosCount} {hiddenPhotosCount === 1 ? 'photo' : 'photos'}
                      </span>
                      <span className="text-[11px] text-sand-100 mt-0.5 font-medium">
                        Voir les {gallerySlotCount} photos
                      </span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* DETAILS GRID (Single Booking Card order-1 on <lg, sticky right column on lg+) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6 lg:gap-8 items-start">
        
        {/* LEFT COLUMN */}
        <div className="lg:col-span-2 order-2 lg:order-1 min-w-0 space-y-6 sm:space-y-8">
          
          {/* Key Specifications (Dynamic items without N/A or orphan mobile tile) */}
          <div
            data-testid="specs-grid"
            className={`p-4 sm:p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card grid grid-cols-2 ${specSmColsClass} gap-4 text-center`}
          >
            {specItems.map((spec, specIdx) => {
              const isOrphanLast = isOddSpecCount && specIdx === specItems.length - 1;
              return (
                <div
                  key={spec.label}
                  className={
                    isOrphanLast
                      ? 'col-span-2 sm:col-span-1 pt-2 sm:pt-0 border-t sm:border-t-0 border-sand-200'
                      : ''
                  }
                >
                  <p className="text-xs text-charcoal-500 font-medium">{spec.label}</p>
                  <p className="font-display font-bold text-lg text-charcoal-900">{spec.value}</p>
                </div>
              );
            })}
          </div>

          {/* Description */}
          <div className="space-y-3">
            <h3 className="font-display font-bold text-lg text-charcoal-900">Description du bien</h3>
            <p className="text-sm text-charcoal-700 leading-relaxed whitespace-pre-line">{property.description}</p>
          </div>

          {/* Équipements (F5) */}
          {property.equipements && property.equipements.length > 0 && (
            <div className="space-y-3 p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card">
              <h3 className="font-display font-bold text-base text-charcoal-900">Équipements & Confort</h3>
              <div className="flex flex-wrap gap-2">
                {property.equipements.map((eq) => (
                  <EquipementBadge key={eq} code={eq} />
                ))}
              </div>
            </div>
          )}

          {/* Proximités (F5) */}
          {property.proximites && property.proximites.length > 0 && (
            <div className="space-y-3 p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card">
              <h3 className="font-display font-bold text-base text-charcoal-900">Proximités & Commodités</h3>
              <div className="flex flex-wrap gap-2">
                {property.proximites.map((px) => (
                  <ProximiteBadge key={px} code={px} />
                ))}
              </div>
            </div>
          )}

          {/* Location Map */}
          {property.latitude && property.longitude && (
            <div className="space-y-3 border-t border-argile-200 pt-6">
              <h3 className="font-display font-bold text-lg text-charcoal-900">Emplacement & Carte</h3>
              <DeferredMap property={property} />
            </div>
          )}

          {/* Landlord Info & Calculated Owner Reputation */}
          {property.auteur && (
            <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card space-y-4">
              <h3 className="font-display font-bold text-base text-charcoal-900">
                {(property.auteurRole === 'AGENT' || property.auteur?.role === 'AGENT')
                  ? 'Publié par un agent immobilier'
                  : 'Proposé par un propriétaire identifié'}
              </h3>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-4 min-w-0">
                  <Avatar src={property.auteur.photoProfilUrl} name={`${property.auteur.prenom} ${property.auteur.nom}`} size="lg" isVerified />
                  <div className="space-y-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="font-display font-bold text-base text-charcoal-900">{property.auteur.prenom} {property.auteur.nom}</h4>
                      <Badge variant="sahel">
                        {(property.auteurRole === 'AGENT' || property.auteur?.role === 'AGENT') ? 'Agent immobilier' : property.auteur.role}
                      </Badge>
                    </div>
                    <p className="text-xs text-charcoal-600">Tel: {property.auteur.telephone}</p>
                  </div>
                </div>

                {/* Landlord Reputation Badge */}
                <div className="sm:text-right bg-sand-100 p-3 rounded-xl border border-argile-300 shrink-0">
                  <p className="text-[11px] text-charcoal-500 font-semibold uppercase tracking-wider">Réputation Propriétaire</p>
                  {landlordReputation?.noteMoyenne ? (
                    <div className="flex items-center sm:justify-end gap-1.5 mt-0.5">
                      <Star className="w-4 h-4 fill-amber-400 text-amber-500" />
                      <span className="font-display font-extrabold text-base text-charcoal-900">
                        {landlordReputation.noteMoyenne.toFixed(1)} / 5
                      </span>
                      <span className="text-xs text-charcoal-500 font-medium">
                        ({landlordReputation.nombreAvis} avis)
                      </span>
                    </div>
                  ) : (
                    <p className="text-xs text-charcoal-500 mt-0.5">Aucun avis encore</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Verification Documents Management (F14) - Restricted to Owner or Admin */}
          {utilisateur && (utilisateur.role === 'ADMIN' || utilisateur.id === property.auteur?.id) && (
            <div className="p-6 rounded-2xl bg-white border border-argile-200 shadow-warm-card space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-argile-200 pb-3">
                <div>
                  <h3 className="font-display font-bold text-base text-charcoal-900 flex items-center gap-2">
                    <FileCheck className="w-5 h-5 text-terracotta-600" />
                    <span>Pièces justificatives & Statuts de vérification (F14)</span>
                  </h3>
                  <p className="text-xs text-charcoal-500">
                    Espace réservé au propriétaire et aux administrateurs pour la certification du titre de propriété.
                  </p>
                </div>
                {property.statutVerification === 'VERIFIEE' ? (
                  <Badge variant="success" className="flex items-center gap-1 shrink-0">
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span>Annonce Vérifiée</span>
                  </Badge>
                ) : (
                  <Badge variant="warning" className="shrink-0">Vérification en cours</Badge>
                )}
              </div>

              {/* Existing documents list with status and rejection reason */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-charcoal-700 uppercase tracking-wider">
                  Documents soumis ({property.documentsVerification?.length ?? 0})
                </h4>
                {(!property.documentsVerification || property.documentsVerification.length === 0) ? (
                  <p className="text-xs text-charcoal-500 italic bg-sand-50 p-3 rounded-xl border border-sand-200">
                    Aucun document justificatif n'a encore été soumis pour cette annonce.
                  </p>
                ) : (
                  <div className="space-y-2">
                    {property.documentsVerification.map((doc) => (
                      <div
                        key={doc.id}
                        className="p-3 rounded-xl border border-sand-300 bg-sand-50 flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <FileText className="w-4 h-4 text-terracotta-600 shrink-0" />
                            <span className="font-semibold text-xs text-charcoal-900">
                              {doc.typeDocument === 'TITRE_FONCIER' && 'Titre Foncier / Attestation de propriété'}
                              {doc.typeDocument === 'BAIL_PRECEDENT' && 'Bail précédent / Facture Senelec'}
                              {doc.typeDocument === 'PIECE_IDENTITE' && "Photo propriétaire avec pièce d'identité"}
                            </span>
                            {doc.statut === 'VALIDE' && <Badge variant="success">Validé</Badge>}
                            {doc.statut === 'EN_ATTENTE' && <Badge variant="warning">En attente</Badge>}
                            {doc.statut === 'REJETE' && <Badge variant="rose">Rejeté</Badge>}
                          </div>
                          {doc.statut === 'REJETE' && doc.motifRejet && (
                            <p className="text-xs text-rose-700 bg-rose-50 p-2 rounded-lg border border-rose-200 mt-1">
                              <strong>Motif du rejet :</strong> {doc.motifRejet}
                            </p>
                          )}
                          <p className="text-[10px] text-charcoal-500">
                            Soumis le {new Date(doc.dateCreation).toLocaleDateString('fr-FR')}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Add document upload form */}
              <form onSubmit={handleUploadDocument} className="space-y-3 pt-3 border-t border-argile-200">
                <h4 className="text-xs font-bold text-charcoal-700 uppercase tracking-wider">
                  Ajouter des pièces justificatives supplémentaires
                </h4>
                <div className="p-2.5 bg-sahel-50 rounded-lg border border-sahel-200 text-[11px] text-sahel-800">
                  <strong>Formats acceptés :</strong> Documents PDF ou images (JPG, JPEG, PNG, WEBP, PDF — Max 10 Mo par document).
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                  <div>
                    <label className="block text-xs font-semibold text-charcoal-700 mb-1">
                      Type de pièce
                    </label>
                    <select
                      value={docTypeToUpload}
                      onChange={(e) =>
                        setDocTypeToUpload(
                          e.target.value as 'TITRE_FONCIER' | 'BAIL_PRECEDENT' | 'PIECE_IDENTITE'
                        )
                      }
                      className="w-full px-3 py-2 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
                    >
                      <option value="TITRE_FONCIER">Titre Foncier / Attestation de propriété</option>
                      <option value="BAIL_PRECEDENT">Bail précédent / Facture Senelec</option>
                      <option value="PIECE_IDENTITE">Photo du propriétaire avec sa pièce d'identité</option>
                    </select>
                  </div>
                  <div>
                    <label className="border border-sand-300 hover:border-terracotta-500 rounded-xl px-3 py-2 flex items-center justify-between cursor-pointer text-xs font-semibold text-sahel-800 bg-sand-50 transition-colors">
                      <span className="truncate">
                        {docFilesToUpload.length > 0
                          ? `${docFilesToUpload.length} fichier(s) sélectionné(s)`
                          : 'Sélectionner des fichiers (PDF ou images)'}
                      </span>
                      <Upload className="w-4 h-4 text-terracotta-500 shrink-0 ml-2" />
                      <input
                        type="file"
                        accept=".jpg,.jpeg,.png,.webp,.pdf,image/jpeg,image/png,image/webp,application/pdf"
                        multiple
                        onChange={(e) => {
                          if (e.target.files) {
                            setDocFilesToUpload(Array.from(e.target.files));
                          }
                        }}
                        className="hidden"
                      />
                    </label>
                  </div>
                </div>
                <div className="flex justify-end pt-2">
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    isLoading={isUploadingDoc}
                    disabled={docFilesToUpload.length === 0}
                  >
                    Envoyer pour vérification
                  </Button>
                </div>
              </form>
            </div>
          )}

          {/* REVIEWS SECTION */}
          <div className="space-y-6 border-t border-argile-200 pt-6">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-display font-bold text-xl text-charcoal-900">
                  Avis et Évaluations ({avisSummary?.nombreAvis ?? 0})
                </h3>
                <p className="text-xs text-charcoal-500">
                  Avis vérifiés déposés exclusivement par des locataires ayant effectué une visite.
                </p>
              </div>
            </div>

            {/* Leave a review form if LOCATAIRE with EFFECTUE visit */}
            {utilisateur?.role === 'LOCATAIRE' && hasEffectueVisit && (
              <form onSubmit={handleSoumettreAvis} className="p-5 rounded-2xl bg-sahel-50/50 border border-sahel-200 space-y-4">
                <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
                  <UserCheck className="w-4 h-4 text-sahel-700" />
                  Laisser un avis suite à votre visite
                </h4>
                
                <div className="flex items-center gap-2">
                  <span className="text-xs text-charcoal-700 font-medium">Votre note :</span>
                  <div className="flex items-center gap-1">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => setNewRating(star)}
                        className="p-1 transition-transform hover:scale-110 focus:outline-none"
                      >
                        <Star
                          className={`w-5 h-5 ${
                            star <= newRating ? 'fill-amber-400 text-amber-500' : 'text-argile-300'
                          }`}
                        />
                      </button>
                    ))}
                  </div>
                </div>

                <textarea
                  rows={3}
                  value={newComment}
                  onChange={(e) => setNewComment(e.target.value)}
                  placeholder="Partagez votre retour d'expérience sur ce logement et le propriétaire..."
                  className="w-full text-xs p-3 rounded-xl border border-argile-300 focus:outline-none focus:ring-2 focus:ring-sahel-500/30 bg-white resize-none"
                />

                <Button variant="primary" size="sm" type="submit" isLoading={isSubmittingAvis}>
                  Publier mon avis
                </Button>
              </form>
            )}

            {/* Reviews List */}
            {avisSummary?.avis && avisSummary.avis.length > 0 ? (
              <div className="space-y-3">
                {avisSummary.avis.map((av) => (
                  <div key={av.id} className="p-4 bg-white rounded-2xl border border-argile-200 shadow-sm space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-display font-bold text-xs text-charcoal-900">
                          {av.auteurPrenom} {av.auteurNom}
                        </span>
                        <div className="flex items-center gap-0.5">
                          {[1, 2, 3, 4, 5].map((s) => (
                            <Star
                              key={s}
                              className={`w-3 h-3 ${s <= av.note ? 'fill-amber-400 text-amber-500' : 'text-argile-200'}`}
                            />
                          ))}
                        </div>
                      </div>
                      <span className="text-[10px] text-charcoal-400">
                        {new Date(av.dateCreation).toLocaleDateString('fr-FR')}
                      </span>
                    </div>

                    {av.commentaire && (
                      <p className="text-xs text-charcoal-700 leading-relaxed">
                        "{av.commentaire}"
                      </p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-charcoal-500 italic p-4 bg-white rounded-2xl border border-argile-200 text-center">
                Aucun commentaire n'a encore été déposé pour cette annonce.
              </p>
            )}
          </div>

        </div>

        {/* RIGHT COLUMN: STICKY BOOKING / TRANSACTION CARD (Single price DOM instance across all viewports) */}
        <div id="booking-card" className="lg:col-span-1 order-1 lg:order-2 scroll-mt-24">
          <div className="lg:sticky lg:top-24 space-y-4">
            
            <Card className="border-terracotta-200 shadow-warm-hover">
              <CardBody className="p-3.5 sm:p-4 space-y-2.5 md:grid md:grid-cols-12 md:gap-5 md:space-y-0 md:items-start lg:block lg:space-y-3">
                
                {/* Price Display & Contextual Reassurance / Notice */}
                <div className="md:col-span-6 space-y-2.5 pr-12 xs:pr-0">
                  <div>
                    <span className="text-xs font-semibold text-charcoal-500 uppercase tracking-wider">
                      {property.modeTransaction === 'LOCATION' ? 'Loyer Mensuel' : 'Prix de Vente'}
                    </span>
                    <div className="flex flex-wrap items-baseline gap-1 mt-0.5">
                      <span className="font-display font-extrabold text-xl sm:text-2xl text-terracotta-600 break-words">
                        {property.prix.toLocaleString('fr-FR')} FCFA
                      </span>
                      {property.modeTransaction === 'LOCATION' && (
                        <span className="text-xs text-charcoal-500">/ mois</span>
                      )}
                    </div>
                    <p className="text-[11px] text-sahel-800 font-semibold mt-0.5 flex items-center gap-1 leading-snug">
                      <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0" />
                      <span>
                        {property.modeTransaction === 'VENTE'
                          ? 'Acompte de réservation sécurisé sous séquestre'
                          : 'Caution 100% sécurisée sous séquestre Wave'}
                      </span>
                    </p>
                  </div>

                  {/* Publication Status Notice when unavailable */}
                  {!isAvailable && statusMeta.notice && (
                    <div
                      data-testid="booking-status-notice"
                      className={`p-3 rounded-xl border text-xs font-medium leading-relaxed ${
                        publicationStatus === 'RESERVEE'
                          ? 'bg-ocher-50 border-ocher-300 text-ocher-900'
                          : 'bg-rose-50 border-rose-200 text-rose-900'
                      }`}
                    >
                      <p className="font-bold flex items-center gap-1.5 mb-1">
                        <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                        <span>Statut : {statusMeta.label}</span>
                      </p>
                      <p>{statusMeta.notice}</p>
                    </div>
                  )}

                  {/* Tablet/Desktop guarantees paired under Price on md */}
                  <div className="hidden md:block lg:hidden text-[11px] text-charcoal-600 space-y-1 pt-2 border-t border-argile-200">
                    <p className="flex items-start gap-1.5">
                      <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                      <span>Aucun frais de visite préalable</span>
                    </p>
                    {property.modeTransaction === 'VENTE' ? (
                      <>
                        <p className="flex items-start gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                          <span>Acompte de réservation protégé sous séquestre avant signature</span>
                        </p>
                        <p className="flex items-start gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                          <span>Engagement de réservation avec double signature numérique</span>
                        </p>
                      </>
                    ) : (
                      <>
                        <p className="flex items-start gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                          <span>Séquestre bloqué via Wave sandbox</span>
                        </p>
                        <p className="flex items-start gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                          <span>Contrat de bail numérique avec double signature électronique</span>
                        </p>
                      </>
                    )}
                  </div>
                </div>

                {/* Primary Action Buttons */}
                <div className="md:col-span-6 space-y-2 pt-0.5 md:pt-0">
                  
                  {/* Case 1: Transaction already exists (always accessible to involved user) */}
                  {existingTx ? (
                    <div className="space-y-2 p-3 rounded-xl bg-emerald-50 border border-emerald-200">
                      <p className="text-xs font-bold text-emerald-900 flex items-center gap-1.5">
                        <FileCheck className="w-4 h-4 text-emerald-600" />
                        Dossier de transaction en cours
                      </p>
                      <Button
                        variant="primary"
                        size="md"
                        className="w-full justify-center bg-emerald-700 hover:bg-emerald-800 text-white"
                        onClick={() => {
                          setSelectedTx(existingTx);
                          setIsTxModalOpen(true);
                        }}
                      >
                        {property.modeTransaction === 'VENTE' ? "Gérer l'acompte & Signer la réservation" : "Gérer la caution & Signer le bail"}
                      </Button>
                    </div>
                  ) : !isAvailable ? (
                    /* Case 2: Property not DISPONIBLE -> block booking/visit */
                    <Button
                      variant="outline"
                      size="md"
                      disabled
                      data-testid="booking-unavailable-cta"
                      className="w-full justify-center opacity-75 cursor-not-allowed border-argile-300 text-charcoal-600 bg-sand-100"
                    >
                      {statusMeta.label} — Réservation indisponible
                    </Button>
                  ) : hasEffectueVisit ? (
                    /* Case 3: Visit is EFFECTUE -> Option to initiate transaction */
                    <div className="space-y-2 p-3 rounded-xl bg-sand-50 border border-terracotta-200">
                      <p className="text-xs font-bold text-sahel-900 flex items-center gap-1.5">
                        <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                        Visite effectuée avec succès !
                      </p>
                      <p className="text-[11px] text-sahel-600">
                        {property.modeTransaction === 'VENTE'
                          ? "Vous pouvez dès maintenant réserver ce bien et consigner l'acompte de réservation (10 % du prix) sous séquestre sécurisé."
                          : "Vous pouvez dès maintenant réserver ce bien et consigner les fonds (caution + 1er loyer + frais) sous séquestre sécurisé."}
                      </p>
                      <Button
                        variant="primary"
                        size="md"
                        className="w-full justify-center"
                        isLoading={isInitiatingTx}
                        onClick={handleInitierTransaction}
                      >
                        {property.modeTransaction === 'VENTE' ? "Initier la réservation d'achat" : "Initier la location"}
                      </Button>
                    </div>
                  ) : (
                    /* Case 4: Available -> Request visit */
                    <Button
                      variant="primary"
                      size="md"
                      className="w-full justify-center"
                      onClick={handleDemanderVisiteClick}
                    >
                      Demander une visite du bien
                    </Button>
                  )}

                  {/* Button Contacter le propriétaire */}
                  {(!utilisateur || (utilisateur.id !== property.auteur?.id)) && (
                    <Button
                      variant="outline"
                      size="md"
                      disabled={!isAvailable && !existingTx}
                      className={`w-full justify-center ${
                        !isAvailable && !existingTx
                          ? 'opacity-60 cursor-not-allowed border-argile-300 text-charcoal-500'
                          : 'border-ocher-600 text-ocher-800 hover:bg-ocher-50'
                      }`}
                      isLoading={isInitiatingChat}
                      onClick={handleContacterProprietaire}
                      leftIcon={
                        <MessageSquare
                          className={`w-4 h-4 ${
                            !isAvailable && !existingTx ? 'text-charcoal-400' : 'text-ocher-700'
                          }`}
                        />
                      }
                    >
                      Contacter le propriétaire
                    </Button>
                  )}

                </div>

                <div className="md:hidden lg:block text-[11px] text-charcoal-600 space-y-1 pt-2 border-t border-argile-200">
                  <p className="flex items-start gap-1.5">
                    <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                    <span>Aucun frais de visite préalable</span>
                  </p>
                  {property.modeTransaction === 'VENTE' ? (
                    <>
                      <p className="flex items-start gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                        <span>Acompte de réservation protégé sous séquestre avant signature</span>
                      </p>
                      <p className="flex items-start gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                        <span>Engagement de réservation avec double signature numérique</span>
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="flex items-start gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                        <span>Séquestre bloqué via Wave sandbox</span>
                      </p>
                      <p className="flex items-start gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5 text-sahel-600 shrink-0 mt-0.5" aria-hidden="true" />
                        <span>Contrat de bail numérique avec double signature électronique</span>
                      </p>
                    </>
                  )}
                </div>

              </CardBody>
            </Card>

          </div>
        </div>

      </div>

      {/* MODAL: VISIT REQUEST FORM */}
      <Modal
        isOpen={isVisitModalOpen}
        onClose={() => setIsVisitModalOpen(false)}
        title="Planifier une Visite du Logement"
      >
        <form onSubmit={handleConfirmVisit} className="space-y-4">
          <p className="text-xs text-charcoal-600">
            Sélectionnez la date et l'heure souhaitées pour visiter <strong>{property.titre}</strong>.
          </p>
          
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-charcoal-800 flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5 text-terracotta-500" /> Date souhaitée
            </label>
            <input
              type="date"
              required
              min={new Date().toISOString().split('T')[0]}
              value={visitDate}
              onChange={(e) => setVisitDate(e.target.value)}
              className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900 bg-sand-50"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-charcoal-800 flex items-center gap-1">
              <Clock className="w-3.5 h-3.5 text-terracotta-500" /> Heure de visite
            </label>
            <input
              type="time"
              required
              value={visitTime}
              onChange={(e) => setVisitTime(e.target.value)}
              className="w-full p-3 rounded-xl border border-argile-300 text-xs text-charcoal-900 bg-sand-50"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            size="md"
            className="w-full justify-center"
            disabled={isSubmittingVisit}
          >
            {isSubmittingVisit ? 'Envoi en cours...' : 'Confirmer la demande de visite'}
          </Button>
        </form>
      </Modal>

      {/* MODAL: 360 VIRTUAL TOUR */}
      {property.lienVisite360 && (
        <Modal
          isOpen={is360ModalOpen}
          onClose={() => setIs360ModalOpen(false)}
          title="Visite Virtuelle 360° du Logement"
        >
          <div className="space-y-4">
            <div className="aspect-video w-full bg-charcoal-900 rounded-2xl overflow-hidden border border-argile-300 flex items-center justify-center">
              <iframe
                src={property.lienVisite360}
                title="Visite 360"
                className="w-full h-full border-0"
                allowFullScreen
              />
            </div>
            <div className="flex justify-end">
              <a
                href={property.lienVisite360}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs font-semibold text-terracotta-600 hover:text-terracotta-700 flex items-center gap-1"
              >
                Ouvrir en plein écran dans un nouvel onglet &rarr;
              </a>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: SIGNALEMENT */}
      {isSignalementModalOpen && (
        <React.Suspense fallback={null}>
          <LazySignalementModal
            annonceId={property.id}
            annonceTitre={property.titre}
            isOpen={isSignalementModalOpen}
            onClose={() => setIsSignalementModalOpen(false)}
          />
        </React.Suspense>
      )}

      {/* MODAL: TRANSACTION DETAILS & LEASE SIGNATURE */}
      {isTxModalOpen && (
        <React.Suspense fallback={null}>
          <LazyTransactionDetailsModal
            isOpen={isTxModalOpen}
            onClose={() => {
              setIsTxModalOpen(false);
              setSelectedTx(null);
            }}
            transaction={
              (selectedTx && userTransactions?.find((t) => t.id === selectedTx.id)) ||
              selectedTx
            }
            onRefresh={() => {
              queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
              queryClient.invalidateQueries({ queryKey: ['annonce', id] });
            }}
          />
        </React.Suspense>
      )}

      {/* LIGHTBOX */}
      {isLightboxOpen && lightboxSlides.length > 0 && (
        <React.Suspense fallback={null}>
          <LazyLightbox
            open={isLightboxOpen}
            close={() => setIsLightboxOpen(false)}
            index={Math.min(selectedPhotoIndex, lightboxSlides.length - 1)}
            slides={lightboxSlides}
            on={{
              view: ({ index }) => {
                setSelectedPhotoIndex(index);
                if (typeof window !== 'undefined' && window.innerWidth < 768) {
                  setMobilePhotoIndex(index);
                }
              },
            }}
          />
        </React.Suspense>
      )}

      {/* CHAT DRAWER */}
      {isChatOpen && (
        <React.Suspense fallback={null}>
          <LazyChatDrawer
            isOpen={isChatOpen}
            onClose={() => setIsChatOpen(false)}
            conversation={activeConversation}
          />
        </React.Suspense>
      )}

    </div>
  );
};
