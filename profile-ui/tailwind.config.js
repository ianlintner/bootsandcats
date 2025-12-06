const themePreset = require("@ianlintner/theme/tailwind.config");

/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [themePreset],
  content: [
    "./src/main/resources/templates/**/*.{html,thymeleaf}",
    "./src/main/frontend/**/*.{html,js,css}",
    "./src/main/resources/static/**/*.{html,js}",
    "./node_modules/@ianlintner/theme/dist/**/*.{js,mjs}"
  ]
};
