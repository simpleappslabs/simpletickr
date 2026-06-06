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

<div class="max-w-2xl mx-auto p-6 space-y-8">
  <section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Portfolios</h2>

    {#if loading}
      <span class="loading loading-spinner loading-sm"></span>
    {:else if error}
      <div class="alert alert-error">
        <span>{error}</span>
      </div>
    {:else if portfolios.length === 0}
      <p class="text-base-content/40 italic text-sm">No portfolios yet. Create one below.</p>
    {:else}
      <ul class="menu bg-base-200 rounded-box w-full">
        {#each portfolios as portfolio}
          <li>
            <a href="/portfolios/{portfolio.id}">{portfolio.name}</a>
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">New portfolio</h2>
    <form onsubmit={handleCreate} class="flex gap-2">
      <input
        type="text"
        placeholder="Portfolio name"
        bind:value={newName}
        disabled={creating}
        required
        class="input input-bordered flex-1"
      />
      <button type="submit" disabled={creating || !newName.trim()} class="btn btn-primary">
        {creating ? 'Creating…' : 'Create'}
      </button>
    </form>
    {#if createError}
      <div class="alert alert-error">
        <span>{createError}</span>
      </div>
    {/if}
  </section>
</div>
