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

<div class="bg-base-200 rounded-box p-4 space-y-4">
    <div class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 items-center">
        <span class="text-sm text-base-content/50">Symbol</span>
        <input class="input input-sm w-full font-mono font-semibold" type="text" placeholder="VWCE" bind:value={listing.ticker} />

        <span class="text-sm text-base-content/50">Venue</span>
        <input class="input input-sm w-full" type="text" placeholder="e.g. XETRA" bind:value={listing.exchange} />

        <span class="text-sm text-base-content/50">Currency</span>
        <input class="input input-sm w-full" type="text" placeholder="EUR" bind:value={listing.currency} />
    </div>

    <PriceMappingForm mappings={listing.mappings} />

    <div class="flex justify-end border-t border-base-300/50 pt-3">
        <button type="button" class="btn btn-ghost btn-xs text-error" onclick={onRemove}>Delete listing</button>
    </div>
</div>
