<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {goto} from '$app/navigation';
    import {getPortfolio, getHoldings, listAssets, listTransactions, deletePortfolio, removeTransaction} from '$lib/api/sdk.gen';
    import type {Asset, Holding, Portfolio, Transaction} from '$lib/api/types.gen';
    import TransactionForm from './TransactionForm.svelte';
    import PortfolioModal from '$lib/PortfolioModal.svelte';
    import {Chart, ArcElement, Tooltip, Legend, DoughnutController} from 'chart.js';
    import '$lib/client';

    Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

    const CHART_COLORS = [
        '#7c6ff7', '#22d3ee', '#4ade80', '#f87171', '#fbbf24',
        '#a78bfa', '#34d399', '#fb923c', '#60a5fa', '#f472b6',
    ];

    let portfolio = $state<Portfolio | null>(null);
    let holdings = $state<Holding[]>([]);
    let transactions = $state<Transaction[]>([]);
    let assets = $state<Asset[]>([]);
    let loading = $state(true);
    let notFound = $state(false);
    let error = $state<string | null>(null);

    let chartCanvas = $state<HTMLCanvasElement | null>(null);
    let chart: Chart | null = null;

    let editingTransaction = $state<Transaction | null>(null);
    let transactionModalOpen = $state(false);

    let deletingTransaction = $state<Transaction | null>(null);
    let deleteTransactionSubmitting = $state(false);
    let deleteTransactionError = $state<string | null>(null);

    let renameModalOpen = $state(false);
    let renamePortfolio = $state<Portfolio | null>(null);

    let deletePortfolioOpen = $state(false);
    let deletePortfolioSubmitting = $state(false);
    let deletePortfolioError = $state<string | null>(null);

    const totalCost = $derived(holdings.reduce((sum, h) => sum + h.totalCost, 0));
    const totalGain = $derived(
        holdings.length > 0 && holdings.every((h) => h.unrealizedGain != null)
            ? holdings.reduce((sum, h) => sum + (h.unrealizedGain ?? 0), 0)
            : null
    );

    function assetTicker(assetId: number): string {
        return assets.find((a) => a.id === assetId)?.ticker ?? '—';
    }

    async function refreshData() {
        if (!portfolio) return;
        const [holdingsRes, transactionsRes] = await Promise.all([
            getHoldings({ path: { id: portfolio.id } }),
            listTransactions({ query: { portfolioId: portfolio.id } }),
        ]);
        holdings = holdingsRes.data ?? [];
        transactions = transactionsRes.data ?? [];
        if (holdings.length > 0) {
            await new Promise((r) => setTimeout(r, 0));
            buildChart();
        } else {
            chart?.destroy();
            chart = null;
        }
    }

    async function onTransactionSuccess() {
        transactionModalOpen = false;
        editingTransaction = null;
        await refreshData();
    }

    async function handleDeleteTransaction() {
        if (!deletingTransaction) return;
        deleteTransactionSubmitting = true;
        deleteTransactionError = null;
        const { error: err } = await removeTransaction({ path: { portfolioId: portfolio!.id, id: deletingTransaction.id } });
        if (err) {
            deleteTransactionError = 'Failed to delete transaction.';
        } else {
            deletingTransaction = null;
            await refreshData();
        }
        deleteTransactionSubmitting = false;
    }

    async function handleDeletePortfolio() {
        if (!portfolio) return;
        deletePortfolioSubmitting = true;
        deletePortfolioError = null;
        const { error: err } = await deletePortfolio({ path: { id: portfolio.id } });
        if (err) {
            deletePortfolioError = 'Failed to delete portfolio.';
            deletePortfolioSubmitting = false;
        } else {
            goto('/');
        }
    }

    function buildChart() {
        if (!chartCanvas || holdings.length === 0) return;
        chart?.destroy();
        chart = new Chart(chartCanvas, {
            type: 'doughnut',
            data: {
                labels: holdings.map((h) => h.ticker),
                datasets: [{
                    data: holdings.map((h) => h.totalCost),
                    backgroundColor: holdings.map((_, i) => CHART_COLORS[i % CHART_COLORS.length]),
                    borderWidth: 0,
                }],
            },
            options: {
                plugins: {
                    legend: {position: 'right', labels: {color: '#e2e8f0', boxWidth: 12}},
                    tooltip: {
                        callbacks: {
                            label: (ctx) => {
                                const pct = totalCost > 0 ? ((ctx.parsed / totalCost) * 100).toFixed(1) : '0';
                                return ` ${ctx.label}: ${pct}%`;
                            },
                        },
                    },
                },
            },
        });
    }

    function fmt(n: number) {
        return n.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    }

    onMount(async () => {
        const id = Number(page.params.id);

        const [portfolioRes, holdingsRes, assetsRes, transactionsRes] = await Promise.all([
            getPortfolio({path: {id}}),
            getHoldings({path: {id}}),
            listAssets(),
            listTransactions({query: {portfolioId: id}}),
        ]);

        if (portfolioRes.error) {
            notFound = true;
            loading = false;
            return;
        }

        portfolio = portfolioRes.data ?? null;
        holdings = holdingsRes.data ?? [];
        assets = assetsRes.data ?? [];
        transactions = transactionsRes.data ?? [];
        loading = false;

        if (holdings.length > 0) {
            await new Promise((r) => setTimeout(r, 0));
            buildChart();
        }
    });
</script>

<div class="max-w-4xl mx-auto p-6 space-y-8">
    <div class="flex items-center gap-3">
        <a href="/" class="btn btn-ghost btn-sm">← Portfolios</a>
        {#if portfolio}
            <h1 class="text-2xl font-bold flex-1">{portfolio.name}</h1>
            <button class="btn btn-ghost btn-sm" title="Rename" onclick={() => { renamePortfolio = portfolio; renameModalOpen = true; }}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
            </button>
            <button class="btn btn-ghost btn-sm text-error" title="Delete portfolio" onclick={() => { deletePortfolioError = null; deletePortfolioOpen = true; }}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                    <path d="M10 11v6M14 11v6"/>
                    <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                </svg>
            </button>
            <a href="/portfolios/{portfolio.id}/realized-gains" class="btn btn-ghost btn-sm">Realized gains</a>
            <button class="btn btn-primary btn-sm" onclick={() => { editingTransaction = null; transactionModalOpen = true; }}>+ Record transaction</button>
        {/if}
    </div>

    {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
    {:else if notFound}
        <div class="alert alert-error"><span>Portfolio not found.</span></div>
    {:else if error}
        <div class="alert alert-error"><span>{error}</span></div>
    {:else}
        <!-- Stats row -->
        <div class="stats bg-base-200 w-full">
            <div class="stat">
                <div class="stat-title">Total cost</div>
                <div class="stat-value text-xl">${fmt(totalCost)}</div>
            </div>
            <div class="stat">
                <div class="stat-title">Unrealized gain</div>
                <div class="stat-value text-xl {totalGain == null ? '' : totalGain >= 0 ? 'text-success' : 'text-error'}">
                    {totalGain == null ? '—' : `${totalGain >= 0 ? '+' : ''}$${fmt(totalGain)}`}
                </div>
            </div>
            <div class="stat">
                <div class="stat-title">Positions</div>
                <div class="stat-value text-xl">{holdings.length}</div>
            </div>
        </div>

        {#if holdings.length === 0}
            <p class="text-base-content/40 italic text-sm">No holdings yet. Record a transaction to get started.</p>
        {:else}
            <!-- Chart + holdings table -->
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div class="lg:col-span-1 flex items-center justify-center bg-base-200 rounded-box p-4">
                    <canvas bind:this={chartCanvas} width="260" height="260"></canvas>
                </div>

                <div class="lg:col-span-2 overflow-x-auto">
                    <table class="table table-zebra w-full">
                        <thead>
                        <tr>
                            <th>Ticker</th>
                            <th>Name</th>
                            <th class="text-right">Qty</th>
                            <th class="text-right">Avg cost</th>
                            <th class="text-right">Total cost</th>
                            <th class="text-right">Gain</th>
                        </tr>
                        </thead>
                        <tbody>
                        {#each holdings as h}
                            <tr>
                                <td class="font-mono font-semibold">{h.ticker}</td>
                                <td>{h.name}</td>
                                <td class="text-right">{fmt(h.quantity)}</td>
                                <td class="text-right">${fmt(h.avgCostBasis)}</td>
                                <td class="text-right">${fmt(h.totalCost)}</td>
                                <td class="text-right">
                                    {#if h.unrealizedGain == null}
                                        <span class="text-base-content/30">—</span>
                                    {:else}
                                        <span class="{h.unrealizedGain >= 0 ? 'text-success' : 'text-error'}">
                                            {h.unrealizedGain >= 0 ? '+' : ''}${fmt(h.unrealizedGain)}
                                        </span>
                                    {/if}
                                </td>
                            </tr>
                        {/each}
                        </tbody>
                    </table>
                </div>
            </div>
        {/if}

        <!-- Transactions table -->
        {#if transactions.length > 0}
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
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        {#each transactions as t}
                            <tr>
                                <td class="tabular-nums">{t.date}</td>
                                <td class="font-mono font-semibold">{assetTicker(t.assetId)}</td>
                                <td>
                                    <span class="badge badge-ghost badge-sm {t.type === 'BUY' ? 'text-success' : 'text-error'}">
                                        {t.type}
                                    </span>
                                </td>
                                <td class="text-right tabular-nums">{fmt(t.quantity)}</td>
                                <td class="text-right tabular-nums">${fmt(t.price)}</td>
                                <td class="text-right tabular-nums">{t.fees != null ? `$${fmt(t.fees)}` : '—'}</td>
                                <td class="text-right">
                                    <button
                                        class="btn btn-ghost btn-xs"
                                        title="Edit"
                                        onclick={() => { editingTransaction = t; transactionModalOpen = true; }}
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                                            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                                        </svg>
                                    </button>
                                    <button
                                        class="btn btn-ghost btn-xs text-error"
                                        title="Delete"
                                        onclick={() => { deleteTransactionError = null; deletingTransaction = t; }}
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
        {/if}
    {/if}
</div>

<!-- Transaction modal (create + edit) -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={transactionModalOpen}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{editingTransaction ? 'Edit transaction' : 'Record transaction'}</h3>

        {#if portfolio}
            <TransactionForm
                {assets}
                portfolioId={portfolio.id}
                transaction={editingTransaction}
                onSuccess={onTransactionSuccess}
                onCancel={() => { transactionModalOpen = false; editingTransaction = null; }}
            />
        {/if}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={() => { transactionModalOpen = false; editingTransaction = null; }}>close</button>
    </form>
</dialog>

<PortfolioModal
    open={renameModalOpen}
    portfolio={renamePortfolio}
    onSuccess={(updated) => { portfolio = updated; renamePortfolio = null; renameModalOpen = false; }}
    onCancel={() => { renamePortfolio = null; renameModalOpen = false; }}
/>

<!-- Delete transaction confirmation modal -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={deletingTransaction !== null}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-2">Delete transaction</h3>
        <p class="text-base-content/70 mb-6">
            Are you sure you want to delete this transaction? This will affect your holdings.
        </p>
        {#if deleteTransactionError}
            <div class="alert alert-error mb-4"><span>{deleteTransactionError}</span></div>
        {/if}
        <div class="modal-action">
            <button class="btn btn-ghost" disabled={deleteTransactionSubmitting} onclick={() => deletingTransaction = null}>
                Cancel
            </button>
            <button class="btn btn-error" disabled={deleteTransactionSubmitting} onclick={handleDeleteTransaction}>
                {deleteTransactionSubmitting ? 'Deleting…' : 'Delete'}
            </button>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={() => deletingTransaction = null}>close</button>
    </form>
</dialog>

<!-- Delete portfolio confirmation modal -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={deletePortfolioOpen}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-2">Delete portfolio</h3>
        <p class="text-base-content/70 mb-6">
            Are you sure you want to delete <strong>{portfolio?.name}</strong>?
            All transactions will be permanently removed.
        </p>
        {#if deletePortfolioError}
            <div class="alert alert-error mb-4"><span>{deletePortfolioError}</span></div>
        {/if}
        <div class="modal-action">
            <button class="btn btn-ghost" disabled={deletePortfolioSubmitting} onclick={() => deletePortfolioOpen = false}>
                Cancel
            </button>
            <button class="btn btn-error" disabled={deletePortfolioSubmitting} onclick={handleDeletePortfolio}>
                {deletePortfolioSubmitting ? 'Deleting…' : 'Delete'}
            </button>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={() => deletePortfolioOpen = false}>close</button>
    </form>
</dialog>
