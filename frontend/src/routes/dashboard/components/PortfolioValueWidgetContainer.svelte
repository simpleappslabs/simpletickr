<script lang="ts">
    import '$lib/client';
    import { getPortfolioValueHistory, updateDashboardWidget } from '$lib/api/sdk.gen';
    import type { DashboardWidget, PortfolioValuePoint } from '$lib/api/types.gen';
    import ValueHistoryChart from '$lib/portfolio/ValueHistoryChart.svelte';
    import DashboardWidgetCard from './DashboardWidgetCard.svelte';
    import { untrack } from 'svelte';

    let { widget, onremove }: {
        widget: DashboardWidget;
        onremove: () => void;
    } = $props();

    const RANGES = ['1M', '3M', '6M', '1Y'] as const;
    type Range = typeof RANGES[number];

    function isRange(r: string): r is Range {
        return (RANGES as readonly string[]).includes(r);
    }

    function toDateString(d: Date) { return d.toISOString().slice(0, 10); }

    function fromDate(range: Range): string {
        const d = new Date();
        if (range === '1M') d.setMonth(d.getMonth() - 1);
        else if (range === '3M') d.setMonth(d.getMonth() - 3);
        else if (range === '6M') d.setMonth(d.getMonth() - 6);
        else d.setFullYear(d.getFullYear() - 1);
        return toDateString(d);
    }

    let activeRange = $state<Range>(untrack(() => isRange(widget.config.range) ? widget.config.range : '1M'));
    let valuePoints = $state<PortfolioValuePoint[]>([]);
    let baseCurrency = $state('');
    let loading = $state(false);
    let error = $state<string | null>(null);

    const latestValue = $derived(valuePoints.findLast(p => p.value != null));
    const latestValueFormatted = $derived(
        latestValue?.value != null ? latestValue.value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : null
    );

    $effect(() => { load(activeRange); });

    async function load(range: Range) {
        loading = true;
        error = null;
        const { data, error: err } = await getPortfolioValueHistory({
            path: { id: widget.config.targetId },
            query: { from: fromDate(range), to: toDateString(new Date()) },
        });
        if (err || !data) error = 'Failed to load value history.';
        else { valuePoints = data.points; baseCurrency = data.baseCurrency; }
        loading = false;
    }

    async function changeRange(range: Range) {
        if (range === activeRange) return;
        await updateDashboardWidget({ path: { id: widget.id }, body: { config: { range } } });
        activeRange = range;
    }
</script>

{#snippet summary()}
    {#if latestValueFormatted}
        <div class="flex items-baseline gap-2 mb-2">
            <span class="text-sm font-semibold">{latestValueFormatted} {baseCurrency}</span>
            <span class="text-xs text-base-content/40">as of {latestValue?.date}</span>
        </div>
    {/if}
{/snippet}

{#snippet rangeSelector()}
    <div class="join mb-3">
        {#each RANGES as range}
            <button
                class="join-item btn btn-xs"
                class:btn-primary={activeRange === range}
                class:btn-ghost={activeRange !== range}
                onclick={() => changeRange(range)}
            >{range}</button>
        {/each}
    </div>
{/snippet}

{#snippet chart()}
    <ValueHistoryChart points={valuePoints} {baseCurrency} compact />
{/snippet}

<DashboardWidgetCard title={widget.label} {loading} {error} {onremove} {summary} {rangeSelector} {chart} />
