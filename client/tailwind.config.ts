import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './pages/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
    './app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        'sv-black':   '#0a0a0a',
        'sv-dark':    '#111111',
        'sv-card':    '#1a1a1a',
        'sv-border':  '#2a2a2a',
        'sv-muted':   '#666666',
        'sv-white':   '#f5f5f5',
        'sv-accent':  '#e50914',
        'sv-accent2': '#ff6b6b',
      },
      fontFamily: {
        display: ['var(--font-display)', 'sans-serif'],
        body:    ['var(--font-body)', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

export default config;
