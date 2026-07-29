import { client } from '$lib/api/client.gen';
import { env } from '$env/dynamic/public';

client.setConfig({ baseUrl: env.PUBLIC_API_BASE_URL, credentials: 'include' });

export { client };
