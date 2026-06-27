<script lang="ts">
    import { getPriceHistory } from '$lib/api/sdk.gen';
    import type { PricePoint } from '$lib/api/types.gen';
    import PriceHistoryChart from './PriceHistoryChart.svelte';

    let { open, listing, onclose }: {
        open: boolean;
        listing: { id: number; ticker: string; currency: string } | null;
        onclose: () => void;
    } = $props();

    type Range = '1M' | '3M' | '6M' | '1Y' | 'All';
    const RANGES: Range[] = ['1M', '3M', '6M', '1Y', 'All'];

    let activeRange = $state<Range>('1Y');
    let points = $state<PricePoint[]>([]);
    let loading = $state(false);
    let error = $state<string | null>(null);

    function toDateString(d: Date): string {
        return d.toISOString().slice(0, 10);
    }

    function fromDate(range: Range): string | undefined {
        if (range === 'All') return undefined;
        const d = new Date();
        if (range === '1M') d.setMonth(d.getMonth() - 1);
        else if (range === '3M') d.setMonth(d.getMonth() - 3);
        else if (range === '6M') d.setMonth(d.getMonth() - 6);
        else if (range === '1Y') d.setFullYear(d.getFullYear() - 1);
        return toDateString(d);
    }

    $effect(() => {
        if (!listing || !open) return;
        const id = listing.id;
        const range = activeRange;

        loading = true;
        error = null;
        points = [];

        getPriceHistory({
            path: { id },
            query: { from: fromDate(range) ?? '', to: toDateString(new Date()) },
        }).then(({ data, error: err }) => {
            if (err || !data) {
                error = 'Failed to load price history.';
            } else {
                points = data;
            }
            loading = false;
        });
    });
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box w-full max-w-2xl">
        <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold">
                Price history · <span class="font-mono">{listing?.ticker}</span>
            </h3>
            <div class="join">
                {#each RANGES as range}
                    <button
                        class="join-item btn btn-xs {activeRange === range ? 'btn-primary' : 'btn-ghost'}"
                        onclick={() => { activeRange = range; }}
                    >{range}</button>
                {/each}
            </div>
        </div>

        {#if loading}
            <div class="flex justify-center py-12">
                <span class="loading loading-spinner loading-sm"></span>
            </div>
        {:else if error}
            <div class="alert alert-error py-2"><span>{error}</span></div>
        {:else}
            <PriceHistoryChart {points} currency={listing?.currency ?? ''} />
        {/if}

        <div class="modal-action mt-4">
            <button class="btn btn-ghost" onclick={onclose}>Close</button>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onclose}>close</button>
    </form>
</dialog>
