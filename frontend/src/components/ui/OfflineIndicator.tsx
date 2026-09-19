import React, { useState, useEffect } from 'react';
import { WifiOff } from 'lucide-react';

export const OfflineIndicator: React.FC = () => {
  const [isOffline, setIsOffline] = useState<boolean>(!navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  if (!isOffline) return null;

  return (
    <div className="fixed bottom-4 left-4 z-50 flex items-center gap-2 bg-charcoal-900/90 text-amber-300 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-amber-500/30 shadow-2xl text-xs font-semibold animate-bounce-subtle">
      <WifiOff className="w-4 h-4 text-amber-400 shrink-0" />
      <span>Mode hors ligne — Consultation des annonces en cache</span>
    </div>
  );
};
