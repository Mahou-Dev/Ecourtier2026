import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    screens: {
      'xs': '360px',
      'sm': '640px',
      'md': '768px',
      'lg': '1024px',
      'xl': '1280px',
      '2xl': '1536px',
    },
    extend: {
      colors: {
        // E-Courtier Custom Senegal Architectural Palette
        terracotta: {
          50: '#FDF6F3',
          100: '#F9EBE5',
          200: '#F3D4C7',
          300: '#E7B29D',
          400: '#D78465',
          500: '#C85A32', // Main Brand Accent (Terre Cuite)
          600: '#B24723',
          700: '#94371A',
          800: '#7B2E18',
          900: '#652917',
        },
        sahel: {
          50: '#F2F7F5',
          100: '#DFECE6',
          200: '#C1D9CD',
          300: '#99BFB0',
          400: '#6F9F8F',
          500: '#4D8272',
          600: '#3A685A',
          700: '#2F544A',
          800: '#1D3E35', // Deep Forest / Sahel Green
          900: '#152E28',
        },
        sand: {
          50: '#FFFFFF',
          100: '#FDFBF7', // Primary Warm Off-White / Blanc Cassé
          200: '#F9F5EC',
          300: '#F3ECDB',
          400: '#EAE0C7',
          500: '#DFCEAA',
          600: '#C7B288',
          700: '#9F8B65',
          800: '#77674A',
          900: '#524734',
        },
        ocher: {
          50: '#FEFBF3',
          100: '#FDF5E1',
          200: '#FBE8BC',
          300: '#F7D68E',
          400: '#EFBD57',
          500: '#E09F3E', // Warm Sun Ocher
          600: '#C7822A',
          700: '#A46120',
          800: '#844B1D',
          900: '#6D3D1B',
        },
        charcoal: {
          50: '#F6F7F9',
          100: '#EBEFF3',
          200: '#D2DBE3',
          300: '#ADC0D0',
          400: '#7E9BB3',
          500: '#577692',
          600: '#425C75',
          700: '#354A5F',
          800: '#1E232A', // Main High Contrast Text
          900: '#13171C',
        },
        argile: {
          100: '#FAF7F0',
          200: '#E5DEC9', // Soft Border & Divider Neutral
          300: '#D5CBB0',
        }
      },
      fontFamily: {
        display: ['"Plus Jakarta Sans"', 'sans-serif'],
        body: ['"Plus Jakarta Sans"', '"Inter"', 'sans-serif'],
      },
      boxShadow: {
        'terracotta-glow': '0 10px 30px -5px rgba(200, 90, 50, 0.28)',
        'terracotta-card': '0 8px 24px -4px rgba(200, 90, 50, 0.16)',
        'sahel-glow': '0 10px 30px -5px rgba(29, 62, 53, 0.28)',
        'sahel-card': '0 8px 24px -4px rgba(29, 62, 53, 0.20)',
        'sand-glow': '0 8px 24px -4px rgba(224, 159, 62, 0.18)',
        'warm-card': '0 4px 20px -2px rgba(82, 71, 52, 0.08), 0 2px 6px -1px rgba(200, 90, 50, 0.04)',
        'warm-hover': '0 14px 36px -4px rgba(200, 90, 50, 0.22), 0 4px 14px -2px rgba(82, 71, 52, 0.10)',
      },
      borderRadius: {
        'xs': '0.125rem',
        '2xl': '1.25rem',
        '3xl': '1.75rem',
      },
    },
  },
  plugins: [],
};

export default config;
