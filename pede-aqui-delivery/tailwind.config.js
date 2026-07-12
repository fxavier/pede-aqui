/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        // Ported from pede-aqui-delivery-ui: Plus Jakarta Sans is both the
        // display and body face; Fraunces kept as an optional serif fallback.
        display: ['Plus Jakarta Sans', 'DM Sans', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        body: ['Plus Jakarta Sans', 'DM Sans', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        serif: ['Fraunces', 'serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
      },
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        /* Brand palette ported from pede-aqui-delivery-ui (rose scale) */
        brand: {
          50: '#fff1f2',
          100: '#ffe4e6',
          200: '#fecdd3',
          300: '#fda4af',
          400: '#fb7185',
          500: '#f43f5e',
          600: '#e11d48',
          700: '#be123c',
          800: '#9f1239',
          900: '#881337',
        },
        'accent-orange': '#FF5A1F',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        xl: 'calc(var(--radius) + 4px)',
        '2xl': 'calc(var(--radius) + 8px)',
      },
      boxShadow: {
        warm: '0 2px 12px -2px rgba(60,30,5,0.10), 0 1px 3px rgba(60,30,5,0.06)',
        'warm-md': '0 8px 24px -4px rgba(60,30,5,0.12), 0 2px 6px rgba(60,30,5,0.06)',
        'warm-lg': '0 16px 48px -8px rgba(60,30,5,0.16)',
      },
      keyframes: {
        'fade-up': {
          '0%':   { opacity: '0', transform: 'translateY(18px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          '0%':   { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'scale-in': {
          '0%':   { opacity: '0', transform: 'scale(0.95)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        'slide-in-right': {
          '0%':   { transform: 'translateX(100%)' },
          '100%': { transform: 'translateX(0)' },
        },
        'spin-slow': {
          '0%':   { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
      },
      animation: {
        'fade-up':        'fade-up 0.5s ease forwards',
        'fade-in':        'fade-in 0.4s ease forwards',
        'scale-in':       'scale-in 0.3s ease forwards',
        'slide-in-right': 'slide-in-right 0.3s cubic-bezier(0.16,1,0.3,1) forwards',
        'spin-slow':      'spin-slow 4s linear infinite',
      },
    },
  },
  plugins: [],
}
