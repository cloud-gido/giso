import path from 'node:path';
import { defineConfig } from 'vite';

export default defineConfig({
  resolve: {
    alias: {
      '@giso/tracker-web': path.resolve(__dirname, '../../sdk/web/src/index.ts'),
    },
  },
  server: {
    port: 5180,
    host: true,
  },
});
