<script lang="ts">
    import { recordTransaction, amendTransaction, lookupFxRate, getSettings } from '$lib/api/sdk.gen';
    import type { Asset, Transaction, TransactionType } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';

    interface Props {
        assets: Asset[];
        portfolioId: number;
        transaction?: Transaction | null;
        onSuccess: () => void;
        onCancel: () => void;
    }

    const { assets, portfolioId, transaction = null, onSuccess, onCancel }: Props = $props();

    let formListingId = $state(0);
    let formType = $state<TransactionType>('BUY');
    let formQuantity = $state('');
    let formPrice = $state('');
    let formDate = $state('');
    let formFees = $state('');
    let formFxRate = $state('');
    let formFxRateAutoDate = $state<string | null>(null);
    let formFxRateUserEdited = $state(false);
    let formFxRateFetching = $state(false);
    let fxFetchVersion = $state(0);
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    let baseCurrency = $state<string | null>(null);

    $effect(() => {
        getSettings().then(res => { baseCurrency = res.data?.baseCurrency ?? null; });
    });

    const selectedListing = $derived(
        formListingId > 0
            ? assets.flatMap(a => a.listings).find(l => l.id === formListingId) ?? null
            : null
    );

    const needsFx = $derived(
        selectedListing != null && baseCurrency != null && selectedListing.currency !== baseCurrency
    );

    // Re-fetch FX rate whenever listing, date, or fetch trigger changes (and user hasn't manually edited it)
    $effect(() => {
        fxFetchVersion; // tracked so the refresh button can force a re-run
        const listing = selectedListing;
        const date = formDate;
        if (!needsFx || !date || formFxRateUserEdited) return;

        formFxRateAutoDate = null;
        formFxRateFetching = true;
        lookupFxRate({ query: { base: baseCurrency!, quote: listing!.currency, date } }).then(res => {
            if (res.data && !formFxRateUserEdited) {
                formFxRate = String(res.data.rate);
                formFxRateAutoDate = res.data.date;
            }
            formFxRateFetching = false;
        });
    });

    $effect(() => {
        if (transaction) {
            formListingId = transaction.listingId;
            formType = transaction.type;
            formQuantity = String(transaction.quantity);
            formPrice = String(transaction.price);
            formDate = transaction.date;
            formFees = transaction.fees != null ? String(transaction.fees) : '';
            formFxRate = transaction.fxRate != null ? String(transaction.fxRate) : '';
            formFxRateAutoDate = null;
            formFxRateUserEdited = transaction.fxRate != null;
        } else {
            formListingId = 0;
            formType = 'BUY';
            formQuantity = '';
            formPrice = '';
            formDate = new Date().toISOString().slice(0, 10);
            formFees = '';
            formFxRate = '';
            formFxRateAutoDate = null;
            formFxRateUserEdited = false;
        }
        formError = null;
    });

    function onFxRateInput() {
        formFxRateAutoDate = null;
        formFxRateUserEdited = true;
    }

    const canSubmit = $derived(
        formListingId > 0 &&
        formQuantity !== '' &&
        formPrice !== '' &&
        formDate !== '' &&
        (!needsFx || formFxRate !== '') &&
        !formFxRateFetching &&
        !formSubmitting
    );

    async function submit(e: SubmitEvent) {
        e.preventDefault();
        formSubmitting = true;
        formError = null;

        const body = {
            listingId: formListingId,
            type: formType,
            quantity: Number(formQuantity),
            price: Number(formPrice),
            date: formDate,
            fees: formFees ? Number(formFees) : undefined,
            fxRate: needsFx && formFxRate ? Number(formFxRate) : undefined,
        };

        const res = transaction
            ? await amendTransaction({ path: { portfolioId, id: transaction.id }, body })
            : await recordTransaction({ path: { portfolioId }, body });

        if (res.error) {
            formError = transaction ? 'Failed to update transaction.' : 'Failed to record transaction.';
            formSubmitting = false;
            return;
        }

        formSubmitting = false;
        onSuccess();
    }
</script>

<form class="space-y-4" onsubmit={submit}>
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete {assets} bind:value={formListingId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Type</legend>
        <select class="select w-full" bind:value={formType}>
            <option value="BUY">Buy</option>
            <option value="SELL">Sell</option>
        </select>
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Quantity</legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formQuantity} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Price per unit</legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formPrice} required />
        </fieldset>
    </div>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Date</legend>
            <input class="input w-full" type="date" bind:value={formDate} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Fees <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formFees} />
        </fieldset>
    </div>

    {#if needsFx}
        <fieldset class="fieldset">
            <legend class="fieldset-legend">
                FX rate
                <span class="text-base-content/40 font-normal text-xs ml-1">
                    1 {baseCurrency} = ? {selectedListing?.currency}
                </span>
            </legend>
            <div class="flex gap-2">
                <div class="relative flex-1">
                    <input
                        class="input w-full"
                        type="number" min="0" step="any"
                        placeholder="0.00000"
                        bind:value={formFxRate}
                        oninput={onFxRateInput}
                        readonly={formFxRateFetching}
                        required
                    />
                    {#if formFxRateFetching}
                        <span class="loading loading-spinner loading-xs absolute right-3 top-1/2 -translate-y-1/2 text-base-content/40"></span>
                    {/if}
                </div>
                <button
                    type="button"
                    class="btn btn-ghost btn-square"
                    title="Re-fetch rate"
                    disabled={formFxRateFetching}
                    onclick={() => { formFxRateUserEdited = false; fxFetchVersion++; }}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
                        <path d="M3 22v-6h6"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/>
                    </svg>
                </button>
            </div>
            {#if formFxRateAutoDate && !formFxRateUserEdited}
                <p class="text-xs text-base-content/50 mt-1">Auto-filled · rate from {formFxRateAutoDate}</p>
            {/if}
        </fieldset>
    {/if}

    {#if formError}
        <div class="alert alert-error text-sm"><span>{formError}</span></div>
    {/if}

    <div class="modal-action mt-6">
        <button type="button" class="btn btn-ghost" onclick={onCancel}>Cancel</button>
        <button type="submit" class="btn btn-primary" disabled={!canSubmit}>
            {#if formSubmitting}
                <span class="loading loading-spinner loading-sm"></span>
            {:else}
                {transaction ? 'Update' : 'Record'}
            {/if}
        </button>
    </div>
</form>
