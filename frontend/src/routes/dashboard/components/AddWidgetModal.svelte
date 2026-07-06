<script lang="ts">
    import '$lib/client';
    import { listAssets, listPortfolios } from '$lib/api/sdk.gen';
    import type { DashboardWidgetType, Asset, Portfolio } from '$lib/api/types.gen';
    import { onMount } from 'svelte';

    let { open, onsubmit, oncancel }: {
        open: boolean;
        onsubmit: (type: DashboardWidgetType, targetId: number, range: string) => Promise<void>;
        oncancel: () => void;
    } = $props();

    let assets = $state<Asset[]>([]);
    let portfolios = $state<Portfolio[]>([]);
    let loading = $state(true);
    let submitting = $state(false);
    let error = $state<string | null>(null);

    let selectedType = $state<DashboardWidgetType>('LISTING_PRICE');
    let selectedListingId = $state<number | null>(null);
    let selectedPortfolioId = $state<number | null>(null);

    const listings = $derived(assets.flatMap(a => a.listings.map(l => ({ ...l, assetName: a.name }))));

    onMount(async () => {
        const [assetsRes, portfoliosRes] = await Promise.all([listAssets(), listPortfolios()]);
        assets = assetsRes.data ?? [];
        portfolios = portfoliosRes.data ?? [];
        if (listings.length > 0) selectedListingId = listings[0].id;
        if (portfolios.length > 0) selectedPortfolioId = portfolios[0].id;
        loading = false;
    });

    $effect(() => {
        if (!open) {
            selectedType = 'LISTING_PRICE';
            error = null;
        }
    });

    async function handleSubmit() {
        const targetId = selectedType === 'LISTING_PRICE' ? selectedListingId : selectedPortfolioId;
        if (!targetId) return;

        submitting = true;
        error = null;
        try {
            await onsubmit(selectedType, targetId, '1M');
        } catch {
            error = 'Failed to add widget.';
        } finally {
            submitting = false;
        }
    }
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') oncancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-4">Add widget</h3>

        {#if loading}
            <div class="flex justify-center py-8"><span class="loading loading-spinner"></span></div>
        {:else}
            <div class="space-y-4">
                <div>
                    <p class="label"><span class="label-text">Widget type</span></p>
                    <div class="join w-full">
                        <button
                            class="join-item btn flex-1"
                            class:btn-primary={selectedType === 'LISTING_PRICE'}
                            class:btn-ghost={selectedType !== 'LISTING_PRICE'}
                            onclick={() => { selectedType = 'LISTING_PRICE'; }}
                        >
                            Listing price
                        </button>
                        <button
                            class="join-item btn flex-1"
                            class:btn-primary={selectedType === 'PORTFOLIO_VALUE'}
                            class:btn-ghost={selectedType !== 'PORTFOLIO_VALUE'}
                            onclick={() => { selectedType = 'PORTFOLIO_VALUE'; }}
                        >
                            Portfolio value
                        </button>
                    </div>
                </div>

                {#if selectedType === 'LISTING_PRICE'}
                    <div>
                        <label class="label" for="listing-select"><span class="label-text">Listing</span></label>
                        {#if listings.length === 0}
                            <p class="text-base-content/50 text-sm italic">No listings available.</p>
                        {:else}
                            <select
                                id="listing-select"
                                class="select select-bordered w-full"
                                bind:value={selectedListingId}
                            >
                                {#each listings as l}
                                    <option value={l.id}>{l.ticker}{l.exchange ? ` (${l.exchange})` : ''} — {l.assetName}</option>
                                {/each}
                            </select>
                        {/if}
                    </div>
                {:else}
                    <div>
                        <label class="label" for="portfolio-select"><span class="label-text">Portfolio</span></label>
                        {#if portfolios.length === 0}
                            <p class="text-base-content/50 text-sm italic">No portfolios available.</p>
                        {:else}
                            <select
                                id="portfolio-select"
                                class="select select-bordered w-full"
                                bind:value={selectedPortfolioId}
                            >
                                {#each portfolios as p}
                                    <option value={p.id}>{p.name}</option>
                                {/each}
                            </select>
                        {/if}
                    </div>
                {/if}

                {#if error}
                    <div class="alert alert-error"><span>{error}</span></div>
                {/if}
            </div>

            <div class="modal-action">
                <button class="btn btn-ghost" disabled={submitting} onclick={oncancel}>Cancel</button>
                <button class="btn btn-primary" disabled={submitting} onclick={handleSubmit}>
                    {submitting ? 'Adding…' : 'Add widget'}
                </button>
            </div>
        {/if}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
