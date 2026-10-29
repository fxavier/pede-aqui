import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { securityHeaders } from './src/lib/security/headers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const opts = { apiBaseUrl: env.VITE_API_BASE_URL, keycloakUrl: env.VITE_KEYCLOAK_URL }
  // `vite preview` serves the production bundle over plain http locally, so HSTS is left out there
  // too; production sets the same headers (plus HSTS) at the CDN — see docs/local-development.md.
  const previewHeaders = securityHeaders({ ...opts, dev: false })
  delete previewHeaders['Strict-Transport-Security']

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: { headers: securityHeaders({ ...opts, dev: true }) },
    preview: { headers: previewHeaders },
  }
})
