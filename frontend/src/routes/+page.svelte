<script lang="ts">
  import { onMount } from 'svelte';
  import { getHealth } from '$lib/api/sdk.gen';
  import '$lib/client';

  let status = $state<string>('loading…');

  onMount(async () => {
    const { data, error } = await getHealth();
    status = error ? `error: ${JSON.stringify(error)}` : JSON.stringify(data);
  });
</script>

<h1>simpletickr</h1>
<p>API health: {status}</p>
