/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Tokens da identidade JH.COMP (carvão / grafite / vermelho industrial)
        background: '#111317',
        surface: {
          lowest: '#0c0e11',
          low: '#1a1c1f',
          DEFAULT: '#1e2023',
          high: '#282a2d',
          highest: '#333538',
          bright: '#37393d',
        },
        brand: {
          DEFAULT: '#ff5354',
          soft: '#ffb3ae',
          deep: '#c41a24',
          laser: '#e62e38',
          ink: '#68000d',
        },
        outline: {
          DEFAULT: '#ac8886',
          variant: '#5c403e',
        },
        content: {
          DEFAULT: '#e2e2e6',
          muted: '#c5c6c9',
          variant: '#e5bdba',
        },
      },
      fontFamily: {
        display: ['Syne', 'system-ui', 'sans-serif'],
        body: ['"Plus Jakarta Sans"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '0.125rem',
        lg: '0.25rem',
        xl: '0.5rem',
      },
      boxShadow: {
        glow: '0 0 12px rgba(255, 83, 84, 0.45)',
      },
    },
  },
  plugins: [],
};
