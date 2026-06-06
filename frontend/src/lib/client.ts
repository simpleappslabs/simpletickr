import { client } from '$lib/api/client.gen';
import { PUBLIC_API_BASE_URL } from '$env/static/public';

client.setConfig({ baseUrl: PUBLIC_API_BASE_URL });

export { client };
