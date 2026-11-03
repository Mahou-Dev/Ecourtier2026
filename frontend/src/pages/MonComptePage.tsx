import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, User, Mail, Phone, ShieldCheck, CheckCircle2, KeyRound } from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { Card, CardBody } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { toast } from 'sonner';

export const MonComptePage: React.FC = () => {
  const navigate = useNavigate();
  const { utilisateur } = useAuthStore();

  const [ancienMotDePasse, setAncienMotDePasse] = useState('');
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState('');
  const [confirmationMotDePasse, setConfirmationMotDePasse] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion', { state: { from: '/mon-compte' } });
    }
  }, [utilisateur, navigate]);

  if (!utilisateur) return null;

  const hasMinLength = nouveauMotDePasse.length >= 8;
  const hasUppercase = /[A-Z]/.test(nouveauMotDePasse);
  const hasDigit = /\d/.test(nouveauMotDePasse);
  const isPolicyValid = hasMinLength && hasUppercase && hasDigit;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ancienMotDePasse || !nouveauMotDePasse) {
      toast.error('Veuillez renseigner votre ancien et votre nouveau mot de passe');
      return;
    }
    if (!isPolicyValid) {
      toast.error('Le nouveau mot de passe doit contenir au moins 8 caractères, une majuscule et un chiffre');
      return;
    }
    if (nouveauMotDePasse !== confirmationMotDePasse) {
      toast.error('La confirmation ne correspond pas au nouveau mot de passe');
      return;
    }

    setLoading(true);
    try {
      const res = await adminService.changerMonMotDePasse({
        ancienMotDePasse,
        nouveauMotDePasse,
      });
      toast.success(res.message || 'Mot de passe modifié avec succès');
      setAncienMotDePasse('');
      setNouveauMotDePasse('');
      setConfirmationMotDePasse('');
    } catch (err: any) {
      toast.error(err.message || 'Impossible de modifier le mot de passe');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-80px)] bg-sand-100 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="relative rounded-3xl bg-sahel-900 text-white p-6 sm:p-8 overflow-hidden shadow-sahel-deep border border-sahel-800">
          <PatternClaustra className="opacity-15 text-terracotta-500" />
          <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-terracotta-500/20 border border-terracotta-500/40 text-terracotta-300 text-xs font-semibold">
                <User className="w-3.5 h-3.5" />
                <span>Mon compte</span>
              </div>
              <h1 className="font-display font-extrabold text-2xl sm:text-3xl">
                {utilisateur.prenom} {utilisateur.nom}
              </h1>
              <p className="text-xs text-sand-300">
                Gérez vos informations de profil et la sécurité de votre mot de passe.
              </p>
            </div>
            <Badge variant="ocher" className="self-start sm:self-center text-xs px-3 py-1.5">
              Rôle : {utilisateur.role}
            </Badge>
          </div>
        </div>

        <Card className="border border-argile-200 bg-white">
          <CardBody className="p-6 space-y-4">
            <h2 className="font-display font-bold text-base text-charcoal-900 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-sahel-700" />
              <span>Informations du compte</span>
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <span className="text-charcoal-500 block mb-0.5">Nom complet</span>
                <span className="font-bold text-charcoal-900">{utilisateur.prenom} {utilisateur.nom}</span>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <span className="text-charcoal-500 block mb-0.5 flex items-center gap-1">
                  <Mail className="w-3.5 h-3.5 text-terracotta-500" /> Email
                </span>
                <span className="font-bold text-charcoal-900">{utilisateur.email}</span>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <span className="text-charcoal-500 block mb-0.5 flex items-center gap-1">
                  <Phone className="w-3.5 h-3.5 text-terracotta-500" /> Téléphone
                </span>
                <span className="font-bold text-charcoal-900">{utilisateur.telephone}</span>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <span className="text-charcoal-500 block mb-0.5">Profil E-Courtier</span>
                <span className="font-bold text-sahel-800">{utilisateur.role}</span>
              </div>
            </div>
          </CardBody>
        </Card>

        <Card className="border border-argile-200 bg-white">
          <CardBody className="p-6 space-y-5">
            <div>
              <h2 className="font-display font-bold text-base text-charcoal-900 flex items-center gap-2">
                <KeyRound className="w-4 h-4 text-terracotta-600" />
                <span>Changer mon mot de passe</span>
              </h2>
              <p className="text-xs text-charcoal-500 mt-1">
                Si votre compte a été créé avec un mot de passe temporaire, définissez ici votre mot de passe personnel.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label="Mot de passe actuel"
                type="password"
                required
                value={ancienMotDePasse}
                onChange={(e) => setAncienMotDePasse(e.target.value)}
                placeholder="Saisissez votre mot de passe actuel"
                leftIcon={<Lock className="w-4 h-4" />}
              />

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Nouveau mot de passe"
                  type="password"
                  required
                  value={nouveauMotDePasse}
                  onChange={(e) => setNouveauMotDePasse(e.target.value)}
                  placeholder="Min. 8 car., 1 majuscule, 1 chiffre"
                  leftIcon={<Lock className="w-4 h-4" />}
                />
                <Input
                  label="Confirmer le nouveau mot de passe"
                  type="password"
                  required
                  value={confirmationMotDePasse}
                  onChange={(e) => setConfirmationMotDePasse(e.target.value)}
                  placeholder="Répétez le nouveau mot de passe"
                  leftIcon={<Lock className="w-4 h-4" />}
                />
              </div>

              <div className="flex flex-wrap gap-3 text-[11px] bg-sand-50 p-3 rounded-xl border border-sand-200">
                <span className={`flex items-center gap-1 font-semibold ${hasMinLength ? 'text-emerald-700' : 'text-charcoal-500'}`}>
                  <CheckCircle2 className="w-3.5 h-3.5" /> 8 caractères minimum
                </span>
                <span className={`flex items-center gap-1 font-semibold ${hasUppercase ? 'text-emerald-700' : 'text-charcoal-500'}`}>
                  <CheckCircle2 className="w-3.5 h-3.5" /> 1 lettre majuscule
                </span>
                <span className={`flex items-center gap-1 font-semibold ${hasDigit ? 'text-emerald-700' : 'text-charcoal-500'}`}>
                  <CheckCircle2 className="w-3.5 h-3.5" /> 1 chiffre
                </span>
              </div>

              <div className="flex justify-end pt-2">
                <Button type="submit" variant="primary" isLoading={loading}>
                  Enregistrer le nouveau mot de passe
                </Button>
              </div>
            </form>
          </CardBody>
        </Card>
      </div>
    </div>
  );
};

export default MonComptePage;
