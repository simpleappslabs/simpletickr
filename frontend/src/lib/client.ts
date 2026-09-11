import { client } from '$lib/api/client.gen';
import { env } from '$env/dynamic/public';

export const apiBaseUrl = env.PUBLIC_API_BASE_URL || '/api';

client.setConfig({ baseUrl: apiBaseUrl, credentials: 'include' });

export { client };
