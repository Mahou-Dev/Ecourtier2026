import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import { Header } from './components/layout/Header';
import { Footer } from './components/layout/Footer';
import { AiAssistantWidget } from './components/ai/AiAssistantWidget';
import { Toaster } from './components/ui/Toast';
import { Skeleton } from './components/ui/Skeleton';

// Code-splitting routes with React.lazy()
const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const SearchResultsPage = lazy(() => import('./pages/SearchResultsPage').then(m => ({ default: m.SearchResultsPage })));
const PropertyDetailPage = lazy(() => import('./pages/PropertyDetailPage').then(m => ({ default: m.PropertyDetailPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const AdminVerificationPage = lazy(() => import('./pages/AdminVerificationPage').then(m => ({ default: m.AdminVerificationPage })));
const AdminSignalementsPage = lazy(() => import('./pages/AdminSignalementsPage').then(m => ({ default: m.AdminSignalementsPage })));
import { OfflineIndicator } from './components/ui/OfflineIndicator';

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

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <Router>
        <div className="min-h-screen flex flex-col bg-sand-100 selection:bg-terracotta-200 selection:text-terracotta-900 font-body antialiased">
          <Header />
          <main className="flex-1">
            <Suspense fallback={<PageSkeletonFallback />}>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/recherche" element={<SearchResultsPage />} />
                <Route path="/annonce/:id" element={<PropertyDetailPage />} />
                <Route path="/connexion" element={<LoginPage />} />
                <Route path="/inscription" element={<RegisterPage />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/admin/verification" element={<AdminVerificationPage />} />
                <Route path="/admin/signalements" element={<AdminSignalementsPage />} />
              </Routes>
            </Suspense>
          </main>
          <Footer />

          {/* GLOBAL FLOATING CONVERSATIONAL AI WIDGET */}
          <AiAssistantWidget />

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
