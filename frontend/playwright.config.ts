import { defineConfig } from '@playwright/test';

export default defineConfig({
	webServer: {
		command: 'npm run codegen && npm run build && npm run preview',
		port: 4173,
		timeout: 120_000,
	},
	use: {
		baseURL: 'http://localhost:4173',
	},
	testMatch: '**/*.e2e.{ts,js}',
});
