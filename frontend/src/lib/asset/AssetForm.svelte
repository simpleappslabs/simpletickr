<script lang="ts">
    import {
        createAsset, updateAsset, getAsset,
        createListing, updateListing, deleteListing,
        setPriceMapping, deletePriceMapping,
        searchListings,
    } from '$lib/api/sdk.gen';
    import type { AssetDetail, AssetType, ListingSearchResult } from '$lib/api/types.gen';
    import ListingForm, { type ListingData } from '$lib/asset/ListingForm.svelte';
    import type { Mapping } from '$lib/asset/PriceMappingForm.svelte';

    const ASSET_TYPES: AssetType[] = ['STOCK', 'ETF', 'CRYPTO', 'OTHER'];

    interface Props {
        asset?: AssetDetail | null;
        onSuccess: (asset: AssetDetail) => void;
        onCancel: () => void;
    }

    const { asset = null, onSuccess, onCancel }: Props = $props();

    let name = $state('');
    let isin = $state('');
    let type = $state<AssetType>('STOCK');
    let error = $state<string | null>(null);
    let submitting = $state(false);
    let listings = $state<ListingData[]>([]);

    let removedIds: number[] = [];
    let nextTempId = -1;

    let searchQuery = $state('');
    let searchResults = $state<ListingSearchResult[]>([]);
    let searchDebounce: ReturnType<typeof setTimeout> | null = null;
    let searchOpen = $state(false);

    function onSearchInput() {
        if (searchDebounce) clearTimeout(searchDebounce);
        if (!searchQuery.trim()) { searchResults = []; searchOpen = false; return; }
        searchDebounce = setTimeout(async () => {
            const { data } = await searchListings({ query: { q: searchQuery } });
            searchResults = data ?? [];
            searchOpen = searchResults.length > 0;
        }, 300);
    }

    function applySearchResult(result: ListingSearchResult) {
        name = result.name;
        type = result.type;
        listings = [{
            tempId: nextTempId--,
            ticker: result.symbol,
            exchange: result.exchange ?? '',
            currency: result.currency ?? '',
            mappings: [{ provider: 'YAHOO', externalId: result.symbol }],
            originalMappings: [],
        }];
        searchQuery = '';
        searchResults = [];
        searchOpen = false;
    }

    $effect(() => {
        name = asset?.name ?? '';
        isin = asset?.isin ?? '';
        type = asset?.type ?? 'STOCK';
        error = null;
        removedIds = [];
        nextTempId = -1;

        if (asset) {
            listings = asset.listings.map(l => ({
                tempId: l.id,
                ticker: l.ticker,
                exchange: l.exchange ?? '',
                currency: l.currency,
                mappings: l.priceMappings.map(m => ({ provider: m.provider, externalId: m.externalId })),
                originalMappings: l.priceMappings.map(m => ({ provider: m.provider, externalId: m.externalId })),
            }));
        } else {
            listings = [{ tempId: nextTempId--, ticker: '', exchange: '', currency: 'EUR', mappings: [], originalMappings: [] }];
        }
    });

    function addListing() {
        listings.push({ tempId: nextTempId--, ticker: '', exchange: '', currency: 'EUR', mappings: [], originalMappings: [] });
    }

    function removeListing(l: ListingData) {
        if (l.tempId > 0) removedIds.push(l.tempId);
        listings = listings.filter(x => x.tempId !== l.tempId);
    }

    function changesFor(l: ListingData) {
        const original = l.originalMappings;
        const current = l.mappings.filter(m => m.provider.trim() && m.externalId.trim());
        return {
            toDelete: original.filter(o => !current.some(c => c.provider.trim() === o.provider)).map(o => o.provider),
            toUpsert: current
                .filter(c => {
                    const orig = original.find(o => o.provider === c.provider.trim());
                    return !orig || orig.externalId !== c.externalId.trim();
                })
                .map(c => ({ provider: c.provider.trim(), externalId: c.externalId.trim() })),
        };
    }

    const canSubmit = $derived(
        name.trim() !== '' &&
        listings.length > 0 &&
        listings.every(l => l.ticker.trim() !== '' && l.currency.trim() !== '') &&
        !submitting
    );

    async function handleSubmit(e: Event) {
        e.preventDefault();
        if (listings.length === 0) { error = 'At least one listing is required.'; return; }
        submitting = true;
        error = null;

        if (asset) {
            const { error: err } = await updateAsset({
                path: { id: asset.id },
                body: { isin: isin.trim() || undefined, name: name.trim(), type },
            });
            if (err) { error = 'Failed to update asset.'; submitting = false; return; }

            for (const id of removedIds) {
                await deleteListing({ path: { id } });
            }
            for (const l of listings) {
                let realId: number;
                if (l.tempId < 0) {
                    const { data: created } = await createListing({
                        path: { id: asset.id },
                        body: { exchange: l.exchange || undefined, ticker: l.ticker, currency: l.currency },
                    });
                    realId = created!.id;
                } else {
                    await updateListing({
                        path: { id: l.tempId },
                        body: { exchange: l.exchange || undefined, ticker: l.ticker, currency: l.currency },
                    });
                    realId = l.tempId;
                }
                const { toDelete, toUpsert } = changesFor(l);
                for (const provider of toDelete) {
                    await deletePriceMapping({ path: { id: realId, provider } });
                }
                for (const m of toUpsert) {
                    await setPriceMapping({ path: { id: realId, provider: m.provider }, body: { externalId: m.externalId } });
                }
            }
            const { data } = await getAsset({ path: { id: asset.id } });
            submitting = false;
            onSuccess(data!);
        } else {
            const { data, error: err } = await createAsset({
                body: {
                    isin: isin.trim() || undefined,
                    name: name.trim(),
                    type,
                    listings: listings.map(l => ({
                        exchange: l.exchange || undefined,
                        ticker: l.ticker,
                        currency: l.currency,
                        priceMappings: changesFor(l).toUpsert,
                    })),
                },
            });
            if (err) { error = 'Failed to create asset.'; submitting = false; return; }
            const { data: full } = await getAsset({ path: { id: data!.id } });
            submitting = false;
            onSuccess(full!);
        }
    }
</script>

<h3 class="text-lg font-bold mb-6">{asset ? 'Edit asset' : 'Add asset'}</h3>

<form onsubmit={handleSubmit} class="space-y-4">
    {#if !asset}
        <div class="relative">
            <fieldset class="fieldset">
                <legend class="fieldset-legend">
                    Search Yahoo Finance
                    <span class="text-base-content/40 font-normal">(optional — or fill in the form below)</span>
                </legend>
                <input
                    class="input w-full"
                    type="text"
                    placeholder="e.g. VWCE or Vanguard..."
                    bind:value={searchQuery}
                    oninput={onSearchInput}
                    onblur={() => setTimeout(() => { searchOpen = false; }, 150)}
                    autocomplete="off"
                    autofocus
                />
            </fieldset>
            {#if searchOpen}
                <ul class="absolute z-50 w-full bg-base-100 border border-base-300 rounded-box shadow-lg mt-1 max-h-64 overflow-y-auto">
                    {#each searchResults as result}
                        <li>
                            <button
                                type="button"
                                class="w-full text-left px-4 py-2.5 hover:bg-base-200 flex items-center justify-between gap-2"
                                onmousedown={() => applySearchResult(result)}
                            >
                                <span>
                                    <span class="font-mono font-semibold text-sm">{result.symbol}</span>
                                    <span class="text-base-content/60 text-sm ml-2">{result.name}</span>
                                </span>
                                <span class="badge badge-ghost badge-sm shrink-0">{result.type}</span>
                            </button>
                        </li>
                    {/each}
                </ul>
            {/if}
        </div>
    {/if}

    {#if !asset}
        <div class="divider text-xs text-base-content/40">or fill in manually</div>
    {/if}

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Name</legend>
        <input class="input w-full" type="text" placeholder="e.g. Apple Inc." bind:value={name} disabled={submitting} required autofocus={!!asset} />
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Type</legend>
            <select class="select w-full" bind:value={type} disabled={submitting}>
                {#each ASSET_TYPES as t}<option value={t}>{t}</option>{/each}
            </select>
        </fieldset>
        <fieldset class="fieldset">
            <legend class="fieldset-legend">ISIN <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input w-full" type="text" placeholder="e.g. IE00B3RBWM25" bind:value={isin} disabled={submitting} />
        </fieldset>
    </div>

    <div class="space-y-2">
        <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Listings</span>
            <button type="button" class="btn btn-ghost btn-xs" onclick={addListing}>+ Add listing</button>
        </div>

        {#each listings as l}
            <ListingForm listing={l} onRemove={() => removeListing(l)} />
        {/each}

        {#if listings.length === 0}
            <p class="text-sm text-base-content/40 italic">No listings yet.</p>
        {/if}
    </div>

    {#if error}
        <div class="alert alert-error text-sm"><span>{error}</span></div>
    {/if}

    <div class="modal-action mt-6">
        <button type="button" class="btn btn-ghost" disabled={submitting} onclick={onCancel}>Cancel</button>
        <button type="submit" class="btn btn-primary" disabled={!canSubmit}>
            {#if submitting}
                <span class="loading loading-spinner loading-sm"></span>
            {:else}
                {asset ? 'Save' : 'Add asset'}
            {/if}
        </button>
    </div>
</form>
