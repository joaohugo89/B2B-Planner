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
      // A escala sobe um degrau em relação ao padrão do Tailwind: o planner é feito de
      // etiquetas e metadados, e com 12px de corpo a tela virava um formulário de leitura
      // difícil. Em rem, para acompanhar o tamanho de fonte da raiz (ver index.css).
      fontSize: {
        xs: ['0.8125rem', { lineHeight: '1.15rem' }],
        sm: ['0.9375rem', { lineHeight: '1.45rem' }],
        base: ['1.0625rem', { lineHeight: '1.7rem' }],
        lg: ['1.1875rem', { lineHeight: '1.8rem' }],
        xl: ['1.375rem', { lineHeight: '1.9rem' }],
        '2xl': ['1.625rem', { lineHeight: '2.1rem' }],
        '3xl': ['2.0625rem', { lineHeight: '2.4rem' }],
        '4xl': ['2.5rem', { lineHeight: '2.8rem' }],
        '5xl': ['3.25rem', { lineHeight: '1.05' }],
      },
      maxWidth: {
        // Em rem de propósito: num monitor grande a raiz cresce e o conteúdo cresce com ela.
        shell: '110rem',
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
