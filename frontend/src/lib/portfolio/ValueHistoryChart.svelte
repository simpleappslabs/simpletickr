<script lang="ts">
    import type { PortfolioValuePoint } from '$lib/api/types.gen';
    import {
        Chart, LineController, LineElement, PointElement,
        CategoryScale, LinearScale, Filler, Tooltip, Legend,
    } from 'chart.js';

    Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, Filler, Tooltip, Legend);

    let { points, baseCurrency }: {
        points: PortfolioValuePoint[];
        baseCurrency: string;
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
                        label: 'Market value',
                        data: points.map((p) => p.value ?? null),
                        borderColor: '#7c6ff7',
                        backgroundColor: 'rgba(124,111,247,0.15)',
                        fill: true,
                        tension: 0.3,
                        pointRadius: 0,
                        spanGaps: false,
                    },
                    {
                        label: 'Invested',
                        data: points.map((p) => p.invested ?? null),
                        borderColor: '#22d3ee',
                        borderWidth: 1.5,
                        backgroundColor: 'transparent',
                        fill: false,
                        tension: 0.3,
                        pointRadius: 0,
                        spanGaps: false,
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
                        title: { display: true, text: baseCurrency, color: '#94a3b8' },
                    },
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => {
                                const v = ctx.parsed.y;
                                if (v == null) return `${ctx.dataset.label}: —`;
                                return ` ${ctx.dataset.label}: ${v.toLocaleString(undefined, { maximumFractionDigits: 2 })} ${baseCurrency}`;
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
        <p class="text-base-content/40 italic text-sm text-center py-8">No value history available.</p>
    {:else}
        <div class="flex gap-3 mb-2">
            <span class="flex items-center gap-1 text-xs text-base-content/60">
                <span class="inline-block w-3 h-0.5 rounded" style="background:#7c6ff7"></span> Market value
            </span>
            <span class="flex items-center gap-1 text-xs text-base-content/60">
                <span class="inline-block w-3 h-0.5 rounded" style="background:#22d3ee"></span> Invested
            </span>
        </div>
        <canvas bind:this={chartCanvas}></canvas>
    {/if}
</div>
