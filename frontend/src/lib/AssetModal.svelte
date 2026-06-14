<script lang="ts">
    import { createAsset, updateAsset, getAsset, createListing, updateListing, deleteListing } from '$lib/api/sdk.gen';
    import type { Asset, AssetType, Listing } from '$lib/api/types.gen';

    const ASSET_TYPES: AssetType[] = ['STOCK', 'ETF', 'CRYPTO', 'OTHER'];

    interface LocalListing {
        tempId: number;          // negative for unsaved; positive = real listing id
        exchange: string;
        ticker: string;
        currency: string;
    }

    interface Props {
        open: boolean;
        asset?: Asset | null;
        onSuccess: (asset: Asset) => void;
        onCancel: () => void;
    }

    const { open, asset = null, onSuccess, onCancel }: Props = $props();

    let isin = $state('');
    let name = $state('');
    let type = $state<AssetType>('STOCK');
    let submitting = $state(false);
    let error = $state<string | null>(null);

    // Listings — local state for both create and edit mode
    let listings = $state<LocalListing[]>([]);
    let editingTempId = $state<number | null>(null);
    let editExchange = $state('');
    let editTicker = $state('');
    let editCurrency = $state('');
    let listingError = $state<string | null>(null);
    let nextTempId = $state(-1);

    $effect(() => {
        if (open) {
            isin = asset?.isin ?? '';
            name = asset?.name ?? '';
            type = asset?.type ?? 'STOCK';
            error = null;
            listingError = null;
            editingTempId = null;

            if (asset) {
                listings = asset.listings.map(l => ({
                    tempId: l.id,
                    exchange: l.exchange ?? '',
                    ticker: l.ticker,
                    currency: l.currency,
                }));
            } else {
                listings = [];
                // Open the add-listing form immediately in create mode
                editingTempId = nextTempId;
                editExchange = '';
                editTicker = '';
                editCurrency = 'USD';
            }
        }
    });

    function startEdit(l: LocalListing) {
        editingTempId = l.tempId;
        editExchange = l.exchange;
        editTicker = l.ticker;
        editCurrency = l.currency;
        listingError = null;
    }

    function startAdd() {
        editingTempId = nextTempId;
        nextTempId -= 1;
        editExchange = '';
        editTicker = '';
        editCurrency = 'USD';
        listingError = null;
    }

    function cancelListingEdit() {
        // If it was a new (unsaved) listing, discard it
        if (editingTempId !== null && editingTempId < 0) {
            listings = listings.filter(l => l.tempId !== editingTempId);
        }
        editingTempId = null;
        listingError = null;
    }

    async function saveListingEdit() {
        if (!editTicker.trim() || !editCurrency.trim()) {
            listingError = 'Ticker and currency are required.';
            return;
        }
        const payload = {
            exchange: editExchange.trim() || undefined,
            ticker: editTicker.trim().toUpperCase(),
            currency: editCurrency.trim().toUpperCase(),
        };

        if (asset && editingTempId !== null && editingTempId > 0) {
            // Edit mode — update via API immediately
            const { error: err } = await updateListing({ path: { id: editingTempId }, body: payload });
            if (err) { listingError = 'Failed to update listing.'; return; }
            listings = listings.map(l =>
                l.tempId === editingTempId
                    ? { ...l, exchange: payload.exchange ?? '', ticker: payload.ticker, currency: payload.currency }
                    : l
            );
        } else if (asset && editingTempId !== null && editingTempId < 0) {
            // Edit mode — add new listing via API immediately
            const { data, error: err } = await createListing({ path: { id: asset.id }, body: payload });
            if (err) { listingError = 'Failed to add listing.'; return; }
            listings = [...listings.filter(l => l.tempId !== editingTempId), {
                tempId: data!.id,
                exchange: data!.exchange ?? '',
                ticker: data!.ticker,
                currency: data!.currency,
            }];
        } else {
            // Create mode — update local state only
            if (editingTempId !== null && listings.some(l => l.tempId === editingTempId)) {
                listings = listings.map(l =>
                    l.tempId === editingTempId
                        ? { ...l, exchange: payload.exchange ?? '', ticker: payload.ticker, currency: payload.currency }
                        : l
                );
            } else if (editingTempId !== null) {
                listings = [...listings, { tempId: editingTempId, exchange: payload.exchange ?? '', ticker: payload.ticker, currency: payload.currency }];
            }
        }
        editingTempId = null;
        listingError = null;
    }

    async function removeListing(l: LocalListing) {
        if (asset && l.tempId > 0) {
            const { error: err } = await deleteListing({ path: { id: l.tempId } });
            if (err) { listingError = 'Failed to delete listing.'; return; }
        }
        listings = listings.filter(x => x.tempId !== l.tempId);
    }

    const canSubmit = $derived(
        name.trim() !== '' &&
        (listings.length > 0 || (editingTempId !== null && editTicker.trim() !== '')) &&
        !submitting
    );

    async function handleSubmit(e: Event) {
        e.preventDefault();
        // Auto-save the pending listing form if still open (create mode only)
        if (!asset && editingTempId !== null && editTicker.trim() !== '') {
            await saveListingEdit();
            if (listingError) return;
        }
        if (listings.length === 0) {
            error = 'At least one listing is required.';
            return;
        }
        submitting = true;
        error = null;

        if (asset) {
            // Edit mode: only update asset-level fields (listings managed inline)
            const { error: err } = await updateAsset({
                path: { id: asset.id },
                body: { isin: isin.trim() || undefined, name: name.trim(), type },
            });
            if (err) { error = 'Failed to update asset.'; submitting = false; return; }
            const { data } = await getAsset({ path: { id: asset.id } });
            submitting = false;
            onSuccess(data!);
        } else {
            // Create mode: create asset with first listing, then add remaining
            const [first, ...rest] = listings;
            if (!first) { error = 'At least one listing is required.'; submitting = false; return; }
            const { data, error: err } = await createAsset({
                body: {
                    isin: isin.trim() || undefined,
                    name: name.trim(),
                    type,
                    listing: { exchange: first.exchange || undefined, ticker: first.ticker, currency: first.currency },
                },
            });
            if (err) { error = 'Failed to create asset.'; submitting = false; return; }
            for (const l of rest) {
                await createListing({ path: { id: data!.id }, body: { exchange: l.exchange || undefined, ticker: l.ticker, currency: l.currency } });
            }
            const { data: full } = await getAsset({ path: { id: data!.id } });
            submitting = false;
            onSuccess(full!);
        }
    }
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box max-w-lg">
        <h3 class="text-lg font-bold mb-6">{asset ? 'Edit asset' : 'Add asset'}</h3>
        <form onsubmit={handleSubmit} class="space-y-4">

            <!-- Asset fields -->
            <fieldset class="fieldset">
                <legend class="fieldset-legend">Name</legend>
                <input class="input w-full" type="text" placeholder="e.g. Apple Inc." bind:value={name} disabled={submitting} required />
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

            <!-- Listings section -->
            <div class="space-y-2">
                <div class="flex items-center justify-between">
                    <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Listings</span>
                    {#if editingTempId === null}
                        <button type="button" class="btn btn-ghost btn-xs" onclick={startAdd}>+ Add listing</button>
                    {/if}
                </div>

                {#if listings.length === 0 && editingTempId === null}
                    <p class="text-sm text-base-content/40 italic">No listings yet.</p>
                {/if}

                {#each listings as l}
                    {#if editingTempId === l.tempId}
                        <!-- Inline edit form -->
                        <div class="bg-base-200 rounded-box p-3 space-y-2">
                            <div class="grid grid-cols-3 gap-2">
                                <fieldset class="fieldset col-span-3 sm:col-span-1">
                                    <legend class="fieldset-legend">Ticker</legend>
                                    <input class="input input-sm w-full" type="text" placeholder="VWCE" bind:value={editTicker} />
                                </fieldset>
                                <fieldset class="fieldset col-span-3 sm:col-span-1">
                                    <legend class="fieldset-legend">Currency</legend>
                                    <input class="input input-sm w-full" type="text" placeholder="EUR" bind:value={editCurrency} />
                                </fieldset>
                                <fieldset class="fieldset col-span-3">
                                    <legend class="fieldset-legend">Exchange <span class="text-base-content/40 font-normal">(optional)</span></legend>
                                    <input class="input input-sm w-full" type="text" placeholder="e.g. Euronext Amsterdam" bind:value={editExchange} />
                                </fieldset>
                            </div>
                            {#if listingError}
                                <p class="text-error text-xs">{listingError}</p>
                            {/if}
                            <div class="flex gap-2 justify-end">
                                <button type="button" class="btn btn-ghost btn-xs" onclick={cancelListingEdit}>Cancel</button>
                                <button type="button" class="btn btn-primary btn-xs" onclick={saveListingEdit}>Save</button>
                            </div>
                        </div>
                    {:else}
                        <div class="flex items-center gap-2 px-3 py-2 bg-base-200 rounded-box">
                            <span class="font-mono font-semibold text-sm">{l.ticker}</span>
                            {#if l.exchange}
                                <span class="text-base-content/60 text-sm">{l.exchange}</span>
                            {/if}
                            <span class="text-xs text-base-content/40">{l.currency}</span>
                            <div class="ml-auto flex gap-1">
                                <button type="button" class="btn btn-ghost btn-xs" onclick={() => startEdit(l)}>Edit</button>
                                <button type="button" class="btn btn-ghost btn-xs text-error" onclick={() => removeListing(l)}>Delete</button>
                            </div>
                        </div>
                    {/if}
                {/each}

                <!-- New listing inline form (when editingTempId is new, not in listings yet) -->
                {#if editingTempId !== null && !listings.some(l => l.tempId === editingTempId)}
                    <div class="bg-base-200 rounded-box p-3 space-y-2">
                        <div class="grid grid-cols-3 gap-2">
                            <fieldset class="fieldset col-span-3 sm:col-span-1">
                                <legend class="fieldset-legend">Ticker</legend>
                                <input class="input input-sm w-full" type="text" placeholder="VWCE" bind:value={editTicker} />
                            </fieldset>
                            <fieldset class="fieldset col-span-3 sm:col-span-1">
                                <legend class="fieldset-legend">Currency</legend>
                                <input class="input input-sm w-full" type="text" placeholder="EUR" bind:value={editCurrency} />
                            </fieldset>
                            <fieldset class="fieldset col-span-3">
                                <legend class="fieldset-legend">Exchange <span class="text-base-content/40 font-normal">(optional)</span></legend>
                                <input class="input input-sm w-full" type="text" placeholder="e.g. Euronext Amsterdam" bind:value={editExchange} />
                            </fieldset>
                        </div>
                        {#if listingError}
                            <p class="text-error text-xs">{listingError}</p>
                        {/if}
                        <div class="flex gap-2 justify-end">
                            {#if asset || listings.length > 0}
                                <button type="button" class="btn btn-ghost btn-xs" onclick={cancelListingEdit}>Cancel</button>
                            {/if}
                            <button type="button" class="btn btn-primary btn-xs" onclick={saveListingEdit}>Save</button>
                        </div>
                    </div>
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
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onCancel}>close</button>
    </form>
</dialog>
