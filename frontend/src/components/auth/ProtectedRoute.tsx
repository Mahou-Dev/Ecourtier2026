import React, { useEffect, useRef } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuthStore } from '../../store/useAuthStore';
import { getRoleHomeRoute, UserRole } from '../../lib/roleLabels';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: readonly UserRole[];
  deniedMessage?: string;
}

const RoleDeniedRedirect: React.FC<{ to: string; message: string }> = ({ to, message }) => {
  const navigate = useNavigate();
  const notifiedRef = useRef(false);

  useEffect(() => {
    if (!notifiedRef.current) {
      notifiedRef.current = true;
      toast.error(message);
    }
    navigate(to, { replace: true });
  }, [navigate, to, message]);

  return null;
};

/**
 * Garde de route centralisée :
 * - Redirige les visiteurs non authentifiés vers `/connexion` en préservant `state.from` (pathname + query).
 * - Contrôle optionnellement les rôles autorisés sans créer de boucles ni permissions fictives.
 */
export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles,
  deniedMessage = 'Accès refusé : espace réservé aux profils autorisés.',
}) => {
  const utilisateur = useAuthStore((s) => s.utilisateur);
  const location = useLocation();

  if (!utilisateur) {
    const requestedTarget = `${location.pathname}${location.search}`;
    return <Navigate to="/connexion" replace state={{ from: requestedTarget }} />;
  }

  if (allowedRoles && !allowedRoles.includes(utilisateur.role)) {
    const fallbackRoute = getRoleHomeRoute(utilisateur.role);
    return <RoleDeniedRedirect to={fallbackRoute} message={deniedMessage} />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
