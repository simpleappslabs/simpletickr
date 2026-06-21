<script lang="ts">
    import { onMount } from 'svelte';
    import { syncPrices, syncFxRates, getSyncHistory } from '$lib/api/sdk.gen';
    import type { SyncResult, SyncHistoryEntry } from '$lib/api/types.gen';
    import SyncHistoryTable from '$lib/SyncHistoryTable.svelte';
    import '$lib/client';

    let pricesSyncing = $state(false);
    let pricesResult = $state<SyncResult | null>(null);
    let pricesError = $state<string | null>(null);
    let priceHistory = $state<SyncHistoryEntry[]>([]);

    let fxSyncing = $state(false);
    let fxResult = $state<SyncResult | null>(null);
    let fxError = $state<string | null>(null);
    let fxHistory = $state<SyncHistoryEntry[]>([]);

    onMount(async () => {
        const [ph, fh] = await Promise.all([
            getSyncHistory({ query: { type: 'PRICE' } }),
            getSyncHistory({ query: { type: 'FX' } }),
        ]);
        priceHistory = ph.data ?? [];
        fxHistory = fh.data ?? [];
    });

    async function refreshPriceHistory() {
        const { data } = await getSyncHistory({ query: { type: 'PRICE' } });
        priceHistory = data ?? [];
    }

    async function refreshFxHistory() {
        const { data } = await getSyncHistory({ query: { type: 'FX' } });
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

<div class="max-w-4xl mx-auto p-6 space-y-8">
    <h1 class="text-2xl font-bold">Admin</h1>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <!-- Price sync -->
        <div class="bg-base-200 rounded-box p-6 space-y-4">
            <div>
                <h2 class="text-lg font-semibold">Price sync</h2>
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

        <!-- FX sync -->
        <div class="bg-base-200 rounded-box p-6 space-y-4">
            <div>
                <h2 class="text-lg font-semibold">FX rate sync</h2>
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
</div>
