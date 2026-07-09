<script lang="ts">
    import { recordTransaction, amendTransaction, lookupFxRate, getSettings, getPriceHistory, syncListingPriceHistory } from '$lib/api/sdk.gen';
    import type { Account, Asset, Holding, Transaction, TransactionType } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';
    import AccountAutocomplete from '$lib/AccountAutocomplete.svelte';

    interface Props {
        assets: Asset[];
        accounts: Account[];
        holdings: Holding[];
        portfolioId: number;
        transaction?: Transaction | null;
        onSuccess: () => void;
        onCancel: () => void;
    }

    const { assets, accounts, holdings, portfolioId, transaction = null, onSuccess, onCancel }: Props = $props();

    let dateInputEl: HTMLInputElement | undefined = $state();

    $effect(() => {
        dateInputEl?.focus();
    });

    let formListingId = $state(0);
    let formType = $state<TransactionType>('BUY');
    let formQuantity = $state('');
    let formPrice = $state('');
    let formPriceAutoDate = $state<string | null>(null);
    let formPriceUserEdited = $state(false);
    let formPriceFetching = $state(false);
    let formPriceNoData = $state(false);
    let formDate = $state('');
    let formFees = $state('');
    let formFxRate = $state('');
    let formFxRateAutoDate = $state<string | null>(null);
    let formFxRateUserEdited = $state(false);
    let formFxRateFetching = $state(false);
    let fxFetchVersion = $state(0);
    let formAccountId = $state(0);
    let formNotes = $state('');
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    const isSplit = $derived(formType === 'SPLIT');

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

    const currentListingQuantity = $derived(
        formListingId > 0
            ? holdings.flatMap(h => h.listings).find(l => l.listingId === formListingId)?.quantity ?? 0
            : 0
    );

    const oversellWarning = $derived(
        formType === 'SELL' &&
        formQuantity !== '' &&
        Number(formQuantity) > currentListingQuantity
    );

    function daysAgo(dateStr: string, n: number): string {
        const d = new Date(dateStr);
        d.setDate(d.getDate() - n);
        return d.toISOString().slice(0, 10);
    }

    // Re-fetch price whenever listing or date changes (and user hasn't manually edited it)
    $effect(() => {
        const listingId = formListingId;
        const date = formDate;
        if (isSplit || !listingId || !date || formPriceUserEdited) return;

        formPriceAutoDate = null;
        formPriceFetching = true;
        formPriceNoData = false;
        getPriceHistory({ path: { id: listingId }, query: { from: daysAgo(date, 3), to: date } }).then(res => {
            const last = res.data?.at(-1);
            if (last) { formPrice = String(last.price); formPriceAutoDate = last.date; }
            else formPriceNoData = true;
            formPriceFetching = false;
        });
    });

    async function syncPrice() {
        if (!formListingId || !formDate) return;
        formPriceFetching = true;
        formPriceNoData = false;
        const { data } = await syncListingPriceHistory({ path: { id: formListingId }, query: { date: formDate } });
        if (data) {
            formPrice = String(data.price);
            formPriceAutoDate = data.date;
            formPriceUserEdited = false;
        } else {
            formPriceNoData = true;
        }
        formPriceFetching = false;
    }

    function onPriceInput() {
        formPriceAutoDate = null;
        formPriceUserEdited = true;
    }

    // Re-fetch FX rate whenever listing, date, or fetch trigger changes (and user hasn't manually edited it)
    $effect(() => {
        fxFetchVersion; // tracked so the refresh button can force a re-run
        const listing = selectedListing;
        const date = formDate;
        if (isSplit || !needsFx || !date || formFxRateUserEdited) return;

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
            formPriceAutoDate = null;
            formPriceUserEdited = true;
            formPriceNoData = false;
            formDate = transaction.date;
            formFees = transaction.fees != null ? String(transaction.fees) : '';
            formFxRate = transaction.fxRate != null ? String(transaction.fxRate) : '';
            formFxRateAutoDate = null;
            formFxRateUserEdited = transaction.fxRate != null;
            formAccountId = transaction.accountId;
            formNotes = transaction.notes ?? '';
        } else {
            formListingId = 0;
            formType = 'BUY';
            formQuantity = '';
            formPrice = '';
            formPriceAutoDate = null;
            formPriceUserEdited = false;
            formPriceNoData = false;
            formDate = new Date().toISOString().slice(0, 10);
            formFees = '';
            formFxRate = '';
            formFxRateAutoDate = null;
            formFxRateUserEdited = false;
            formAccountId = 0;
            formNotes = '';
        }
        formError = null;
    });

    function onFxRateInput() {
        formFxRateAutoDate = null;
        formFxRateUserEdited = true;
    }

    const canSubmit = $derived(
        formListingId > 0 &&
        formAccountId > 0 &&
        formQuantity !== '' &&
        (isSplit || formPrice !== '') &&
        formDate !== '' &&
        (isSplit || !needsFx || formFxRate !== '') &&
        !formFxRateFetching &&
        !formPriceFetching &&
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
            price: isSplit ? 0 : Number(formPrice),
            date: formDate,
            fees: !isSplit && formFees ? Number(formFees) : undefined,
            fxRate: !isSplit && needsFx && formFxRate ? Number(formFxRate) : undefined,
            accountId: formAccountId,
            notes: formNotes || undefined,
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
        <legend class="fieldset-legend">Date</legend>
        <input class="input w-full" type="date" bind:value={formDate} bind:this={dateInputEl} required />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete {assets} bind:value={formListingId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Type</legend>
        <select class="select w-full" bind:value={formType}>
            <option value="BUY">Buy</option>
            <option value="SELL">Sell</option>
            <option value="SPLIT">Split</option>
        </select>
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">{isSplit ? 'Ratio (new / old shares)' : 'Quantity'}</legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder={isSplit ? '2' : '0.00'} bind:value={formQuantity} required />
        </fieldset>

        {#if !isSplit}
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Price per unit</legend>
            <div class="relative">
                <input class="input w-full" type="number" min="0" step="any" placeholder="0.00"
                       bind:value={formPrice} oninput={onPriceInput} readonly={formPriceFetching} required />
                {#if formPriceFetching}
                    <span class="loading loading-spinner loading-xs absolute right-3 top-1/2 -translate-y-1/2 text-base-content/40"></span>
                {/if}
            </div>
            {#if formPriceAutoDate}
                <p class="text-xs text-base-content/50 mt-1">Auto-filled · price from {formPriceAutoDate}</p>
            {:else if formPriceNoData}
                <p class="text-xs text-warning mt-1">
                    No price data —
                    <button type="button" class="link link-warning font-normal" onclick={syncPrice}>fetch from provider</button>
                    or enter manually
                </p>
            {/if}
        </fieldset>
        {/if}
    </div>

    {#if oversellWarning}
        <div class="alert alert-warning text-sm">
            <span>Quantity exceeds your current holding of {currentListingQuantity}. The transaction will still be recorded.</span>
        </div>
    {/if}

    {#if !isSplit}
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Fees <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <input class="input w-full" type="number" min="0" step="any"
               placeholder="0.00" bind:value={formFees} />
    </fieldset>
    {/if}

    {#if !isSplit && needsFx}
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

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Account</legend>
        <AccountAutocomplete {accounts} bind:value={formAccountId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Notes <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <textarea class="textarea w-full" rows="2" placeholder="Any context" bind:value={formNotes}></textarea>
    </fieldset>

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
