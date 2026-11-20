import React from 'react';
import type { LucideIcon } from 'lucide-react';
import { Skeleton } from '../ui/Skeleton';
import { cn } from '../../lib/cn';
import type { DashboardTabId } from '../../lib/dashboardModel';

export interface DashboardMetric {
  id: string;
  label: string;
  /** `null` = donnée indisponible : l'indicateur n'est pas affiché. `undefined` = en cours de chargement. */
  value: number | string | null | undefined;
  hint?: string;
  icon: LucideIcon;
  tab?: DashboardTabId;
  tone?: 'default' | 'attention';
}

interface DashboardSummaryProps {
  metrics: DashboardMetric[];
  onSelectTab: (tab: DashboardTabId) => void;
}

export const DashboardSummary: React.FC<DashboardSummaryProps> = ({ metrics, onSelectTab }) => {
  // Un indicateur sans donnée réelle n'est jamais affiché (pas de chiffre fictif).
  const visible = metrics.filter((metric) => metric.value !== null);
  if (visible.length === 0) return null;

  return (
    <section aria-label="Indicateurs clés" className="grid grid-cols-2 xl:grid-cols-4 gap-2.5 sm:gap-3">
      {visible.map((metric) => {
        const Icon = metric.icon;
        const isLoading = metric.value === undefined;
        const content = (
          <>
            <span
              className={cn(
                'w-9 h-9 rounded-xl flex items-center justify-center shrink-0',
                metric.tone === 'attention' ? 'bg-terracotta-100 text-terracotta-700' : 'bg-sahel-100 text-sahel-800'
              )}
            >
              <Icon className="w-4 h-4" aria-hidden="true" />
            </span>
            <span className="min-w-0 text-left">
              {isLoading ? (
                <Skeleton className="h-6 w-10" aria-hidden="true" />
              ) : (
                <span className="block font-display font-bold text-xl leading-tight text-sahel-900 truncate">
                  {metric.value}
                </span>
              )}
              <span className="block text-[11px] sm:text-xs font-semibold text-sahel-700 leading-snug">{metric.label}</span>
              {metric.hint && !isLoading && (
                <span className="block text-[10px] sm:text-[11px] text-sahel-500 leading-snug">{metric.hint}</span>
              )}
            </span>
          </>
        );
        const className =
          'flex items-center gap-3 p-3 rounded-2xl bg-white border border-sand-300 shadow-warm-card min-w-0';
        return metric.tab ? (
          <button
            key={metric.id}
            type="button"
            onClick={() => onSelectTab(metric.tab as DashboardTabId)}
            className={cn(className, 'hover:border-terracotta-300 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-400')}
          >
            {content}
          </button>
        ) : (
          <div key={metric.id} className={className}>
            {content}
          </div>
        );
      })}
    </section>
  );
};
