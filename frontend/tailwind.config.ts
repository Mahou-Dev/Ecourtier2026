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
        // E-Courtier Custom Senegal Architectural Palette (Aligned with Sahel Premium)
        terracotta: {
          50: '#FDF6F3',
          100: '#F9EBE5',
          200: '#F3D4C7',
          300: '#E7B29D',
          400: '#D78465',
          500: '#B85C42', // Main Brand Accent (Terre Cuite Sahel Premium)
          600: '#9D4A32',
          700: '#823B26',
          800: '#6C2F1F',
          900: '#582619',
        },
        sahel: {
          50: '#F2F7F5',
          100: '#DFECE6',
          200: '#C1D9CD',
          300: '#99BFB0',
          400: '#6F9F8F',
          500: '#4D8272',
          600: '#3A685A',
          700: '#274940',
          800: '#1C352E', // Deep Forest / Sahel Green
          900: '#152722',
        },
        sand: {
          50: '#FAF7F2',
          100: '#F5F0E8', // Primary Warm Ivory / Sand
          200: '#EDE6D8',
          300: '#E2DACB',
          400: '#D7CCBA',
          500: '#C8BA9F',
          600: '#B19F7E',
          700: '#8E7D5E',
          800: '#6D5E45',
          900: '#4D4231',
        },
        ocher: {
          50: '#FEFBF3',
          100: '#FDF5E1',
          200: '#FBE8BC',
          300: '#F2D29A',
          400: '#E0BD87',
          500: '#D5AD72', // Warm Sun Ocher
          600: '#BC8C49',
          700: '#9A6C2E',
          800: '#7B5321',
          900: '#63421B',
        },
        charcoal: {
          50: '#F6F7F8',
          100: '#ECEEF0',
          200: '#D5DAE0',
          300: '#B2BBC4',
          400: '#83919F',
          500: '#5C6974',
          600: '#47525A',
          700: '#353C3B',
          800: '#252A29', // Main High Contrast Charcoal
          900: '#18211F',
        },
        argile: {
          100: '#FAF7F2',
          200: '#E2DACB', // Soft Border & Divider Neutral
          300: '#D6CCB8',
        }
      },
      fontFamily: {
        display: ['"Manrope"', '"Plus Jakarta Sans"', 'sans-serif'],
        editorial: ['"Manrope"', '"Plus Jakarta Sans"', 'sans-serif'],
        body: ['"Inter"', '"Plus Jakarta Sans"', 'sans-serif'],
      },
      boxShadow: {
        'terracotta-glow': '0 10px 30px -5px rgba(184, 92, 66, 0.28)',
        'terracotta-card': '0 8px 24px -4px rgba(184, 92, 66, 0.16)',
        'sahel-glow': '0 10px 30px -5px rgba(28, 53, 46, 0.28)',
        'sahel-deep': '0 16px 40px -12px rgba(21, 39, 34, 0.45)',
        'sahel-card': '0 8px 24px -4px rgba(28, 53, 46, 0.20)',
        'sand-glow': '0 8px 24px -4px rgba(213, 173, 114, 0.18)',
        'warm-card': '0 4px 20px -2px rgba(37, 42, 41, 0.08), 0 2px 6px -1px rgba(184, 92, 66, 0.04)',
        'warm-hover': '0 14px 36px -4px rgba(184, 92, 66, 0.20), 0 4px 14px -2px rgba(37, 42, 41, 0.10)',
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
