import React from 'react';
import { Heart } from 'lucide-react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, useReducedMotion } from 'framer-motion';
import { apiFetch } from '../../services/api';
import { useAuthStore } from '../../store/useAuthStore';
import { toast } from 'sonner';
import { cn } from '../../lib/cn';

interface FavoriteButtonProps {
  annonceId: string;
  estFavori: boolean;
  size?: 'sm' | 'md';
  className?: string;
}

export const FavoriteButton: React.FC<FavoriteButtonProps> = ({
  annonceId,
  estFavori,
  size = 'sm',
  className,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const shouldReduceMotion = useReducedMotion();
  const { utilisateur } = useAuthStore();

  const toggleMutation = useMutation({
    mutationFn: async () => {
      if (estFavori) {
        await apiFetch(`/api/v1/annonces/${annonceId}/favoris`, { method: 'DELETE' });
      } else {
        await apiFetch(`/api/v1/annonces/${annonceId}/favoris`, { method: 'POST' });
      }
    },
    onMutate: async () => {
      // Cancel outgoing refetches
      await queryClient.cancelQueries({ queryKey: ['annonces'] });
      await queryClient.cancelQueries({ queryKey: ['annonce', annonceId] });
      await queryClient.cancelQueries({ queryKey: ['favoris'] });

      // Snapshot previous data for rollback
      const previousAnnonce = queryClient.getQueryData(['annonce', annonceId]);
      const previousAnnonces = queryClient.getQueriesData({ queryKey: ['annonces'] });

      // Optimistically update the single annonce query
      queryClient.setQueryData(['annonce', annonceId], (old: any) => {
        if (!old) return old;
        return { ...old, estFavori: !estFavori };
      });

      // Optimistically update annonces list queries
      queryClient.setQueriesData({ queryKey: ['annonces'] }, (old: any) => {
        if (!old?.content) return old;
        return {
          ...old,
          content: old.content.map((a: any) =>
            a.id === annonceId ? { ...a, estFavori: !estFavori } : a
          ),
        };
      });

      return { previousAnnonce, previousAnnonces };
    },
    onError: (_err, _vars, context) => {
      // Rollback on error
      if (context?.previousAnnonce) {
        queryClient.setQueryData(['annonce', annonceId], context.previousAnnonce);
      }
      if (context?.previousAnnonces) {
        context.previousAnnonces.forEach(([queryKey, data]: [any, any]) => {
          queryClient.setQueryData(queryKey, data);
        });
      }
      toast.error(estFavori ? 'Impossible de retirer des favoris' : 'Impossible d\'ajouter aux favoris');
    },
    onSettled: () => {
      // Refetch to ensure consistency
      queryClient.invalidateQueries({ queryKey: ['annonces'] });
      queryClient.invalidateQueries({ queryKey: ['annonce', annonceId] });
      queryClient.invalidateQueries({ queryKey: ['favoris'] });
      queryClient.invalidateQueries({ queryKey: ['favoris-count'] });
    },
  });

  const handleClick = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!utilisateur) {
      toast.info('Connectez-vous pour sauvegarder vos annonces favorites');
      navigate('/connexion', { state: { from: location.pathname + location.search } });
      return;
    }

    toggleMutation.mutate();
  };

  const sizeClasses = size === 'md'
    ? 'w-10 h-10'
    : 'w-8 h-8';

  const iconSize = size === 'md' ? 'w-5 h-5' : 'w-4 h-4';

  return (
    <motion.button
      type="button"
      onClick={handleClick}
      disabled={toggleMutation.isPending}
      aria-label={estFavori ? 'Retirer des favoris' : 'Ajouter aux favoris'}
      whileTap={{ scale: shouldReduceMotion ? 1 : 0.85 }}
      animate={estFavori && !shouldReduceMotion ? { scale: [1, 1.25, 0.95, 1] } : { scale: 1 }}
      transition={{ type: 'spring', stiffness: 450, damping: 15 }}
      className={cn(
        'rounded-full flex items-center justify-center transition-colors duration-200',
        'focus:outline-none focus:ring-2 focus:ring-terracotta-500/40',
        estFavori
          ? 'bg-terracotta-500 text-white shadow-terracotta-glow hover:bg-terracotta-600'
          : 'bg-white/90 text-charcoal-500 shadow-sm hover:bg-white hover:text-terracotta-500 border border-argile-200',
        toggleMutation.isPending && 'opacity-60 cursor-not-allowed',
        sizeClasses,
        className
      )}
    >
      <Heart
        className={cn(
          iconSize,
          'transition-all duration-200',
          estFavori && 'fill-current'
        )}
      />
    </motion.button>
  );
};
