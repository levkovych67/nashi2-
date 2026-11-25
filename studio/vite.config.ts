import react from '@vitejs/plugin-react'
import tailwind from '@tailwindcss/vite'
import { defineConfig } from 'vitest/config'
import { viteStaticCopy } from 'vite-plugin-static-copy'

export default defineConfig({
  plugins: [
    react(),
    tailwind(),
    viteStaticCopy({
      targets: [
        { src: '../contracts/fixtures/*', dest: 'fixtures' },
        { src: '../contracts/geo/*.{json,geojson}', dest: 'geo' },
      ],
    }),
  ],
  server: { fs: { allow: ['..'] } },
  test: { environment: 'jsdom', include: ['tests/unit/**/*.test.{ts,tsx}'] },
})
