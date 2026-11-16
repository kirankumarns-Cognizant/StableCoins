import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { readConnections, publicConnections } = require('../services/config/read-connections.cjs') as {
  readConnections: (environment: Record<string, string | undefined>) => Record<string, string>
  publicConnections: (properties: Record<string, string>) => unknown
}
const repositoryRoot = fileURLToPath(new URL('../', import.meta.url))

export default defineConfig(({ mode }) => {
 const settings=readConnections({...loadEnv(mode,repositoryRoot,''),...process.env})
 return ({
  server:{host:'0.0.0.0',port:5173,strictPort:true,allowedHosts:settings['frontend.dev.allowed-hosts'].split(',').map(host=>host.trim()).filter(Boolean),proxy:{'/api/v1':{target:settings['connections.services.payment-url'],changeOrigin:true}}},
  plugins: [react(), {
    name: 'stableflow-local-readiness',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/__stablecoins_dev_health', (_request, response) => {
        response.setHeader('Content-Type', 'application/json')
        response.setHeader('Cache-Control', 'no-store')
        response.end(JSON.stringify({ application: 'stableflow-vite-development' }))
      })
    },
  }],
  // Only the explicit public allowlist enters the browser bundle.
  define: {
    __APP_CONNECTIONS__: JSON.stringify(publicConnections(readConnections({
      ...loadEnv(mode, repositoryRoot, ''), ...process.env,
    }))),
  },
})})
