import React, { useState } from 'react';
import { 
  Home, 
  Calendar, 
  Lock, 
  ShieldCheck, 
  Plus, 
  Eye, 
  Edit3, 
  LogOut
} from 'lucide-react';
import { MOCK_PROPERTIES } from '../mocks/properties';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { Avatar } from '../components/ui/Avatar';

export const DashboardPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'ANNONCES' | 'VISITES' | 'SEQUESTRES'>('ANNONCES');

  const mockVisits = [
    { id: 'v-1', property: 'Appartement F3 Nema', client: 'Moussa Diouf', phone: '+221 77 123 45 67', date: 'Demain, 15:30', status: 'CONFIRME' },
    { id: 'v-2', property: 'Studio Meublé Boucotte', client: 'Aminata Ba', phone: '+221 78 987 65 43', date: 'Vendredi, 10:00', status: 'EN_ATTENTE' },
    { id: 'v-3', property: 'Villa Diabir', client: 'Jean Gomis', phone: '+221 76 543 21 09', date: 'Samedi, 11:00', status: 'CONFIRME' },
  ];

  const mockEscrows = [
    { id: 'esc-1', property: 'Appartement F3 Nema', tenant: 'Moussa Diouf', amount: '300 000 FCFA', status: 'FONDS_BLOQUES_SEQUESTRE', step: 'En attente d’état des lieux' },
    { id: 'esc-2', property: 'Villa Diabir', tenant: 'Khadija Kane', amount: '500 000 FCFA', status: 'BAIL_SIGNE', step: 'Libération des fonds sous 24h' },
  ];

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      
      {/* SIDEBAR NAVIGATION */}
      <aside className="w-full md:w-64 bg-sahel-900 text-white shrink-0 border-r border-sahel-800 p-4 space-y-6">
        
        {/* User profile snippet */}
        <div className="flex items-center gap-3 p-3 rounded-2xl bg-sahel-800 border border-sahel-700">
          <Avatar
            src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80"
            name="Ousmane Badji"
            size="md"
            isVerified
          />
          <div className="min-w-0">
            <h3 className="font-display font-bold text-sm text-white truncate">Ousmane Badji</h3>
            <p className="text-[11px] text-ocher-300 font-semibold">Courtier Certifié SN</p>
          </div>
        </div>

        {/* Sidebar Nav Items */}
        <nav className="space-y-1">
          <button
            onClick={() => setActiveTab('ANNONCES')}
            className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
              activeTab === 'ANNONCES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <Home className="w-4 h-4" />
              <span>Mes Annonces ({MOCK_PROPERTIES.length})</span>
            </div>
          </button>

          <button
            onClick={() => setActiveTab('VISITES')}
            className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
              activeTab === 'VISITES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <Calendar className="w-4 h-4" />
              <span>Agenda des Visites</span>
            </div>
            <span className="bg-ocher-500 text-charcoal-900 text-[10px] font-extrabold px-2 py-0.5 rounded-full">3</span>
          </button>

          <button
            onClick={() => setActiveTab('SEQUESTRES')}
            className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
              activeTab === 'SEQUESTRES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <Lock className="w-4 h-4" />
              <span>Dossiers Séquestre</span>
            </div>
            <span className="bg-sahel-700 text-sahel-100 text-[10px] font-extrabold px-2 py-0.5 rounded-full">2</span>
          </button>
        </nav>

        <div className="pt-6 border-t border-sahel-800/80">
          <Button variant="ghost" size="sm" className="w-full justify-start text-sahel-300 hover:text-white hover:bg-sahel-800" leftIcon={<LogOut className="w-4 h-4" />}>
            Déconnexion
          </Button>
        </div>
      </aside>

      {/* MAIN DASHBOARD CONTENT */}
      <main className="flex-1 p-4 xs:p-6 lg:p-8 space-y-8 overflow-y-auto">
        
        {/* Top Header Stats Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="font-display font-extrabold text-2xl text-charcoal-900">
              Tableau de Bord Partenaire
            </h1>
            <p className="text-xs text-charcoal-600 mt-0.5">
              Gestion de vos biens et suivi des cautions sous séquestre bancaire.
            </p>
          </div>

          <Button variant="primary" size="md" leftIcon={<Plus className="w-4 h-4" />}>
            Publier une nouvelle annonce
          </Button>
        </div>

        {/* STATS OVERVIEW CARDS */}
        <div className="grid grid-cols-1 xs:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="bg-white">
            <CardBody className="p-4 space-y-2">
              <span className="text-xs font-semibold text-charcoal-500">Annonces Actives</span>
              <div className="flex items-baseline justify-between">
                <span className="font-display font-extrabold text-2xl text-charcoal-900">4</span>
                <Badge variant="sahel">100% Certifiées</Badge>
              </div>
            </CardBody>
          </Card>

          <Card className="bg-white">
            <CardBody className="p-4 space-y-2">
              <span className="text-xs font-semibold text-charcoal-500">Visites cette semaine</span>
              <div className="flex items-baseline justify-between">
                <span className="font-display font-extrabold text-2xl text-charcoal-900">3</span>
                <Badge variant="ocher">2 Confirmées</Badge>
              </div>
            </CardBody>
          </Card>

          <Card className="bg-white">
            <CardBody className="p-4 space-y-2">
              <span className="text-xs font-semibold text-charcoal-500">Fonds sous Séquestre</span>
              <div className="flex items-baseline justify-between">
                <span className="font-display font-extrabold text-xl text-terracotta-600">800 000 F</span>
                <Badge variant="sahel">Sécurisés</Badge>
              </div>
            </CardBody>
          </Card>

          <Card className="bg-white">
            <CardBody className="p-4 space-y-2">
              <span className="text-xs font-semibold text-charcoal-500">Note Global Agent</span>
              <div className="flex items-baseline justify-between">
                <span className="font-display font-extrabold text-2xl text-charcoal-900">4.9 / 5</span>
                <span className="text-xs text-charcoal-500 font-semibold">38 avis</span>
              </div>
            </CardBody>
          </Card>
        </div>

        {/* TAB CONTENT 1: MES ANNONCES (RESPONSIVE TABLE TO CARDS) */}
        {activeTab === 'ANNONCES' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-display font-bold text-lg text-charcoal-900">Mes Annonces Immobilières</h2>
            </div>

            <div className="bg-white rounded-2xl border border-argile-200 shadow-warm-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-sand-100 text-charcoal-700 font-semibold uppercase tracking-wider border-b border-argile-200">
                    <tr>
                      <th className="p-4">Bien</th>
                      <th className="p-4">Localisation</th>
                      <th className="p-4">Prix (FCFA)</th>
                      <th className="p-4">Statut Certification</th>
                      <th className="p-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-argile-200">
                    {MOCK_PROPERTIES.slice(0, 4).map((prop) => (
                      <tr key={prop.id} className="hover:bg-sand-50/60 transition-colors">
                        <td className="p-4">
                          <div className="flex items-center gap-3">
                            <img src={prop.images[0]} alt={prop.title} className="w-12 h-12 rounded-xl object-cover" />
                            <div>
                              <span className="font-bold text-charcoal-900 block line-clamp-1">{prop.title}</span>
                              <span className="text-[11px] text-charcoal-500">{prop.type} &bull; {prop.surface} m²</span>
                            </div>
                          </div>
                        </td>
                        <td className="p-4 font-medium text-charcoal-800">
                          {prop.neighborhood}, {prop.city}
                        </td>
                        <td className="p-4 font-extrabold text-terracotta-600">
                          {prop.price.toLocaleString('fr-FR')} F
                        </td>
                        <td className="p-4">
                          <Badge variant="sahel">
                            <ShieldCheck className="w-3.5 h-3.5 mr-1 inline" />
                            Niveau {prop.verificationLevel} Validé
                          </Badge>
                        </td>
                        <td className="p-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Button variant="ghost" size="sm" className="p-1.5"><Eye className="w-4 h-4 text-charcoal-600" /></Button>
                            <Button variant="ghost" size="sm" className="p-1.5"><Edit3 className="w-4 h-4 text-charcoal-600" /></Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB CONTENT 2: VISITS AGENDA */}
        {activeTab === 'VISITES' && (
          <div className="space-y-4">
            <h2 className="font-display font-bold text-lg text-charcoal-900">Agenda des Visites de la Semaine</h2>
            
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {mockVisits.map((visit) => (
                <Card key={visit.id} className="bg-white">
                  <CardBody className="space-y-3">
                    <div className="flex items-center justify-between">
                      <Badge variant={visit.status === 'CONFIRME' ? 'sahel' : 'ocher'}>
                        {visit.status === 'CONFIRME' ? 'Visite Confirmée' : 'En Attente de Confirmation'}
                      </Badge>
                      <span className="text-xs font-bold text-terracotta-600">{visit.date}</span>
                    </div>

                    <h4 className="font-display font-bold text-sm text-charcoal-900">{visit.property}</h4>

                    <div className="text-xs text-charcoal-600 space-y-1 bg-sand-100 p-2.5 rounded-xl">
                      <p><strong>Client :</strong> {visit.client}</p>
                      <p><strong>WhatsApp :</strong> {visit.phone}</p>
                    </div>

                    <div className="flex items-center gap-2 pt-1">
                      <Button variant="primary" size="sm" className="w-full justify-center">Contacter WhatsApp</Button>
                    </div>
                  </CardBody>
                </Card>
              ))}
            </div>
          </div>
        )}

        {/* TAB CONTENT 3: ESCROW DOSSIERS */}
        {activeTab === 'SEQUESTRES' && (
          <div className="space-y-4">
            <h2 className="font-display font-bold text-lg text-charcoal-900">Dossiers de Cautions sous Séquestre</h2>
            
            <div className="space-y-3">
              {mockEscrows.map((escrow) => (
                <div key={escrow.id} className="p-5 bg-white rounded-2xl border border-argile-200 shadow-warm-card flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <Lock className="w-4 h-4 text-sahel-800" />
                      <h4 className="font-display font-bold text-base text-charcoal-900">{escrow.property}</h4>
                      <Badge variant="sahel">Séquestre Actif</Badge>
                    </div>
                    <p className="text-xs text-charcoal-600">Locataire : <strong>{escrow.tenant}</strong> &bull; Étape : {escrow.step}</p>
                  </div>
                  <div className="text-right">
                    <span className="font-display font-extrabold text-xl text-terracotta-600 block">{escrow.amount}</span>
                    <span className="text-[11px] text-sahel-800 font-semibold">Compte Séquestre UBA / Wave</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

      </main>

    </div>
  );
};
