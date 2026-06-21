<script lang="ts">
    import type { Holding } from '$lib/api/types.gen';

    let { holdings }: { holdings: Holding[] } = $props();

    let expandedAssets = $state<Set<number>>(new Set());

    function toggleExpand(assetId: number) {
        const next = new Set(expandedAssets);
        if (next.has(assetId)) next.delete(assetId);
        else next.add(assetId);
        expandedAssets = next;
    }

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function fmtCcy(n: number | undefined | null, ccy: string) {
        if (n == null) return '—';
        return `${fmt(n)} ${ccy}`;
    }
</script>

<div class="flex-1 min-w-0 overflow-x-auto">
    <table class="table table-zebra table-sm w-full">
        <thead>
        <tr>
            <th></th>
            <th>Asset</th>
            <th class="text-right">Qty</th>
            <th class="text-right">Avg cost</th>
            <th class="text-right">Total cost</th>
            <th class="text-right">Market value</th>
            <th class="text-right">Gain</th>
        </tr>
        </thead>
        <tbody>
        {#each holdings as h}
            <tr class="cursor-pointer hover" onclick={() => toggleExpand(h.assetId)}>
                <td class="w-6 text-base-content/40">
                    {#if h.listings.length > 1}
                        {expandedAssets.has(h.assetId) ? '▾' : '▸'}
                    {/if}
                </td>
                <td>
                    <div class="font-semibold">{h.assetName}</div>
                    <div class="text-xs text-base-content/50 font-mono">{h.listings.map(l => l.ticker).join(' · ')}</div>
                </td>
                <td class="text-right tabular-nums">{fmt(h.totalQuantity)}</td>
                <td class="text-right tabular-nums">
                    {h.avgCostBasisBase != null ? fmtCcy(h.avgCostBasisBase, h.baseCurrency) : '—'}
                </td>
                <td class="text-right tabular-nums">
                    {fmtCcy(h.totalCostBase, h.baseCurrency)}
                </td>
                <td class="text-right tabular-nums">
                    {fmtCcy(h.marketValueBase, h.baseCurrency)}
                </td>
                <td class="text-right tabular-nums">
                    {#if h.unrealizedPnlBase == null}
                        <span class="text-base-content/30">—</span>
                    {:else}
                        <span class="{h.unrealizedPnlBase >= 0 ? 'text-success' : 'text-error'}">
                            {h.unrealizedPnlBase >= 0 ? '+' : ''}{fmt(h.unrealizedPnlBase)} {h.baseCurrency}
                            {#if h.unrealizedPnlPct != null}
                                <span class="text-xs opacity-70">({h.unrealizedPnlPct >= 0 ? '+' : ''}{fmt(h.unrealizedPnlPct)}%)</span>
                            {/if}
                        </span>
                    {/if}
                </td>
            </tr>
            {#if expandedAssets.has(h.assetId) && h.listings.length > 1}
                {#each h.listings as l}
                    <tr class="bg-base-300/30 text-sm">
                        <td></td>
                        <td class="pl-6 text-base-content/70">
                            <span class="font-mono">{l.ticker}</span>
                            {#if l.exchange}
                                <span class="text-xs text-base-content/40 ml-1">{l.exchange}</span>
                            {/if}
                        </td>
                        <td class="text-right tabular-nums">{fmt(l.quantity)}</td>
                        <td class="text-right tabular-nums">{fmtCcy(l.avgCostLocal, l.currency)}</td>
                        <td class="text-right tabular-nums">{fmtCcy(l.totalCostLocal, l.currency)}</td>
                        <td class="text-right tabular-nums">{fmtCcy(l.marketValueBase, h.baseCurrency)}</td>
                        <td></td>
                    </tr>
                {/each}
            {/if}
        {/each}
        </tbody>
    </table>
</div>
