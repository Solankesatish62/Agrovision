/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#16a34a', // green-600
          dark: '#15803d',
          light: '#22c55e',
        },
        secondary: {
          DEFAULT: '#1e40af', // blue-800
          dark: '#1e3a8a',
          light: '#3b82f6',
        }
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
