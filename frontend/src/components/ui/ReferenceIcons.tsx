import React from 'react';
import { getEquipementMeta, getProximiteMeta } from '../../lib/referenceMeta';

export const EquipementBadge: React.FC<{ code: string; className?: string }> = ({
  code,
  className = '',
}) => {
  const meta = getEquipementMeta(code);
  const Icon = meta.icon;
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-sand-100 border border-sand-300 text-xs font-medium text-charcoal-700 ${className}`}
    >
      <Icon className="w-3.5 h-3.5 text-terracotta-500 shrink-0" />
      <span>{meta.label}</span>
    </span>
  );
};

export const ProximiteBadge: React.FC<{ code: string; className?: string }> = ({
  code,
  className = '',
}) => {
  const meta = getProximiteMeta(code);
  const Icon = meta.icon;
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-clay-50 border border-clay-200 text-xs font-medium text-sahel-800 ${className}`}
    >
      <Icon className="w-3.5 h-3.5 text-sahel-600 shrink-0" />
      <span>{meta.label}</span>
    </span>
  );
};
