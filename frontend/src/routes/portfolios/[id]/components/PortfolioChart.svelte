<script lang="ts">
    import type { Holding } from '$lib/api/types.gen';
    import { Chart, ArcElement, Tooltip, Legend, DoughnutController } from 'chart.js';
    import { CHART_COLORS } from './chartColors';

    Chart.register(ArcElement, Tooltip, Legend, DoughnutController);

    let { holdings }: { holdings: Holding[] } = $props();

    let chartCanvas = $state<HTMLCanvasElement | null>(null);

    const totalMarketValue = $derived(holdings.reduce((sum, h) => sum + (h.marketValueBase ?? 0), 0));
    const sortedHoldings = $derived(
        [...holdings].sort((a, b) => (b.marketValueBase ?? 0) - (a.marketValueBase ?? 0)),
    );
    const chartHeight = $derived(Math.max(220, sortedHoldings.length * 24));

    $effect(() => {
        if (!chartCanvas || sortedHoldings.length === 0) return;

        const textColor = getComputedStyle(chartCanvas).color;

        const chart = new Chart(chartCanvas, {
            type: 'doughnut',
            data: {
                labels: sortedHoldings.map((h) => h.listings[0]?.ticker ?? '—'),
                datasets: [{
                    data: sortedHoldings.map((h) => h.marketValueBase ?? 0),
                    backgroundColor: sortedHoldings.map((_, i) => CHART_COLORS[i % CHART_COLORS.length]),
                    borderWidth: 0,
                }],
            },
            options: {
                responsive: false,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'right',
                        labels: {
                            color: textColor,
                            boxWidth: 12,
                            generateLabels: (chart) => {
                                const dataset = chart.data.datasets[0];
                                const values = dataset.data as number[];
                                const total = values.reduce((s, v) => s + v, 0);
                                return (chart.data.labels as string[]).map((label, i) => ({
                                    text: `${label}  ${total > 0 ? Math.round((values[i] / total) * 100) : 0}%`,
                                    fillStyle: (dataset.backgroundColor as string[])[i],
                                    fontColor: textColor,
                                    strokeStyle: 'transparent',
                                    lineWidth: 0,
                                    hidden: false,
                                    index: i,
                                }));
                            },
                        },
                    },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => {
                                const pct = totalMarketValue > 0 ? ((ctx.parsed / totalMarketValue) * 100).toFixed(1) : '0';
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

<div class="bg-base-200 rounded-box p-4 flex items-center justify-center h-full">
    <canvas bind:this={chartCanvas} width="220" height={chartHeight}></canvas>
</div>
