/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        dhanpay: {
          navy: '#0F172A',
          petrol: '#05334D',
          petrolDark: '#032539',
          petrolInput: '#0D445F',
          mint: '#00D084',
          mintBright: '#00C896',
          ice: '#DDF2F1',
          iceDeep: '#CBEAEA',
          royal: '#05334D',
          saffron: '#00D084',
        },
      },
      boxShadow: {
        card: '0 10px 30px -10px rgba(15, 23, 42, 0.08)',
        floating: '0 20px 50px -12px rgba(5, 51, 77, 0.25)',
      },
    },
  },
  plugins: [],
};
