import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi.yaml',
  output: {
    path: 'src/lib/api',
    clean: true,
  },
  plugins: [
    '@hey-api/client-fetch',
    '@hey-api/typescript',
    '@hey-api/sdk',
  ],
});
