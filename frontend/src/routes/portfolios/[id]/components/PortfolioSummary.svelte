<script lang="ts">
    import type { Holding, PortfolioValuationSummary } from '$lib/api/types.gen';

    let { holdings, summary, lastSyncAt = null }: {
        holdings: Holding[];
        summary: PortfolioValuationSummary | null;
        lastSyncAt?: Date | null;
    } = $props();

    const ccy = $derived(holdings[0]?.baseCurrency ?? '');

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
        <div class="stat-value text-xl">{summary ? `${fmt(summary.totalCostBase)} ${ccy}` : '—'}</div>
    </div>
    <div class="stat">
        <div class="stat-title">Market value</div>
        <div class="stat-value text-xl">
            {summary?.totalMarketValueBase == null ? '—' : `${fmt(summary.totalMarketValueBase)} ${ccy}`}
        </div>
    </div>
    <div class="stat">
        <div class="stat-title">Unrealized gain</div>
        <div class="stat-value text-xl {summary?.totalUnrealizedPnlBase == null ? '' : summary.totalUnrealizedPnlBase >= 0 ? 'text-success' : 'text-error'}">
            {summary?.totalUnrealizedPnlBase == null ? '—' : `${summary.totalUnrealizedPnlBase >= 0 ? '+' : ''}${fmt(summary.totalUnrealizedPnlBase)} ${ccy}`}
            {#if summary?.totalUnrealizedPnlPct != null}
                <span class="stat-desc" class:text-success={summary.totalUnrealizedPnlPct >= 0} class:text-error={summary.totalUnrealizedPnlPct < 0}>
                    ({summary.totalUnrealizedPnlPct >= 0 ? '+' : ''}{fmt(summary.totalUnrealizedPnlPct)}%)
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

{#if summary && summary.excludedHoldingCount > 0}
    <p class="text-xs text-base-content/50 mt-1">
        Portfolio totals exclude {summary.excludedHoldingCount}
        holding{summary.excludedHoldingCount === 1 ? '' : 's'} with unavailable market prices
        ({summary.excludedHoldingNames.join(', ')}).
    </p>
{/if}
