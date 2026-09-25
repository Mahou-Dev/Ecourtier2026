import React from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
  className?: string;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  size = 'md',
  className,
}) => {
  const sizes = {
    sm: 'max-w-md',
    md: 'max-w-xl',
    lg: 'max-w-3xl',
    xl: 'max-w-5xl',
    full: 'max-w-[95vw] h-[90vh]',
  };

  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        {/* Backdrop Overlay with Backdrop Blur */}
        <Dialog.Overlay className="fixed inset-0 z-50 bg-charcoal-900/60 backdrop-blur-sm animate-in fade-in duration-200" />
        
        {/* Dialog Content Container with automatic Focus-Trap & Escape listener */}
        <Dialog.Content
          className={cn(
            'fixed left-[50%] top-[50%] z-50 w-full max-w-[calc(100vw-1.5rem)] max-h-[calc(100dvh-2rem)] translate-x-[-50%] translate-y-[-50%] bg-white rounded-2xl shadow-warm-hover overflow-hidden border border-argile-200 flex flex-col focus:outline-none animate-in fade-in zoom-in-95 duration-200 p-0',
            sizes[size],
            className
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-argile-200 bg-sand-50/50 shrink-0">
            {title ? (
              <Dialog.Title className="font-display font-bold text-base text-charcoal-900">
                {title}
              </Dialog.Title>
            ) : (
              <div />
            )}
            <Dialog.Close asChild>
              <button
                type="button"
                className="w-8 h-8 rounded-full bg-sand-200 hover:bg-sand-300 text-charcoal-600 flex items-center justify-center transition-colors focus:outline-none focus:ring-2 focus:ring-terracotta-500"
                aria-label="Fermer"
              >
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>

          {/* Body Content */}
          <div className="p-6 overflow-y-auto flex-1 min-h-0">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
};
