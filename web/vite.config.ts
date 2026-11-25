import react from '@vitejs/plugin-react'
import tailwind from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import { viteStaticCopy } from 'vite-plugin-static-copy'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), 'VITE_')
  // Test files — fixture audio/images (served at /fixtures/** so MSW-returned URLs resolve) and the MSW worker in
  // public/ — belong to the dev server and mock builds (VITE_API_MOCK=1) only, never to a production build.
  const mock = command === 'serve' || env.VITE_API_MOCK === '1'
  const fixtures = [
    { src: '../contracts/fixtures/audio', dest: 'fixtures' },
    { src: '../contracts/fixtures/img', dest: 'fixtures' },
  ]

  return {
    plugins: [
      react(),
      tailwind(),
      viteStaticCopy({
        targets: [...(mock ? fixtures : []), { src: '../contracts/geo/*.{json,geojson}', dest: 'geo' }],
      }),
    ],
    publicDir: mock ? 'public' : false,
    resolve: {
      alias: { '@contracts': fileURLToPath(new URL('../contracts', import.meta.url)) },
    },
    // maplibre-gl alone is ~1 MB minified; it is loaded lazily with the map route.
    build: {
      chunkSizeWarningLimit: 1100,
      manifest: true,
      rollupOptions: {
        output: {
          // maplibre-gl + react-map-gl in one chunk of their own, imported only by the map route
          // React stays out of it: otherwise the maplibre chunk absorbs React and every route has to load the map code.
          manualChunks: (id) => {
            if (/node_modules\/(maplibre-gl|react-map-gl|@maplibre)\//.test(id)) return 'maplibre'
            // vite's dynamic-import helper too: every route uses it, and it would otherwise land in the maplibre chunk
            if (id.includes('vite/preload-helper') || /node_modules\/(react|react-dom|scheduler)\//.test(id)) return 'vendor'
            return undefined
          },
        },
      },
    },
    server: {
      port: 5174,
      strictPort: true,
      fs: { allow: ['..'] },
      proxy: {
        '/api': {
          target: process.env.VITE_DEV_API_PROXY ?? 'http://127.0.0.1:8081',
          changeOrigin: true,
          // Through the proxy the API is same-origin for the page, but the browser still sends Origin on a POST
          // (http://127.0.0.1:5174) while the backend sees its own host: Spring then treats it as a CORS request from
          // an origin it does not list and answers 403. Without Origin it is the same-origin request it really is.
          configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
        },
      },
    },
  }
})
