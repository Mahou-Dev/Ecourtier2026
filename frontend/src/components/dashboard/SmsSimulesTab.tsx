import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { MessageCircle, Smartphone } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { NotificationSmsDTO, PageResponse, TypeNotificationSms } from '../../types';
import { Badge } from '../ui/Badge';
import { Card, CardBody } from '../ui/Card';
import { Skeleton } from '../ui/Skeleton';

const TYPE_LABELS: Record<TypeNotificationSms, { label: string; variant: 'warning' | 'info' | 'rose' | 'success' }> = {
  RDV_DEMANDE: { label: 'Demande de visite', variant: 'warning' },
  RDV_CONFIRME: { label: 'Visite confirmée', variant: 'success' },
  RDV_ANNULE: { label: 'Visite annulée', variant: 'rose' },
  RDV_RAPPEL: { label: 'Rappel J-1', variant: 'info' },
};

/**
 * Panneau « SMS simulés » : affiche les SMS enregistrés par SmsGatewayLogImpl
 * pour l'utilisateur connecté (GET /api/v1/notifications-sms/mes-sms).
 */
export const SmsSimulesTab: React.FC = () => {
  const { data, isLoading, isError } = useQuery<PageResponse<NotificationSmsDTO>>({
    queryKey: ['mes-sms'],
    queryFn: () => apiFetch<PageResponse<NotificationSmsDTO>>('/api/v1/notifications-sms/mes-sms?page=0&size=50'),
    refetchInterval: 15000,
  });

  const smsList = data?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display font-bold text-2xl text-sahel-900">SMS simulés</h1>
        <p className="text-xs text-sahel-600">
          Notifications SMS envoyées automatiquement à chaque étape d'un rendez-vous (demande, confirmation, annulation, rappel la veille).
          Mode démonstration : aucun SMS réel n'est expédié.
        </p>
      </div>

      {isLoading ? (
        <Skeleton variant="card" className="h-32" />
      ) : isError ? (
        <Card className="p-6 text-center border border-rose-200 bg-rose-50 text-xs text-rose-800">
          Impossible de charger les SMS simulés.
        </Card>
      ) : smsList.length === 0 ? (
        <Card className="p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
          <MessageCircle className="w-12 h-12 text-sahel-400 mx-auto" />
          <h3 className="font-display font-bold text-lg text-sahel-900">Aucun SMS reçu</h3>
          <p className="text-xs text-sahel-600 max-w-md mx-auto">
            Les SMS liés à vos demandes de visite apparaîtront ici.
          </p>
        </Card>
      ) : (
        <ul className="space-y-3" aria-label="Liste des SMS simulés">
          {smsList.map((sms) => {
            const meta = TYPE_LABELS[sms.type];
            return (
              <li key={sms.id}>
                <Card className="border border-sand-300">
                  <CardBody className="p-4 space-y-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant={meta?.variant ?? 'neutral'}>{meta?.label ?? sms.type}</Badge>
                        <Badge variant="neutral">{sms.statut}</Badge>
                        <span className="text-[11px] text-sahel-600 flex items-center gap-1">
                          <Smartphone className="w-3.5 h-3.5" aria-hidden="true" />
                          {sms.telephone || 'N/A'}
                        </span>
                      </div>
                      <time className="text-[11px] text-sahel-600" dateTime={sms.dateCreation}>
                        {new Date(sms.dateCreation).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })}
                      </time>
                    </div>
                    <p className="text-sm text-sahel-900">{sms.message}</p>
                  </CardBody>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};
