<script lang="ts">
    import type { Asset, Transaction } from '$lib/api/types.gen';

    let { transactions, assets, onedit, ondelete }: {
        transactions: Transaction[];
        assets: Asset[];
        onedit: (t: Transaction) => void;
        ondelete: (t: Transaction) => void;
    } = $props();

    function listingTicker(listingId: number): string {
        for (const a of assets) {
            const l = a.listings.find(l => l.id === listingId);
            if (l) return l.ticker;
        }
        return '—';
    }

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
</script>

<section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Transactions</h2>
    <div class="overflow-x-auto">
        <table class="table table-zebra w-full">
            <thead>
            <tr>
                <th>Date</th>
                <th>Ticker</th>
                <th>Type</th>
                <th class="text-right">Qty</th>
                <th class="text-right">Price</th>
                <th class="text-right">Fees</th>
                <th>Broker</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            {#each transactions as t}
                <tr>
                    <td class="tabular-nums">{t.date}</td>
                    <td class="font-mono font-semibold">{listingTicker(t.listingId)}</td>
                    <td>
                        <span class="badge badge-ghost badge-sm {t.type === 'BUY' ? 'text-success' : t.type === 'SELL' ? 'text-error' : 'text-info'}">
                            {t.type}
                        </span>
                    </td>
                    <td class="text-right tabular-nums">{t.type === 'SPLIT' ? `${t.quantity}×` : fmt(t.quantity)}</td>
                    <td class="text-right tabular-nums">{t.type === 'SPLIT' ? '—' : fmt(t.price)}</td>
                    <td class="text-right tabular-nums">{t.fees != null ? fmt(t.fees) : '—'}</td>
                    <td class="text-base-content/60 text-sm">
                        <span class="inline-flex items-center gap-1.5">
                            {t.broker ?? '—'}
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
                    <td class="text-right">
                        <button
                            class="btn btn-ghost btn-xs"
                            title="Edit"
                            onclick={() => onedit(t)}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                            </svg>
                        </button>
                        <button
                            class="btn btn-ghost btn-xs text-error"
                            title="Delete"
                            onclick={() => ondelete(t)}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="3 6 5 6 21 6"/>
                                <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                                <path d="M10 11v6M14 11v6"/>
                                <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                            </svg>
                        </button>
                    </td>
                </tr>
            {/each}
            </tbody>
        </table>
    </div>
</section>
