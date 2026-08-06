import { request } from '@playwright/test';

const AUTH_FILE = 'tests/.auth/storage-state.json';

export default async function globalSetup() {
	const requestContext = await request.newContext({ baseURL: 'http://localhost:8081' });
	const response = await requestContext.post('/api/auth/login', {
		data: { username: 'e2e-admin', password: 'TestPassword123!' },
	});
	if (!response.ok()) {
		throw new Error(`E2E login setup failed: ${response.status()} ${await response.text()}`);
	}
	await requestContext.storageState({ path: AUTH_FILE });
	await requestContext.dispose();
}
