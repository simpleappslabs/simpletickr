<script lang="ts">
    import type { Holding } from '$lib/api/types.gen';

    let { holdings, lastSyncAt = null }: { holdings: Holding[]; lastSyncAt?: Date | null } = $props();

    const ccy = $derived(holdings[0]?.baseCurrency ?? '');
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
    const totalGainPct = $derived(
        totalGain != null && totalCost > 0 ? (totalGain / totalCost) * 100 : null
    );

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function relativeTime(date: Date): string {
        const diffMin = Math.floor((Date.now() - date.getTime()) / 60000);
        if (diffMin < 1) return 'just now';
        if (diffMin < 60) return `${diffMin}m ago`;
        const diffHr = Math.floor(diffMin / 60);
        if (diffHr < 24) return `${diffHr}h ago`;
        return `${Math.floor(diffHr / 24)}d ago`;
    }
</script>

<div class="stats stats-vertical sm:stats-horizontal bg-base-200 w-full">
    <div class="stat">
        <div class="stat-title">Total cost</div>
        <div class="stat-value text-xl">{fmt(totalCost)} {ccy}</div>
    </div>
    <div class="stat">
        <div class="stat-title">Market value</div>
        <div class="stat-value text-xl">
            {totalMarketValue == null ? '—' : `${fmt(totalMarketValue)} ${ccy}`}
        </div>
    </div>
    <div class="stat">
        <div class="stat-title">Unrealized gain</div>
        <div class="stat-value text-xl {totalGain == null ? '' : totalGain >= 0 ? 'text-success' : 'text-error'}">
            {totalGain == null ? '—' : `${totalGain >= 0 ? '+' : ''}${fmt(totalGain)} ${ccy}`}
            {#if totalGainPct != null}
                <span class="stat-desc" class:text-success={totalGain != null && totalGain >= 0} class:text-error={totalGain != null && totalGain < 0}>
                    ({totalGainPct >= 0 ? '+' : ''}{fmt(totalGainPct)}%)
                </span>
            {/if}
        </div>
    </div>
    <div class="stat">
        <div class="stat-title">Positions</div>
        <div class="stat-value text-xl">{holdings.length}</div>
        {#if lastSyncAt}
            <div class="stat-desc">Synced {relativeTime(lastSyncAt)}</div>
        {/if}
    </div>
</div>
