import React from 'react';
import { Toaster as SonnerToaster, toast } from 'sonner';

export const Toaster: React.FC = () => {
  return (
    <SonnerToaster
      position="top-right"
      toastOptions={{
        style: {
          background: '#FDFBF7',
          color: '#1E232A',
          border: '1px solid #E5DEC9',
          borderRadius: '1rem',
          fontFamily: 'Plus Jakarta Sans, sans-serif',
          fontSize: '0.875rem',
          boxShadow: '0 10px 30px -5px rgba(30, 35, 42, 0.1)',
        },
      }}
    />
  );
};

export { toast };
