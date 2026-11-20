import React from 'react';
import { AlertTriangle, type LucideIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Skeleton } from '../ui/Skeleton';

export const DashboardListSkeleton: React.FC<{ rows?: number }> = ({ rows = 3 }) => (
  <div className="space-y-4" role="status" aria-live="polite" aria-label="Chargement en cours">
    {Array.from({ length: rows }).map((_, index) => (
      <Skeleton key={index} variant="card" className="h-28" />
    ))}
  </div>
);

interface DashboardErrorProps {
  title: string;
  description?: string;
  onRetry: () => void;
  isRetrying?: boolean;
}

export const DashboardError: React.FC<DashboardErrorProps> = ({ title, description, onRetry, isRetrying }) => (
  <Card className="p-6 sm:p-8 border border-rose-200 bg-rose-50/60">
    <div role="alert" className="flex flex-col sm:flex-row sm:items-center gap-4">
      <AlertTriangle className="w-8 h-8 text-rose-600 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <h3 className="font-display font-bold text-base text-rose-900">{title}</h3>
        <p className="text-xs text-rose-800 mt-1">
          {description ?? 'Vos données n’ont pas pu être chargées. Vérifiez votre connexion puis réessayez.'}
        </p>
      </div>
      <Button
        variant="outline"
        size="sm"
        onClick={onRetry}
        isLoading={isRetrying}
        className="min-h-[44px] shrink-0 w-full sm:w-auto"
      >
        Réessayer
      </Button>
    </div>
  </Card>
);

interface DashboardEmptyProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: React.ReactNode;
}

export const DashboardEmpty: React.FC<DashboardEmptyProps> = ({ icon: Icon, title, description, action }) => (
  <Card className="p-8 sm:p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
    <Icon className="w-12 h-12 text-sahel-400 mx-auto" aria-hidden="true" />
    <h3 className="font-display font-bold text-lg text-sahel-900">{title}</h3>
    <p className="text-xs sm:text-sm text-sahel-600 max-w-md mx-auto">{description}</p>
    {action && <div className="flex justify-center">{action}</div>}
  </Card>
);

interface TabHeaderProps {
  title: string;
  description: string;
  actions?: React.ReactNode;
}

export const DashboardTabHeader: React.FC<TabHeaderProps> = ({ title, description, actions }) => (
  <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
    <div className="min-w-0">
      <h1 className="font-display font-bold text-xl sm:text-2xl text-sahel-900 break-words">{title}</h1>
      <p className="text-xs sm:text-sm text-sahel-600 mt-1">{description}</p>
    </div>
    {actions && <div className="shrink-0">{actions}</div>}
  </div>
);
