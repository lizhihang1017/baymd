/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: '#ECFEFF',
        panel: '#FFFFFF',
        'text-primary': '#164E63',
        accent: '#0891B2',
        'accent-bright': '#22D3EE',
        'accent-dark': '#0E7490',
        'accent-green': '#059669',
        vital: '#DC2626',
        warn: '#D97706',
        muted: '#64748B',
        border: '#A5F3FC',
        'bg-deep': '#0F2A33',
        // ===== 用户端(ChatGPT 中性风)=====
        'u-bg': '#F7F7F8',
        'u-sidebar': '#171717',
        'u-text': '#1F2937',
        'u-muted': '#8E8E93',
        'u-border': '#E5E5E5',
        'u-input': '#FFFFFF',
        'u-send': '#1F2937',
        'u-send-hover': '#111827',
        'u-user-bubble': '#1F2937',
        'u-assistant-bubble': '#FFFFFF',
        'u-amber': '#D97706',
      },
      fontFamily: {
        display: ['Figtree', 'sans-serif'],
        body: ['Noto Sans', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      animation: {
        ekg: 'ekg-wave 1.6s ease-in-out infinite',
        'fade-in': 'fade-in 0.4s ease-out',
      },
      keyframes: {
        'ekg-wave': {
          '0%': { transform: 'scaleX(0.3)', opacity: '0.4' },
          '30%': { transform: 'scaleX(1.2)', opacity: '1' },
          '50%': { transform: 'scaleX(0.6)', opacity: '0.7' },
          '70%': { transform: 'scaleX(1.0)', opacity: '0.9' },
          '100%': { transform: 'scaleX(0.3)', opacity: '0.4' },
        },
        'fade-in': {
          from: { opacity: '0', transform: 'translateY(4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
}
