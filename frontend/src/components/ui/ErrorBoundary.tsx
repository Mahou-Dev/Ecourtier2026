import React from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Home, RotateCcw } from 'lucide-react';
import { PatternClaustra } from './PatternClaustra';
import { Button } from './Button';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  resetKey?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string | null;
}

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      errorMessage: null,
    };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      errorMessage: error?.message || 'Erreur inattendue dans l’interface',
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('[ErrorBoundary] Exception capturée :', error, errorInfo);
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    if (this.state.hasError && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false, errorMessage: null });
    }
  }

  private handleReset = () => {
    this.setState({ hasError: false, errorMessage: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <section
          role="alert"
          className="relative flex-1 min-h-[60vh] flex items-center justify-center bg-sand-100 py-14 px-4 sm:px-6 pb-24 sm:pb-16 overflow-hidden"
        >
          <PatternClaustra variant="terracotta" opacity={0.05} scale={1.1} />

          <div className="relative z-10 max-w-lg w-full bg-white rounded-3xl border border-argile-200 shadow-warm-card p-6 sm:p-10 text-center space-y-5">
            <div className="w-14 h-14 rounded-2xl bg-rose-50 border border-rose-200 text-rose-600 flex items-center justify-center mx-auto">
              <AlertTriangle className="w-7 h-7" aria-hidden="true" />
            </div>

            <div className="space-y-2">
              <span className="inline-flex items-center px-3 py-1 rounded-full bg-sand-100 border border-argile-200 text-sahel-800 text-xs font-bold uppercase tracking-wider">
                Incident d’affichage
              </span>
              <h1 className="font-editorial text-xl sm:text-2xl font-extrabold text-charcoal-900 tracking-tight">
                Cette vue n’a pas pu se charger correctement
              </h1>
              <p className="text-sm text-charcoal-600 leading-relaxed">
                Un incident technique temporaire est survenu. Vous pouvez réessayer l’affichage ou revenir vers la page d’accueil.
              </p>
              {this.state.errorMessage && (
                <p className="text-xs font-mono text-charcoal-500 bg-sand-50 border border-sand-200 rounded-xl p-2.5 break-words">
                  {this.state.errorMessage}
                </p>
              )}
            </div>

            <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3 pt-1">
              <Button
                type="button"
                variant="primary"
                size="md"
                onClick={this.handleReset}
                className="w-full sm:w-auto justify-center min-h-[44px]"
                leftIcon={<RotateCcw className="w-4 h-4" />}
              >
                Réessayer
              </Button>
              <Link
                to="/"
                onClick={this.handleReset}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] rounded-full border-2 border-terracotta-500 text-terracotta-600 hover:bg-terracotta-50 font-display font-semibold text-sm transition-colors"
              >
                <Home className="w-4 h-4" aria-hidden="true" />
                <span>Retour à l’accueil</span>
              </Link>
            </div>
          </div>
        </section>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
