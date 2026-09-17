import React from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { motion, AnimatePresence } from 'framer-motion';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface DrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  position?: 'right' | 'bottom' | 'left';
  className?: string;
}

export const Drawer: React.FC<DrawerProps> = ({
  isOpen,
  onClose,
  title,
  children,
  position = 'right',
  className,
}) => {
  // Motion animation variants based on edge anchor
  const getMotionProps = () => {
    if (position === 'bottom') {
      return {
        initial: { y: '100%' },
        animate: { y: 0 },
        exit: { y: '100%' },
      };
    }
    if (position === 'left') {
      return {
        initial: { x: '-100%' },
        animate: { x: 0 },
        exit: { x: '-100%' },
      };
    }
    // Default right edge slide-in
    return {
      initial: { x: '100%' },
      animate: { x: 0 },
      exit: { x: '100%' },
    };
  };

  const motionProps = getMotionProps();

  const positionStyles = {
    right: 'fixed inset-y-0 right-0 w-full sm:w-[420px] border-l border-argile-200 shadow-warm-hover',
    left: 'fixed inset-y-0 left-0 w-full sm:w-[420px] border-r border-argile-200 shadow-warm-hover',
    bottom: 'fixed inset-x-0 bottom-0 max-h-[88vh] rounded-t-3xl border-t border-argile-200 shadow-warm-hover',
  };

  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <AnimatePresence>
        {isOpen && (
          <Dialog.Portal forceMount>
            {/* Backdrop Overlay */}
            <Dialog.Overlay asChild>
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="fixed inset-0 z-50 bg-charcoal-900/50 backdrop-blur-xs"
              />
            </Dialog.Overlay>

            {/* Edge-Anchored Sliding Drawer with Radix Focus-Trap */}
            <Dialog.Content asChild>
              <motion.div
                {...motionProps}
                transition={{ type: 'spring', damping: 28, stiffness: 280 }}
                className={cn(
                  'z-50 bg-white flex flex-col focus:outline-none overflow-hidden',
                  positionStyles[position],
                  className
                )}
              >
                {/* Visual drag handle indicator for bottom sheet */}
                {position === 'bottom' && (
                  <div className="w-12 h-1.5 bg-argile-300 rounded-full mx-auto mt-2.5 shrink-0" />
                )}

                {/* Drawer Header */}
                <div className="flex items-center justify-between p-5 border-b border-argile-200 bg-sand-100 shrink-0">
                  {title ? (
                    <Dialog.Title className="font-display font-bold text-lg text-charcoal-900">
                      {title}
                    </Dialog.Title>
                  ) : (
                    <div />
                  )}
                  <Dialog.Close asChild>
                    <button
                      type="button"
                      className="p-2 rounded-xl text-charcoal-500 hover:bg-sand-200 transition-colors focus:outline-none focus:ring-2 focus:ring-terracotta-500"
                      aria-label="Fermer"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  </Dialog.Close>
                </div>

                {/* Drawer Scrollable Content */}
                <div className="p-6 overflow-y-auto flex-1">{children}</div>
              </motion.div>
            </Dialog.Content>
          </Dialog.Portal>
        )}
      </AnimatePresence>
    </Dialog.Root>
  );
};
