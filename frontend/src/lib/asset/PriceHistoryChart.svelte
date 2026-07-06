<script lang="ts">
    import type { PricePoint } from '$lib/api/types.gen';
    import {
        Chart, LineController, LineElement, PointElement,
        CategoryScale, LinearScale, Filler, Tooltip,
    } from 'chart.js';

    Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, Filler, Tooltip);

    let { points, currency }: {
        points: PricePoint[];
        currency: string;
    } = $props();

    let chartCanvas = $state<HTMLCanvasElement | null>(null);

    $effect(() => {
        if (!chartCanvas || points.length === 0) return;

        const chart = new Chart(chartCanvas, {
            type: 'line',
            data: {
                labels: points.map((p) => p.date),
                datasets: [
                    {
                        label: '',
                        data: points.map((p) => p.price),
                        borderColor: '#7c6ff7',
                        backgroundColor: 'rgba(124,111,247,0.15)',
                        fill: true,
                        tension: 0.3,
                        pointRadius: 0,
                    },
                ],
            },
            options: {
                responsive: true,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: {
                        ticks: { color: '#94a3b8', maxTicksLimit: 8 },
                        grid: { color: 'rgba(148,163,184,0.1)' },
                    },
                    y: {
                        ticks: { color: '#94a3b8' },
                        grid: { color: 'rgba(148,163,184,0.1)' },
                        title: { display: true, text: currency, color: '#94a3b8' },
                    },
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        displayColors: false,
                        callbacks: {
                            label: (ctx) => {
                                const v = ctx.parsed.y;
                                if (v == null) return '—';
                                return ` ${v.toLocaleString(undefined, { maximumFractionDigits: 2 })} ${currency}`;
                            },
                        },
                    },
                },
            },
        });

        return () => chart.destroy();
    });
</script>

<div class="bg-base-200 rounded-box p-4 w-full">
    {#if points.length === 0}
        <p class="text-base-content/40 italic text-sm text-center py-8">No price data available for this listing.</p>
    {:else}
        <canvas bind:this={chartCanvas}></canvas>
    {/if}
</div>
