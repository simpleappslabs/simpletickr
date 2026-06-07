<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {goto} from '$app/navigation';
    import {getPortfolio, getHoldings, listAssets, deletePortfolio} from '$lib/api/sdk.gen';
    import type {Asset, Holding, Portfolio} from '$lib/api/types.gen';
    import TransactionForm from './TransactionForm.svelte';
    import RenamePortfolioModal from '$lib/RenamePortfolioModal.svelte';
    import {Chart, ArcElement, Tooltip, Legend, DoughnutController} from 'chart.js';
    import '$lib/client';

    Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

    const CHART_COLORS = [
        '#7c6ff7', '#22d3ee', '#4ade80', '#f87171', '#fbbf24',
        '#a78bfa', '#34d399', '#fb923c', '#60a5fa', '#f472b6',
    ];

    let portfolio = $state<Portfolio | null>(null);
    let holdings = $state<Holding[]>([]);
    let assets = $state<Asset[]>([]);
    let loading = $state(true);
    let notFound = $state(false);
    let error = $state<string | null>(null);

    let chartCanvas = $state<HTMLCanvasElement | null>(null);
    let chart: Chart | null = null;

    let modalOpen = $state(false);

    let renamePortfolio = $state<Portfolio | null>(null);

    let deleteOpen = $state(false);
    let deleteSubmitting = $state(false);
    let deleteError = $state<string | null>(null);

    const totalCost = $derived(holdings.reduce((sum, h) => sum + h.totalCost, 0));
    const totalGain = $derived(
        holdings.length > 0 && holdings.every((h) => h.unrealizedGain != null)
            ? holdings.reduce((sum, h) => sum + (h.unrealizedGain ?? 0), 0)
            : null
    );

    async function handleDelete() {
        if (!portfolio) return;
        deleteSubmitting = true;
        deleteError = null;
        const { error: err } = await deletePortfolio({ path: { id: portfolio.id } });
        if (err) {
            deleteError = 'Failed to delete portfolio.';
            deleteSubmitting = false;
        } else {
            goto('/');
        }
    }

    async function onTransactionSuccess() {
        if (!portfolio) return;
        const holdingsRes = await getHoldings({ path: { id: portfolio.id } });
        holdings = holdingsRes.data ?? [];
        modalOpen = false;
        if (holdings.length > 0) {
            await new Promise((r) => setTimeout(r, 0));
            buildChart();
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

        const [portfolioRes, holdingsRes, assetsRes] = await Promise.all([
            getPortfolio({path: {id}}),
            getHoldings({path: {id}}),
            listAssets()
        ]);

        if (portfolioRes.error) {
            notFound = true;
            loading = false;
            return;
        }

        portfolio = portfolioRes.data ?? null;
        holdings = holdingsRes.data ?? [];
        assets = assetsRes.data ?? [];
        loading = false;

        if (holdings.length > 0) {
            // Wait for canvas to be rendered
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
            <button class="btn btn-ghost btn-sm" title="Rename" onclick={() => renamePortfolio = portfolio}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
            </button>
            <button class="btn btn-ghost btn-sm text-error" title="Delete" onclick={() => { deleteError = null; deleteOpen = true; }}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                    <path d="M10 11v6M14 11v6"/>
                    <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                </svg>
            </button>
            <button class="btn btn-primary btn-sm" onclick={() => modalOpen = true}>+ Record transaction</button>
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
            <!-- Chart + table -->
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
    {/if}
</div>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={modalOpen}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">Record transaction</h3>

        {#if portfolio}
            <TransactionForm
                {assets}
                portfolioId={portfolio.id}
                onSuccess={onTransactionSuccess}
                onCancel={() => modalOpen = false}
            />
        {/if}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={() => modalOpen = false}>close</button>
    </form>
</dialog>

<RenamePortfolioModal
    portfolio={renamePortfolio}
    onSuccess={(updated) => { portfolio = updated; renamePortfolio = null; }}
    onCancel={() => renamePortfolio = null}
/>

<!-- Delete confirmation modal -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={deleteOpen}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-2">Delete portfolio</h3>
        <p class="text-base-content/70 mb-6">
            Are you sure you want to delete <strong>{portfolio?.name}</strong>?
            All transactions will be permanently removed.
        </p>
        {#if deleteError}
            <div class="alert alert-error mb-4"><span>{deleteError}</span></div>
        {/if}
        <div class="modal-action">
            <button class="btn btn-ghost" disabled={deleteSubmitting} onclick={() => deleteOpen = false}>
                Cancel
            </button>
            <button class="btn btn-error" disabled={deleteSubmitting} onclick={handleDelete}>
                {deleteSubmitting ? 'Deleting…' : 'Delete'}
            </button>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={() => deleteOpen = false}>close</button>
    </form>
</dialog>
