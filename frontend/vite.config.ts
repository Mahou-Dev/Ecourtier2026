import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        globIgnores: [
          '**/Admin*.js',
          '**/admin.*.js',
          '**/DashboardPage*.js',
          '**/MonComptePage*.js',
        ],
        runtimeCaching: [
          {
            // Scope strictly to anonymous public GET /api/v1/annonces and /api/v1/annonces/{id}
            urlPattern: ({ url, request }) => {
              // 1. Only GET requests
              if (request.method !== 'GET') return false;

              // 2. Reject ANY request bearing an Authorization header (authenticated contexts)
              const authHeader = request.headers
                ? (request.headers.get('authorization') || request.headers.get('Authorization'))
                : null;
              if (authHeader) return false;

              // 2b. Reject ANY request bearing cookies or credentials (session/cookie auth contexts)
              const cookieHeader = request.headers
                ? (request.headers.get('cookie') || request.headers.get('Cookie'))
                : null;
              if (cookieHeader) return false;
              const creds = (request as any).credentials;
              if (creds === 'include' || creds === 'same-origin') return false;

              const pathname = url.pathname;
              const search = url.search;

              // 3. Exclude administrative, authentication, and user-private routes
              if (pathname.includes('/admin') || pathname.includes('/auth') || pathname.includes('/utilisateurs/moi')) {
                return false;
              }

              // 4. Exclude verification documents, user uploads, and private queries
              if (pathname.includes('/documents') || pathname.includes('/fichier')) {
                return false;
              }
              if (
                search.includes('mes_annonces') ||
                search.includes('mes-annonces') ||
                search.includes('mes-transactions') ||
                search.includes('favoris')
              ) {
                return false;
              }

              // 5. Match only genuinely public listing endpoints
              return (
                pathname === '/api/v1/annonces' ||
                pathname === '/api/v1/annonces/quartiers' ||
                /^\/api\/v1\/annonces\/[a-f0-9-]+$/i.test(pathname)
              );
            },
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'public-annonces-cache',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 24 * 60 * 60, // 24h
              },
              cacheableResponse: {
                statuses: [200],
              },
            },
          },
        ],
      },
      manifest: {
        name: 'E-Courtier Sénégal',
        short_name: 'E-Courtier',
        description: 'Plateforme immobilière sénégalaise avec contrôle cadastral & séquestre bancaire',
        lang: 'fr',
        theme_color: '#1a3c34',
        background_color: '#faf8f5',
        display: 'standalone',
        icons: [
          {
            src: 'favicon.ico',
            sizes: '64x64 32x32 24x24 16x16',
            type: 'image/x-icon',
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    allowedHosts: true,
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes('node_modules')) {
            if (id.includes('leaflet')) {
              return 'vendor-leaflet';
            }
            if (id.includes('yet-another-react-lightbox')) {
              return 'vendor-lightbox';
            }
            if (id.includes('@radix-ui')) {
              return 'vendor-radix';
            }
            if (id.includes('framer-motion')) {
              return 'vendor-motion';
            }
            if (id.includes('react-dom') || id.includes('react-router-dom') || id.includes('@tanstack')) {
              return 'vendor-react';
            }
          }
        },
      },
    },
  },
});
