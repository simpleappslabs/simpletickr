<script lang="ts">
    import type { Holding } from '$lib/api/types.gen';

    let { holdings }: { holdings: Holding[] } = $props();

    const totalCost = $derived(holdings.reduce((sum, h) => sum + (h.totalCostBase ?? 0), 0));
    const totalMarketValue = $derived(
        holdings.length > 0 && holdings.every((h) => h.marketValueBase != null)
            ? holdings.reduce((sum, h) => sum + (h.marketValueBase ?? 0), 0)
            : null
    );
    const totalGain = $derived(
        holdings.length > 0 && holdings.every((h) => h.unrealizedPnlBase != null)
            ? holdings.reduce((sum, h) => sum + (h.unrealizedPnlBase ?? 0), 0)
            : null
    );

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
</script>

<div class="stats stats-vertical sm:stats-horizontal bg-base-200 w-full">
    <div class="stat">
        <div class="stat-title">Total cost</div>
        <div class="stat-value text-xl">{fmt(totalCost)} {holdings[0]?.baseCurrency ?? ''}</div>
    </div>
    <div class="stat">
        <div class="stat-title">Market value</div>
        <div class="stat-value text-xl">
            {totalMarketValue == null ? '—' : `${fmt(totalMarketValue)} ${holdings[0]?.baseCurrency ?? ''}`}
        </div>
    </div>
    <div class="stat">
        <div class="stat-title">Unrealized gain</div>
        <div class="stat-value text-xl {totalGain == null ? '' : totalGain >= 0 ? 'text-success' : 'text-error'}">
            {totalGain == null ? '—' : `${totalGain >= 0 ? '+' : ''}${fmt(totalGain)} ${holdings[0]?.baseCurrency ?? ''}`}
        </div>
    </div>
    <div class="stat">
        <div class="stat-title">Positions</div>
        <div class="stat-value text-xl">{holdings.length}</div>
    </div>
</div>
