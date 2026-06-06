<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {getPortfolio, getHoldings, listAssets} from '$lib/api/sdk.gen';
    import type {Asset, Holding, Portfolio} from '$lib/api/types.gen';
    import TransactionForm from './TransactionForm.svelte';
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

    const totalCost = $derived(holdings.reduce((sum, h) => sum + h.totalCost, 0));
    const totalGain = $derived(
        holdings.length > 0 && holdings.every((h) => h.unrealizedGain != null)
            ? holdings.reduce((sum, h) => sum + (h.unrealizedGain ?? 0), 0)
            : null
    );

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
