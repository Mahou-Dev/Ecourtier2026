import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation, Navigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import { Header } from './components/layout/Header';
import { Footer } from './components/layout/Footer';
import { Toaster } from './components/ui/Toast';
import { Skeleton } from './components/ui/Skeleton';
import { OfflineIndicator } from './components/ui/OfflineIndicator';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { useCurrentProfile } from './hooks/useCurrentProfile';

// Code-splitting global widgets with React.lazy()
const LazyAiAssistantWidget = lazy(() => import('./components/ai/AiAssistantWidget').then(m => ({ default: m.AiAssistantWidget })));

// Code-splitting routes with React.lazy()
const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const SearchResultsPage = lazy(() => import('./pages/SearchResultsPage').then(m => ({ default: m.SearchResultsPage })));
const PropertyDetailPage = lazy(() => import('./pages/PropertyDetailPage').then(m => ({ default: m.PropertyDetailPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const FavorisPage = lazy(() => import('./pages/FavorisPage').then(m => ({ default: m.FavorisPage })));
const MonComptePage = lazy(() => import('./pages/MonComptePage').then(m => ({ default: m.MonComptePage })));
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage').then(m => ({ default: m.AdminDashboardPage })));
const AdminUtilisateursPage = lazy(() => import('./pages/AdminUtilisateursPage').then(m => ({ default: m.AdminUtilisateursPage })));
const AdminVerificationPage = lazy(() => import('./pages/AdminVerificationPage').then(m => ({ default: m.AdminVerificationPage })));
const AdminSignalementsPage = lazy(() => import('./pages/AdminSignalementsPage').then(m => ({ default: m.AdminSignalementsPage })));
const AdminTransactionsPage = lazy(() => import('./pages/AdminTransactionsPage').then(m => ({ default: m.AdminTransactionsPage })));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then(m => ({ default: m.NotFoundPage })));

// Design System Skeleton Fallback
const PageSkeletonFallback: React.FC = () => (
  <div className="max-w-7xl mx-auto px-4 py-8 space-y-6">
    <Skeleton variant="rect" className="w-full h-48 rounded-3xl" />
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Skeleton variant="card" className="h-64" />
      <Skeleton variant="card" className="h-64" />
      <Skeleton variant="card" className="h-64" />
    </div>
  </div>
);

const AnimatedRoutes: React.FC = () => {
  const location = useLocation();
  useCurrentProfile();

  return (
    <div key={location.pathname} className="w-full min-w-0 flex-1 animate-route-fade-in">
      <ErrorBoundary resetKey={`${location.pathname}${location.search}`}>
        <Suspense fallback={<PageSkeletonFallback />}>
          <Routes location={location}>
            <Route path="/" element={<HomePage />} />
            <Route path="/recherche" element={<SearchResultsPage />} />
            <Route path="/annonce/:id" element={<PropertyDetailPage />} />
            <Route
              path="/favoris"
              element={
                <ProtectedRoute>
                  <FavorisPage />
                </ProtectedRoute>
              }
            />
            <Route path="/connexion" element={<LoginPage />} />
            <Route path="/inscription" element={<RegisterPage />} />
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/mon-compte"
              element={
                <ProtectedRoute>
                  <MonComptePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <Navigate to="/admin/dashboard" replace />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/dashboard"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <AdminDashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/utilisateurs"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <AdminUtilisateursPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/verification"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <AdminVerificationPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/signalements"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <AdminSignalementsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/transactions"
              element={
                <ProtectedRoute
                  allowedRoles={['ADMIN']}
                  deniedMessage="Accès refusé : espace réservé aux administrateurs E-Courtier."
                >
                  <AdminTransactionsPage />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </ErrorBoundary>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <Router>
        <div className="min-h-screen w-full max-w-[100vw] overflow-x-hidden flex flex-col bg-sand-100 selection:bg-terracotta-200 selection:text-terracotta-900 font-body antialiased">
          <Header />
          <main className="flex-1 flex flex-col min-w-0 w-full">
            <AnimatedRoutes />
          </main>
          <Footer />

          {/* GLOBAL FLOATING CONVERSATIONAL AI WIDGET */}
          <Suspense fallback={null}>
            <LazyAiAssistantWidget />
          </Suspense>

          {/* OFFLINE STATUS INDICATOR */}
          <OfflineIndicator />

          {/* SONNER TOASTER */}
          <Toaster />
        </div>
      </Router>
    </QueryClientProvider>
  );
};

export default App;
