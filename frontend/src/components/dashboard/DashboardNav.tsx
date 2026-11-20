import React from 'react';
import { Calendar, Home, Lock, MessageSquare, Smartphone, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import type { DashboardTabId } from '../../lib/dashboardModel';

export interface DashboardNavItem {
  id: DashboardTabId;
  label: string;
  icon: LucideIcon;
  count?: number | null;
  /** Compteur d'alerte (ex. messages non lus) affiché en ocre. */
  alert?: boolean;
}

interface DashboardNavProps {
  items: DashboardNavItem[];
  activeTab: DashboardTabId;
  onSelect: (tab: DashboardTabId) => void;
}

export const DASHBOARD_TAB_ICONS: Record<DashboardTabId, LucideIcon> = {
  annonces: Home,
  visites: Calendar,
  transactions: Lock,
  messages: MessageSquare,
  sms: Smartphone,
};

/**
 * Navigation unique (un seul DOM) : onglets défilables sous 1024px, colonne latérale au-delà.
 * Les boutons exposent `aria-current` ; aucun doublon mobile/desktop.
 */
export const DashboardNav: React.FC<DashboardNavProps> = ({ items, activeTab, onSelect }) => (
  <nav
    aria-label="Sections du tableau de bord"
    className="-mx-3 px-3 sm:mx-0 sm:px-0 flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0 lg:gap-1.5 [scrollbar-width:thin]"
  >
    {items.map((item) => {
      const Icon = item.icon;
      const isActive = item.id === activeTab;
      const hasCount = typeof item.count === 'number' && item.count > 0;
      return (
        <button
          key={item.id}
          type="button"
          onClick={() => onSelect(item.id)}
          aria-current={isActive ? 'page' : undefined}
          className={cn(
            'shrink-0 min-h-[44px] flex items-center justify-between gap-2.5 px-3.5 lg:px-4 rounded-xl text-xs sm:text-[13px] font-semibold transition-colors whitespace-nowrap',
            'focus:outline-none focus-visible:ring-2 focus-visible:ring-ocher-400 focus-visible:ring-offset-2 focus-visible:ring-offset-sahel-900',
            isActive
              ? 'bg-terracotta-500 text-white shadow-terracotta-glow'
              : 'bg-sahel-800/80 lg:bg-transparent text-sahel-100 lg:text-sahel-200 hover:bg-sahel-800'
          )}
        >
          <span className="flex items-center gap-2.5">
            <Icon className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>{item.label}</span>
          </span>
          {hasCount && (
            <span
              className={cn(
                'min-w-[1.5rem] px-1.5 py-0.5 rounded-full text-center text-[10px] font-bold',
                item.alert ? 'bg-ocher-500 text-charcoal-900' : 'bg-black/20 text-white'
              )}
              aria-label={`${item.count} élément${(item.count ?? 0) > 1 ? 's' : ''}`}
            >
              {item.count}
            </span>
          )}
        </button>
      );
    })}
  </nav>
);
