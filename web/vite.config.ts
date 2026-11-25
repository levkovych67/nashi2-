import react from '@vitejs/plugin-react'
import tailwind from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import { viteStaticCopy } from 'vite-plugin-static-copy'

// Fixture audio/images are served at /fixtures/** so MSW-returned URLs resolve in the browser.
export default defineConfig({
  plugins: [
    react(),
    tailwind(),
    viteStaticCopy({
      targets: [
        { src: '../contracts/fixtures/audio', dest: 'fixtures' },
        { src: '../contracts/fixtures/img', dest: 'fixtures' },
        { src: '../contracts/geo/*.{json,geojson}', dest: 'geo' },
      ],
    }),
  ],
  resolve: {
    alias: { '@contracts': fileURLToPath(new URL('../contracts', import.meta.url)) },
  },
  // maplibre-gl alone is ~1 MB minified; it is loaded lazily with the map route.
  build: { chunkSizeWarningLimit: 1100 },
  server: {
    port: 5174,
    strictPort: true,
    fs: { allow: ['..'] },
    proxy: {
      '/api': process.env.VITE_DEV_API_PROXY ?? 'http://127.0.0.1:8081',
    },
  },
})
