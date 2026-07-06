<script lang="ts">
    import { getPortfolioValueHistory } from '$lib/api/sdk.gen';
    import type { PortfolioValuePoint } from '$lib/api/types.gen';
    import ValueHistoryChart from '$lib/portfolio/ValueHistoryChart.svelte';

    let { portfolioId, refreshKey = 0 }: { portfolioId: number; refreshKey?: number } = $props();

    type Range = '1M' | '3M' | '6M' | '1Y' | 'All';
    const RANGES: Range[] = ['1M', '3M', '6M', '1Y', 'All'];

    let activeRange = $state<Range>('6M');
    let points = $state<PortfolioValuePoint[]>([]);
    let baseCurrency = $state('');
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

    async function load(range: Range) {
        loading = true;
        error = null;
        const { data, error: err } = await getPortfolioValueHistory({
            path: { id: portfolioId },
            query: { from: fromDate(range), to: toDateString(new Date()) },
        });
        if (err || !data) {
            error = 'Failed to load value history.';
        } else {
            points = data.points;
            baseCurrency = data.baseCurrency;
        }
        loading = false;
    }

    $effect(() => {
        refreshKey; // re-run when parent signals a refresh
        load(activeRange);
    });
</script>

<div class="space-y-3">
    <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">Portfolio value</h2>
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
        <div class="flex justify-center py-8">
            <span class="loading loading-spinner loading-sm"></span>
        </div>
    {:else if error}
        <div class="alert alert-error py-2"><span>{error}</span></div>
    {:else}
        <ValueHistoryChart {points} {baseCurrency} />
    {/if}
</div>
