<script lang="ts">
    import type { Holding } from '$lib/api/types.gen';
    import { Chart, ArcElement, Tooltip, Legend, DoughnutController } from 'chart.js';

    Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

    const CHART_COLORS = [
        '#7c6ff7', '#22d3ee', '#4ade80', '#f87171', '#fbbf24',
        '#a78bfa', '#34d399', '#fb923c', '#60a5fa', '#f472b6',
    ];

    let { holdings }: { holdings: Holding[] } = $props();

    let chartCanvas = $state<HTMLCanvasElement | null>(null);

    const totalCost = $derived(holdings.reduce((sum, h) => sum + (h.totalCostBase ?? 0), 0));

    $effect(() => {
        if (!chartCanvas || holdings.length === 0) return;

        const chart = new Chart(chartCanvas, {
            type: 'doughnut',
            data: {
                labels: holdings.map((h) => h.listings[0]?.ticker ?? '—'),
                datasets: [{
                    data: holdings.map((h) => h.totalCostBase ?? 0),
                    backgroundColor: holdings.map((_, i) => CHART_COLORS[i % CHART_COLORS.length]),
                    borderWidth: 0,
                }],
            },
            options: {
                plugins: {
                    legend: { position: 'right', labels: { color: '#e2e8f0', boxWidth: 12 } },
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

        return () => chart.destroy();
    });
</script>

<div class="shrink-0 flex items-center justify-center bg-base-200 rounded-box p-4">
    <canvas bind:this={chartCanvas} width="200" height="200"></canvas>
</div>
