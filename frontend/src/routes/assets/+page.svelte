<script lang="ts">
  import { onMount } from 'svelte';
  import { listAssets, createAsset } from '$lib/api/sdk.gen';
  import type { Asset, AssetType } from '$lib/api/types.gen';
  import '$lib/client';

  const ASSET_TYPES: AssetType[] = ['STOCK', 'ETF', 'CRYPTO', 'OTHER'];

  let assets = $state<Asset[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);

  let ticker = $state('');
  let name = $state('');
  let type = $state<AssetType>('STOCK');
  let currency = $state('USD');
  let creating = $state(false);
  let createError = $state<string | null>(null);

  async function load() {
    loading = true;
    error = null;
    const { data, error: err } = await listAssets();
    if (err) {
      error = 'Failed to load assets.';
    } else {
      assets = data ?? [];
    }
    loading = false;
  }

  async function handleCreate(e: Event) {
    e.preventDefault();
    if (!ticker.trim() || !name.trim() || !currency.trim()) return;
    creating = true;
    createError = null;
    const { error: err } = await createAsset({
      body: { ticker: ticker.trim().toUpperCase(), name: name.trim(), type, currency: currency.trim().toUpperCase() },
    });
    if (err) {
      createError = 'Failed to add asset.';
    } else {
      ticker = '';
      name = '';
      type = 'STOCK';
      currency = 'USD';
      await load();
    }
    creating = false;
  }

  onMount(load);
</script>

<div class="max-w-4xl mx-auto p-6 space-y-8">
  <h1 class="text-2xl font-bold">Assets</h1>

  <section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Catalog</h2>

    {#if loading}
      <span class="loading loading-spinner loading-sm"></span>
    {:else if error}
      <div class="alert alert-error"><span>{error}</span></div>
    {:else if assets.length === 0}
      <p class="text-base-content/40 italic text-sm">No assets yet.</p>
    {:else}
      <div class="overflow-x-auto">
        <table class="table table-zebra w-full">
          <thead>
            <tr>
              <th>Ticker</th>
              <th>Name</th>
              <th>Type</th>
              <th>Currency</th>
            </tr>
          </thead>
          <tbody>
            {#each assets as asset}
              <tr>
                <td class="font-mono font-semibold">{asset.ticker}</td>
                <td>{asset.name}</td>
                <td><span class="badge badge-ghost">{asset.type}</span></td>
                <td>{asset.currency}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </section>

  <section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Add asset</h2>
    <form onsubmit={handleCreate} class="grid grid-cols-2 gap-3 max-w-lg">
      <input
        type="text"
        placeholder="Ticker (e.g. AAPL)"
        bind:value={ticker}
        disabled={creating}
        required
        class="input input-bordered col-span-1"
      />
      <input
        type="text"
        placeholder="Currency (e.g. USD)"
        bind:value={currency}
        disabled={creating}
        required
        class="input input-bordered col-span-1"
      />
      <input
        type="text"
        placeholder="Name (e.g. Apple Inc.)"
        bind:value={name}
        disabled={creating}
        required
        class="input input-bordered col-span-2"
      />
      <select bind:value={type} disabled={creating} class="select select-bordered col-span-1">
        {#each ASSET_TYPES as t}
          <option value={t}>{t}</option>
        {/each}
      </select>
      <button
        type="submit"
        disabled={creating || !ticker.trim() || !name.trim() || !currency.trim()}
        class="btn btn-primary col-span-1"
      >
        {creating ? 'Adding…' : 'Add asset'}
      </button>
    </form>
    {#if createError}
      <div class="alert alert-error"><span>{createError}</span></div>
    {/if}
  </section>
</div>
