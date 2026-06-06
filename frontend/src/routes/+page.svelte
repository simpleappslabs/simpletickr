<script lang="ts">
  import { onMount } from 'svelte';
  import { listPortfolios, createPortfolio } from '$lib/api/sdk.gen';
  import type { Portfolio } from '$lib/api/types.gen';
  import '$lib/client';

  let portfolios = $state<Portfolio[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let newName = $state('');
  let creating = $state(false);
  let createError = $state<string | null>(null);

  async function load() {
    loading = true;
    error = null;
    const { data, error: err } = await listPortfolios();
    if (err) {
      error = 'Failed to load portfolios.';
    } else {
      portfolios = data ?? [];
    }
    loading = false;
  }

  async function handleCreate(e: Event) {
    e.preventDefault();
    if (!newName.trim()) return;
    creating = true;
    createError = null;
    const { error: err } = await createPortfolio({ body: { name: newName.trim() } });
    if (err) {
      createError = 'Failed to create portfolio.';
    } else {
      newName = '';
      await load();
    }
    creating = false;
  }

  onMount(load);
</script>

<main>
  <h1>simpletickr</h1>

  <section>
    <h2>Portfolios</h2>

    {#if loading}
      <p>Loading…</p>
    {:else if error}
      <p class="error">{error}</p>
    {:else if portfolios.length === 0}
      <p class="empty">No portfolios yet. Create one below.</p>
    {:else}
      <ul>
        {#each portfolios as portfolio}
          <li>
            <a href="/portfolios/{portfolio.id}">{portfolio.name}</a>
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section>
    <h2>New portfolio</h2>
    <form onsubmit={handleCreate}>
      <input
        type="text"
        placeholder="Portfolio name"
        bind:value={newName}
        disabled={creating}
        required
      />
      <button type="submit" disabled={creating || !newName.trim()}>
        {creating ? 'Creating…' : 'Create'}
      </button>
    </form>
    {#if createError}
      <p class="error">{createError}</p>
    {/if}
  </section>
</main>

<style>
  main {
    max-width: 640px;
    margin: 2rem auto;
    padding: 0 1rem;
    font-family: sans-serif;
  }

  h1 {
    font-size: 1.5rem;
    margin-bottom: 2rem;
  }

  h2 {
    font-size: 1rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: #666;
    margin-bottom: 0.75rem;
  }

  section {
    margin-bottom: 2rem;
  }

  ul {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  li {
    border-bottom: 1px solid #eee;
  }

  li a {
    display: block;
    padding: 0.75rem 0;
    color: inherit;
    text-decoration: none;
  }

  li a:hover {
    color: #0066cc;
  }

  form {
    display: flex;
    gap: 0.5rem;
  }

  input[type='text'] {
    flex: 1;
    padding: 0.5rem 0.75rem;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 0.9rem;
  }

  button {
    padding: 0.5rem 1rem;
    background: #0066cc;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.9rem;
    cursor: pointer;
  }

  button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .error {
    color: #c00;
    font-size: 0.9rem;
    margin-top: 0.5rem;
  }

  .empty {
    color: #999;
    font-style: italic;
  }
</style>
