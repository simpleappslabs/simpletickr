<script lang="ts">
  import { onMount } from 'svelte';
  import { getSettings, updateSettings, syncPrices, syncFxRates, getSyncHistory } from '$lib/api/sdk.gen';
  import type { SyncResult, SyncHistoryEntry } from '$lib/api/types.gen';
  import SyncHistoryTable from '$lib/SyncHistoryTable.svelte';
  import '$lib/client';

  let baseCurrency = $state('');
  let loading = $state(true);
  let submitting = $state(false);
  let loadError = $state<string | null>(null);
  let saveError = $state<string | null>(null);
  let saved = $state(false);

  let pricesSyncing = $state(false);
  let pricesResult = $state<SyncResult | null>(null);
  let pricesError = $state<string | null>(null);
  let priceHistory = $state<SyncHistoryEntry[]>([]);

  let fxSyncing = $state(false);
  let fxResult = $state<SyncResult | null>(null);
  let fxError = $state<string | null>(null);
  let fxHistory = $state<SyncHistoryEntry[]>([]);

  onMount(async () => {
    const [settingsRes, ph, fh] = await Promise.all([
      getSettings(),
      getSyncHistory({ query: { type: 'PRICE', limit: 5 } }),
      getSyncHistory({ query: { type: 'FX', limit: 5 } }),
    ]);
    if (settingsRes.error) {
      loadError = 'Failed to load settings.';
    } else {
      baseCurrency = settingsRes.data?.baseCurrency ?? '';
    }
    priceHistory = ph.data ?? [];
    fxHistory = fh.data ?? [];
    loading = false;
  });

  async function handleSubmit() {
    submitting = true;
    saveError = null;
    saved = false;
    const { error } = await updateSettings({ body: { baseCurrency } });
    if (error) {
      saveError = 'Failed to save settings. Make sure the currency code is a valid ISO 4217 code (e.g. EUR, USD, GBP).';
    } else {
      saved = true;
    }
    submitting = false;
  }

  async function refreshPriceHistory() {
    const { data } = await getSyncHistory({ query: { type: 'PRICE', limit: 5 } });
    priceHistory = data ?? [];
  }

  async function refreshFxHistory() {
    const { data } = await getSyncHistory({ query: { type: 'FX', limit: 5 } });
    fxHistory = data ?? [];
  }

  async function triggerPriceSync() {
    pricesSyncing = true;
    pricesResult = null;
    pricesError = null;
    const res = await syncPrices();
    if (res.error) {
      pricesError = 'Price sync failed.';
    } else {
      pricesResult = res.data ?? null;
      await refreshPriceHistory();
    }
    pricesSyncing = false;
  }

  async function triggerFxSync() {
    fxSyncing = true;
    fxResult = null;
    fxError = null;
    const res = await syncFxRates();
    if (res.error) {
      fxError = 'FX sync failed.';
    } else {
      fxResult = res.data ?? null;
      await refreshFxHistory();
    }
    fxSyncing = false;
  }
</script>

<div class="max-w-4xl mx-auto p-4 sm:p-6 space-y-8">
  <h1 class="text-2xl font-bold">Settings</h1>

  {#if loading}
    <span class="loading loading-spinner loading-sm"></span>
  {:else if loadError}
    <div class="alert alert-error"><span>{loadError}</span></div>
  {:else}
    <form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-6">
      <section class="space-y-4">
        <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Portfolio</h2>
        <div class="flex flex-col gap-1 max-w-xs">
          <label class="text-sm font-medium" for="base-currency">Base currency</label>
          <p class="text-xs text-base-content/50">All holdings and gains are converted to this currency.</p>
          <input
            id="base-currency"
            type="text"
            class="input input-bordered w-28 font-mono uppercase"
            maxlength="3"
            placeholder="EUR"
            bind:value={baseCurrency}
            oninput={(e) => { baseCurrency = (e.currentTarget as HTMLInputElement).value.toUpperCase(); }}
            required
          />
        </div>
      </section>

      {#if saveError}
        <div class="alert alert-error text-sm"><span>{saveError}</span></div>
      {/if}
      {#if saved}
        <div class="alert alert-success text-sm"><span>Settings saved.</span></div>
      {/if}

      <button type="submit" class="btn btn-primary btn-sm" disabled={submitting}>
        {submitting ? 'Saving…' : 'Save'}
      </button>
    </form>

    <section class="space-y-4">
      <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Data sync</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <div class="bg-base-200 rounded-box p-6 space-y-4">
          <div>
            <h3 class="text-lg font-semibold">Price sync</h3>
            <p class="text-sm text-base-content/60">Fetch latest prices from Yahoo Finance for all mapped listings.</p>
          </div>
          <button class="btn btn-primary btn-sm" onclick={triggerPriceSync} disabled={pricesSyncing}>
            {#if pricesSyncing}
              <span class="loading loading-spinner loading-xs"></span> Syncing…
            {:else}
              Sync prices
            {/if}
          </button>
          {#if pricesError}
            <div class="alert alert-error py-2 text-sm"><span>{pricesError}</span></div>
          {:else if pricesResult}
            <div class="text-sm">
              <span class="text-success font-semibold">{pricesResult.synced} synced</span>
              {#if pricesResult.failed > 0}
                · <span class="text-error font-semibold">{pricesResult.failed} failed</span>
              {/if}
            </div>
          {/if}
          <SyncHistoryTable entries={priceHistory} />
        </div>

        <div class="bg-base-200 rounded-box p-6 space-y-4">
          <div>
            <h3 class="text-lg font-semibold">FX rate sync</h3>
            <p class="text-sm text-base-content/60">Fetch latest exchange rates for all currencies in your listings.</p>
          </div>
          <button class="btn btn-primary btn-sm" onclick={triggerFxSync} disabled={fxSyncing}>
            {#if fxSyncing}
              <span class="loading loading-spinner loading-xs"></span> Syncing…
            {:else}
              Sync FX rates
            {/if}
          </button>
          {#if fxError}
            <div class="alert alert-error py-2 text-sm"><span>{fxError}</span></div>
          {:else if fxResult}
            <div class="text-sm">
              <span class="text-success font-semibold">{fxResult.synced} synced</span>
              {#if fxResult.failed > 0}
                · <span class="text-error font-semibold">{fxResult.failed} failed</span>
              {/if}
            </div>
          {/if}
          <SyncHistoryTable entries={fxHistory} />
        </div>
      </div>
    </section>
  {/if}
</div>
