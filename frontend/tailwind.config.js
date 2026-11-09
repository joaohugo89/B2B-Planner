/** @type {import('tailwindcss').Config} */

// Os tons vivem em variáveis CSS (ver index.css) para o modo claro trocar a paleta
// inteira sem mexer em nenhuma classe. O formato "R G B" é o que o Tailwind precisa
// para continuar aceitando opacidade (bg-surface/50).
const token = (name) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: token('background'),
        surface: {
          lowest: token('surface-lowest'),
          low: token('surface-low'),
          DEFAULT: token('surface'),
          high: token('surface-high'),
          highest: token('surface-highest'),
          bright: token('surface-bright'),
        },
        brand: {
          DEFAULT: token('brand'),
          soft: token('brand-soft'),
          deep: token('brand-deep'),
          laser: token('brand-laser'),
          ink: token('brand-ink'),
        },
        outline: {
          DEFAULT: token('outline'),
          variant: token('outline-variant'),
        },
        content: {
          DEFAULT: token('content'),
          muted: token('content-muted'),
          variant: token('content-variant'),
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
        glow: '0 0 12px rgb(var(--brand) / 0.45)',
      },
    },
  },
  plugins: [],
};
