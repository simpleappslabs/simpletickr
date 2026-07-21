<script lang="ts">
    import type { Asset, Portfolio, Transaction, Transfer } from '$lib/api/types.gen';
    import { mergeLedger } from './LedgerEntry';

    let { transactions, transfers = [], assets, portfolios, onedit, ondelete, ondeletetransfer }: {
        transactions: Transaction[];
        transfers?: Transfer[];
        assets: Asset[];
        portfolios?: Portfolio[];
        onedit?: (t: Transaction) => void;
        ondelete?: (t: Transaction) => void;
        ondeletetransfer?: (t: Transfer) => void;
    } = $props();

    const entries = $derived(mergeLedger(transactions, transfers));

    function listingTicker(listingId: number): string {
        for (const a of assets) {
            const l = a.listings.find(l => l.id === listingId);
            if (l) return l.ticker;
        }
        return '—';
    }

    function portfolioName(portfolioId: number): string {
        return portfolios?.find(p => p.id === portfolioId)?.name ?? String(portfolioId);
    }

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
</script>

<div class="overflow-x-auto">
    <table class="table table-zebra w-full">
        <thead>
        <tr>
            {#if portfolios}
                <th>Portfolio</th>
            {/if}
            <th>Date</th>
            <th>Ticker</th>
            <th>Type</th>
            <th class="text-right">Qty</th>
            <th class="text-right">Price</th>
            <th class="text-right">Fees</th>
            <th>Account</th>
            {#if onedit || ondelete}
                <th></th>
            {/if}
        </tr>
        </thead>
        <tbody>
        {#each entries as entry}
            {#if entry.kind === 'transaction'}
                {@const t = entry.transaction}
                <tr>
                    {#if portfolios}
                        <td class="text-sm text-base-content/70">{portfolioName(t.portfolioId)}</td>
                    {/if}
                    <td class="tabular-nums">{t.date}</td>
                    <td class="font-mono font-semibold">{listingTicker(t.listingId)}</td>
                    <td>
                        <span class="badge badge-ghost badge-sm {t.type === 'BUY' ? 'text-success' : t.type === 'SELL' ? 'text-error' : 'text-info'}">
                            {t.type}
                        </span>
                        {#if t.tradeId != null}
                            <span class="badge badge-outline badge-xs ml-1 text-base-content/50" title="Part of a crypto trade">↔</span>
                        {/if}
                    </td>
                    <td class="text-right tabular-nums">{t.type === 'SPLIT' ? `${t.quantity}×` : fmt(t.quantity)}</td>
                    <td class="text-right tabular-nums">{t.type === 'SPLIT' ? '—' : fmt(t.price)}</td>
                    <td class="text-right tabular-nums">{t.fees != null ? fmt(t.fees) : '—'}</td>
                    <td class="text-base-content/60 text-sm">
                        <span class="inline-flex items-center gap-1.5">
                            {t.account?.name ?? '—'}
                            {#if t.notes}
                                <span title={t.notes} class="cursor-default text-base-content/40 hover:text-base-content/70 transition-colors">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                                        <polyline points="14 2 14 8 20 8"/>
                                        <line x1="16" y1="13" x2="8" y2="13"/>
                                        <line x1="16" y1="17" x2="8" y2="17"/>
                                        <polyline points="10 9 9 9 8 9"/>
                                    </svg>
                                </span>
                            {/if}
                        </span>
                    </td>
                    {#if onedit || ondelete}
                        <td class="text-right">
                            {#if onedit && t.tradeId == null}
                                <button
                                    class="btn btn-ghost btn-xs"
                                    title="Edit"
                                    onclick={() => onedit!(t)}
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                                    </svg>
                                </button>
                            {/if}
                            {#if ondelete}
                                <button
                                    class="btn btn-ghost btn-xs text-error"
                                    title="Delete"
                                    onclick={() => ondelete!(t)}
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <polyline points="3 6 5 6 21 6"/>
                                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                                        <path d="M10 11v6M14 11v6"/>
                                        <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                                    </svg>
                                </button>
                            {/if}
                        </td>
                    {/if}
                </tr>
            {:else}
                {@const tr = entry.transfer}
                <tr>
                    {#if portfolios}
                        <td class="text-sm text-base-content/70">{portfolioName(tr.portfolioId)}</td>
                    {/if}
                    <td class="tabular-nums">{tr.date}</td>
                    <td class="font-mono font-semibold">{listingTicker(tr.listingId)}</td>
                    <td>
                        <span class="badge badge-ghost badge-sm text-info">TRANSFER</span>
                        <span class="badge badge-outline badge-xs ml-1 text-base-content/50" title="Moves custody between accounts — no price or gain">⇄</span>
                    </td>
                    <td class="text-right tabular-nums">{fmt(tr.quantity)}</td>
                    <td class="text-right tabular-nums">—</td>
                    <td class="text-right tabular-nums">{tr.assetFeeQuantity != null ? fmt(tr.assetFeeQuantity) : '—'}</td>
                    <td class="text-base-content/60 text-sm">
                        {tr.sourceAccount.name} → {tr.destinationAccount.name}
                    </td>
                    {#if onedit || ondelete}
                        <td class="text-right">
                            {#if ondeletetransfer}
                                <button
                                    class="btn btn-ghost btn-xs text-error"
                                    title="Delete"
                                    onclick={() => ondeletetransfer!(tr)}
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <polyline points="3 6 5 6 21 6"/>
                                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                                        <path d="M10 11v6M14 11v6"/>
                                        <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                                    </svg>
                                </button>
                            {/if}
                        </td>
                    {/if}
                </tr>
            {/if}
        {/each}
        </tbody>
    </table>
</div>
