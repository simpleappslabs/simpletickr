<script lang="ts">
    import { onMount } from 'svelte';
    import { page } from '$app/state';
    import { getPortfolio, getRealizedGains } from '$lib/api/sdk.gen';
    import type { Portfolio, RealizedGainsReport, RealizationMethod } from '$lib/api/types.gen';
    import '$lib/client';

    const portfolioId = Number(page.params.id);

    const currentYear = new Date().getFullYear();

    let portfolio = $state<Portfolio | null>(null);
    let from = $state(`${currentYear}-01-01`);
    let to = $state(new Date().toISOString().slice(0, 10));
    let method = $state<RealizationMethod>('FIFO');
    let report = $state<RealizedGainsReport | null>(null);
    let loading = $state(false);
    let error = $state<string | null>(null);
    let notFound = $state(false);

    onMount(async () => {
        const res = await getPortfolio({ path: { id: portfolioId } });
        if (res.error) { notFound = true; return; }
        portfolio = res.data ?? null;
    });

    async function calculate() {
        loading = true;
        error = null;
        report = null;
        const res = await getRealizedGains({
            path: { id: portfolioId },
            query: { method, from, to },
        });
        if (res.error) {
            error = 'Failed to compute realized gains.';
        } else {
            report = res.data ?? null;
        }
        loading = false;
    }

    function fmt(n: number) {
        return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    const currencyTotals = $derived(
        report ? Object.values(report.byCurrency) : []
    );

    const swapTotalsByCurrency = $derived(
        report?.entries.reduce((map, e) => {
            if (!e.tradeId) return map;
            const cur = e.currency;
            map[cur] ??= { count: 0, gain: 0 };
            map[cur].count++;
            map[cur].gain += e.gain;
            return map;
        }, {} as Record<string, { count: number; gain: number }>) ?? {}
    );
</script>

<div class="max-w-4xl mx-auto p-4 sm:p-6 space-y-8">
    <div class="flex items-center gap-3">
        <a href="/portfolios/{portfolioId}" class="btn btn-ghost btn-sm">← {portfolio?.name ?? 'Portfolio'}</a>
        <h1 class="text-2xl font-bold flex-1">Realized gains</h1>
    </div>

    {#if notFound}
        <div class="alert alert-error"><span>Portfolio not found.</span></div>
    {:else}
        <!-- Calculator form -->
        <div class="bg-base-200 rounded-box p-6">
            <div class="flex flex-wrap gap-4 items-end">
                <fieldset class="fieldset">
                    <legend class="fieldset-legend">Method</legend>
                    <select class="select" bind:value={method}>
                        <option value="FIFO">FIFO</option>
                        <option value="AVERAGE_COST">Average cost</option>
                    </select>
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">From</legend>
                    <input class="input" type="date" bind:value={from} />
                </fieldset>

                <fieldset class="fieldset">
                    <legend class="fieldset-legend">To</legend>
                    <input class="input" type="date" bind:value={to} />
                </fieldset>

                <button class="btn btn-primary" onclick={calculate} disabled={loading}>
                    {#if loading}
                        <span class="loading loading-spinner loading-sm"></span>
                    {:else}
                        Calculate
                    {/if}
                </button>
            </div>
        </div>

        {#if error}
            <div class="alert alert-error"><span>{error}</span></div>
        {/if}

        {#if report}
            {#if report.entries.length === 0}
                <p class="text-base-content/40 italic text-sm">No realized gains in this period.</p>
            {:else}
                <!-- Per-currency summary stats -->
                {#each currencyTotals as ct}
                    {@const swaps = swapTotalsByCurrency[ct.currency]}
                    <div class="stats stats-vertical sm:stats-horizontal bg-base-200 w-full">
                        <div class="stat">
                            <div class="stat-title">Proceeds <span class="badge badge-ghost badge-sm ml-1">{ct.currency}</span></div>
                            <div class="stat-value text-xl">{fmt(ct.totalProceeds)} {ct.currency}</div>
                            <div class="stat-desc">{ct.tradeCount} trade{ct.tradeCount === 1 ? '' : 's'}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-title">Cost basis</div>
                            <div class="stat-value text-xl">{fmt(ct.totalCostBasis)} {ct.currency}</div>
                        </div>
                        <div class="stat">
                            <div class="stat-title">Gain / loss</div>
                            <div class="stat-value text-xl {ct.totalGain >= 0 ? 'text-success' : 'text-error'}">
                                {ct.totalGain >= 0 ? '+' : ''}{fmt(ct.totalGain)} {ct.currency}
                            </div>
                            {#if swaps}
                                <div class="stat-desc">
                                    incl. {swaps.count} crypto swap{swaps.count === 1 ? '' : 's'}: {swaps.gain >= 0 ? '+' : ''}{fmt(swaps.gain)} {ct.currency}
                                </div>
                            {/if}
                        </div>
                    </div>
                {/each}

                <!-- Entries table -->
                <section class="space-y-3">
                    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">
                        Transactions — {report.method === 'AVERAGE_COST' ? 'Average cost' : 'FIFO'}
                    </h2>
                    <div class="overflow-x-auto">
                        <table class="table table-zebra w-full text-sm">
                            <thead>
                                <tr>
                                    <th>Date</th>
                                    <th>Ticker</th>
                                    <th>CCY</th>
                                    <th class="text-right">Qty</th>
                                    <th class="text-right">Proceeds</th>
                                    <th class="text-right">Buy fees</th>
                                    <th class="text-right">Sell fees</th>
                                    <th class="text-right">Cost basis</th>
                                    <th class="text-right">Gain / loss</th>
                                </tr>
                            </thead>
                            <tbody>
                                {#each report.entries as e}
                                    <tr>
                                        <td class="tabular-nums">{e.date}</td>
                                        <td class="font-mono font-semibold">
                                            {e.ticker}
                                            {#if e.receivedTicker}
                                                <span class="badge badge-outline badge-xs ml-1 font-normal normal-case tracking-normal align-middle">↔ {e.receivedTicker}</span>
                                            {/if}
                                        </td>
                                        <td class="text-xs text-base-content/50">{e.currency}</td>
                                        <td class="text-right tabular-nums">{fmt(e.quantity)}</td>
                                        <td class="text-right tabular-nums">{fmt(e.proceeds)}</td>
                                        <td class="text-right tabular-nums">{e.buyFees > 0 ? fmt(e.buyFees) : '—'}</td>
                                        <td class="text-right tabular-nums">{e.sellFees > 0 ? fmt(e.sellFees) : '—'}</td>
                                        <td class="text-right tabular-nums">{fmt(e.costBasis)}</td>
                                        <td class="text-right tabular-nums font-semibold {e.gain >= 0 ? 'text-success' : 'text-error'}">
                                            {e.gain >= 0 ? '+' : ''}{fmt(e.gain)} {e.currency}
                                        </td>
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                </section>
            {/if}
        {/if}
    {/if}
</div>
