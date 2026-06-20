import { defineConfig } from '@playwright/test';

const isCI = !!process.env.CI;

export default defineConfig({
	webServer: [
		{
			command: './gradlew bootRun',
			cwd: '../backend',
			port: 8081,
			timeout: 180_000,
			reuseExistingServer: !isCI,
			env: isCI
				? { SPRING_PROFILES_ACTIVE: 'e2etest', SERVER_PORT: '8081' }
				: {
						SPRING_PROFILES_ACTIVE: 'e2etest',
						SERVER_PORT: '8081',
						DB_PORT: '5435',
						DB_NAME: 'simpletickr_test',
						DB_USER: 'simpletickr',
						DB_PASSWORD: 'simpletickr',
					},
		},
		{
			command: 'npm run codegen && npm run build && npm run preview',
			port: 4173,
			timeout: 120_000,
			reuseExistingServer: !isCI,
			env: { PUBLIC_API_BASE_URL: 'http://localhost:8081' },
		},
	],
	use: {
		baseURL: 'http://localhost:4173',
	},
	testMatch: '**/*.e2e.{ts,js}',
});
