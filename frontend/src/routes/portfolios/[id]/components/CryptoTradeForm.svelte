<script lang="ts">
    import { recordCryptoTrade, getPriceHistory, syncListingPriceHistory } from '$lib/api/sdk.gen';
    import type { Asset } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';

    interface Props {
        assets: Asset[];
        portfolioId: number;
        onSuccess: () => void;
        onCancel: () => void;
    }

    const { assets, portfolioId, onSuccess, onCancel }: Props = $props();

    const cryptoAssets = $derived(assets.filter(a => a.type === 'CRYPTO'));

    let sellListingId = $state(0);
    let sellQuantity = $state('');
    let sellPrice = $state('');
    let sellPriceFetching = $state(false);
    let sellPriceAutoDate = $state<string | null>(null);
    let sellPriceNoData = $state(false);

    let buyListingId = $state(0);
    let buyQuantity = $state('');
    let buyPrice = $state('');
    let buyPriceFetching = $state(false);
    let buyPriceAutoDate = $state<string | null>(null);
    let buyPriceNoData = $state(false);

    let formDate = $state(new Date().toISOString().slice(0, 10));
    let formFees = $state('');
    let formBroker = $state('');
    let formNotes = $state('');
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    const canSubmit = $derived(
        sellListingId > 0 && sellQuantity !== '' && sellPrice !== '' &&
        buyListingId > 0 && buyQuantity !== '' && buyPrice !== '' &&
        formDate !== '' && !formSubmitting
    );

    const sellValue = $derived(
        sellQuantity !== '' && sellPrice !== '' ? Number(sellQuantity) * Number(sellPrice) : null
    );
    const buyValue = $derived(
        buyQuantity !== '' && buyPrice !== '' ? Number(buyQuantity) * Number(buyPrice) : null
    );
    const feesValue = $derived(formFees !== '' ? Number(formFees) : 0);
    const totalSellCost = $derived(sellValue != null ? sellValue + feesValue : null);
    const deltaPercent = $derived(
        totalSellCost != null && buyValue != null && totalSellCost > 0
            ? ((buyValue - totalSellCost) / totalSellCost) * 100
            : null
    );
    const showSummary = $derived(sellValue != null && buyValue != null);

    function daysAgo(dateStr: string, n: number): string {
        const d = new Date(dateStr);
        d.setDate(d.getDate() - n);
        return d.toISOString().slice(0, 10);
    }

    $effect(() => {
        const listingId = sellListingId;
        const date = formDate;
        if (!listingId || !date) return;
        sellPriceFetching = true;
        sellPriceAutoDate = null;
        sellPriceNoData = false;
        getPriceHistory({ path: { id: listingId }, query: { from: daysAgo(date, 3), to: date } }).then(res => {
            const last = res.data?.at(-1);
            if (last) { sellPrice = String(last.price); sellPriceAutoDate = last.date; }
            else sellPriceNoData = true;
            sellPriceFetching = false;
        });
    });

    $effect(() => {
        const listingId = buyListingId;
        const date = formDate;
        if (!listingId || !date) return;
        buyPriceFetching = true;
        buyPriceAutoDate = null;
        buyPriceNoData = false;
        getPriceHistory({ path: { id: listingId }, query: { from: daysAgo(date, 3), to: date } }).then(res => {
            const last = res.data?.at(-1);
            if (last) { buyPrice = String(last.price); buyPriceAutoDate = last.date; }
            else buyPriceNoData = true;
            buyPriceFetching = false;
        });
    });

    async function syncPrice(side: 'sell' | 'buy') {
        const listingId = side === 'sell' ? sellListingId : buyListingId;
        if (!listingId || !formDate) return;
        if (side === 'sell') { sellPriceFetching = true; sellPriceNoData = false; }
        else                 { buyPriceFetching  = true; buyPriceNoData  = false; }
        const { data } = await syncListingPriceHistory({ path: { id: listingId }, query: { date: formDate } });
        if (data) {
            if (side === 'sell') { sellPrice = String(data.price); sellPriceAutoDate = data.date; }
            else                 { buyPrice  = String(data.price); buyPriceAutoDate  = data.date; }
        } else {
            if (side === 'sell') sellPriceNoData = true;
            else                 buyPriceNoData  = true;
        }
        if (side === 'sell') sellPriceFetching = false;
        else                 buyPriceFetching  = false;
    }

    async function submit(e: SubmitEvent) {
        e.preventDefault();
        formSubmitting = true;
        formError = null;
        const { error: err } = await recordCryptoTrade({
            path: { portfolioId },
            body: {
                sellListingId,
                sellQuantity: Number(sellQuantity),
                sellPrice: Number(sellPrice),
                buyListingId,
                buyQuantity: Number(buyQuantity),
                buyPrice: Number(buyPrice),
                date: formDate,
                fees: formFees !== '' ? Number(formFees) : undefined,
                broker: formBroker || undefined,
                notes: formNotes || undefined,
            },
        });
        formSubmitting = false;
        if (err) {
            const detail = (err as { detail?: string })?.detail;
            formError = detail ?? 'Failed to record trade.';
        } else {
            onSuccess();
        }
    }
</script>

<form class="space-y-4" onsubmit={submit}>
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Date</legend>
        <input
            class="input w-full"
            type="date"
            required
            value={formDate}
            onchange={(e) => { formDate = (e.currentTarget as HTMLInputElement).value; }}
        />
    </fieldset>

    <p class="text-sm font-semibold text-error">Sell</p>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete assets={cryptoAssets} bind:value={sellListingId} />
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Quantity</legend>
            <input class="input w-full" type="number" min="0" step="any" placeholder="0.00" bind:value={sellQuantity} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Price per unit</legend>
            <div class="relative">
                <input class="input w-full" type="number" min="0" step="any" placeholder="0.00"
                    bind:value={sellPrice} readonly={sellPriceFetching} required />
                {#if sellPriceFetching}
                    <span class="loading loading-spinner loading-xs absolute right-3 top-1/2 -translate-y-1/2 text-base-content/40"></span>
                {/if}
            </div>
            {#if sellPriceAutoDate}
                <p class="text-xs text-base-content/50 mt-1">Auto-filled · price from {sellPriceAutoDate}</p>
            {:else if sellPriceNoData}
                <p class="text-xs text-warning mt-1">
                    No price data —
                    <button type="button" class="link link-warning font-normal" onclick={() => syncPrice('sell')}>fetch from provider</button>
                    or enter manually
                </p>
            {/if}
        </fieldset>
    </div>

    <p class="text-sm font-semibold text-success">Buy</p>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete assets={cryptoAssets} bind:value={buyListingId} />
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Quantity</legend>
            <input class="input w-full" type="number" min="0" step="any" placeholder="0.00" bind:value={buyQuantity} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Price per unit</legend>
            <div class="relative">
                <input class="input w-full" type="number" min="0" step="any" placeholder="0.00"
                    bind:value={buyPrice} readonly={buyPriceFetching} required />
                {#if buyPriceFetching}
                    <span class="loading loading-spinner loading-xs absolute right-3 top-1/2 -translate-y-1/2 text-base-content/40"></span>
                {/if}
            </div>
            {#if buyPriceAutoDate}
                <p class="text-xs text-base-content/50 mt-1">Auto-filled · price from {buyPriceAutoDate}</p>
            {:else if buyPriceNoData}
                <p class="text-xs text-warning mt-1">
                    No price data —
                    <button type="button" class="link link-warning font-normal" onclick={() => syncPrice('buy')}>fetch from provider</button>
                    or enter manually
                </p>
            {/if}
        </fieldset>
    </div>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Fees <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <input class="input w-full" type="number" min="0" step="any" placeholder="0.00" bind:value={formFees} />
        <p class="text-xs text-base-content/50 mt-1">Applied to sell leg</p>
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Broker <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <input class="input w-full" type="text" placeholder="e.g. Coinbase" bind:value={formBroker} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Notes <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <textarea class="textarea w-full" rows="2" placeholder="Any context" bind:value={formNotes}></textarea>
    </fieldset>

    {#if showSummary}
        <div class="bg-base-200 rounded-box p-4 space-y-2 text-sm">
            <div class="flex justify-between">
                <span class="text-base-content/60">Sell value</span>
                <span class="font-mono">{sellValue!.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
            </div>
            {#if feesValue > 0}
                <div class="flex justify-between">
                    <span class="text-base-content/60">Fees</span>
                    <span class="font-mono text-base-content/60">−{feesValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
                </div>
                <div class="flex justify-between border-t border-base-300 pt-2">
                    <span class="text-base-content/60">Total cost</span>
                    <span class="font-mono">{totalSellCost!.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
                </div>
            {/if}
            <div class="flex justify-between">
                <span class="text-base-content/60">Buy value</span>
                <span class="font-mono">{buyValue!.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
            </div>
            {#if deltaPercent != null}
                <div class="flex justify-between border-t border-base-300 pt-2">
                    <span class="text-base-content/60">Value delta</span>
                    <span class="font-mono font-semibold {deltaPercent > 0 ? 'text-success' : deltaPercent < 0 ? 'text-error' : ''}">
                        {deltaPercent > 0 ? '+' : ''}{deltaPercent.toFixed(2)}%
                    </span>
                </div>
                {#if Math.abs(deltaPercent) > 10}
                    <div class="alert alert-warning text-xs py-2">
                        <span>Delta exceeds 10% — double-check your quantities and prices.</span>
                    </div>
                {/if}
            {/if}
        </div>
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
                Record trade
            {/if}
        </button>
    </div>
</form>
