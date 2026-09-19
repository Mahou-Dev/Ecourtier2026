import React from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import { cn } from '../../lib/cn';

export interface TabItem {
  id: string;
  label: string;
  icon?: React.ReactNode;
  badge?: string | number;
}

export interface TabsProps {
  tabs: TabItem[];
  activeTab: string;
  onChange: (id: string) => void;
  variant?: 'pills' | 'underline';
  className?: string;
}

export const Tabs: React.FC<TabsProps> = ({
  tabs,
  activeTab,
  onChange,
  variant = 'pills',
  className,
}) => {
  return (
    <RadixTabs.Root value={activeTab} onValueChange={onChange} className={cn('w-full', className)}>
      <RadixTabs.List className="flex items-center gap-1 overflow-x-auto no-scrollbar">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTab;
          return (
            <RadixTabs.Trigger
              key={tab.id}
              value={tab.id}
              className={cn(
                'px-4 py-2 text-xs font-semibold rounded-xl whitespace-nowrap transition-all duration-200 flex items-center gap-2 focus:outline-none focus:ring-2 focus:ring-terracotta-500',
                variant === 'pills' && isActive && 'bg-terracotta-500 text-white shadow-terracotta-glow',
                variant === 'pills' && !isActive && 'bg-sand-200 text-charcoal-700 hover:bg-sand-300',
                variant === 'underline' && isActive && 'border-b-2 border-terracotta-500 text-terracotta-600 rounded-none',
                variant === 'underline' && !isActive && 'text-charcoal-500 hover:text-charcoal-900 rounded-none'
              )}
            >
              {tab.icon}
              <span>{tab.label}</span>
              {tab.badge !== undefined && (
                <span className={cn('px-1.5 py-0.5 rounded-full text-[10px]', isActive ? 'bg-white/20 text-white' : 'bg-sand-300 text-charcoal-800')}>
                  {tab.badge}
                </span>
              )}
            </RadixTabs.Trigger>
          );
        })}
      </RadixTabs.List>
    </RadixTabs.Root>
  );
};
