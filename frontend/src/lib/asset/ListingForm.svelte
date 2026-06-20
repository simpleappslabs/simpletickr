<script lang="ts">
    import PriceMappingForm, { type Mapping } from '$lib/asset/PriceMappingForm.svelte';

    export interface ListingData {
        tempId: number;
        ticker: string;
        exchange: string;
        currency: string;
        mappings: Mapping[];
        originalMappings: Mapping[];
    }

    interface Props {
        listing: ListingData;
        onRemove: () => void;
    }

    const { listing, onRemove }: Props = $props();
</script>

<div class="bg-base-200 rounded-box p-4 space-y-3">
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Ticker</legend>
        <input class="input input-sm w-full font-mono font-semibold" type="text" placeholder="VWCE" bind:value={listing.ticker} />
    </fieldset>

    <div class="grid grid-cols-2 gap-3">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Exchange <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input input-sm w-full" type="text" placeholder="e.g. XETRA" bind:value={listing.exchange} />
        </fieldset>
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Currency</legend>
            <input class="input input-sm w-full" type="text" placeholder="EUR" bind:value={listing.currency} />
        </fieldset>
    </div>

    <PriceMappingForm mappings={listing.mappings} />

    <div class="flex justify-end border-t border-base-300/50 pt-3">
        <button type="button" class="btn btn-ghost btn-xs text-error" onclick={onRemove}>Delete listing</button>
    </div>
</div>
