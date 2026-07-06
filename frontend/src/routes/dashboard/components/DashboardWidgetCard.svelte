<script lang="ts">
    import type { Snippet } from 'svelte';

    let { title, loading, error, onremove, summary, rangeSelector, chart }: {
        title: string;
        loading: boolean;
        error: string | null;
        onremove: () => void;
        summary?: Snippet;
        rangeSelector?: Snippet;
        chart?: Snippet;
    } = $props();
</script>

<div class="card bg-base-200 shadow">
    <div class="card-body p-4">
        <div class="flex items-center justify-between gap-2 mb-2">
            <h3 class="font-semibold text-base truncate">{title}</h3>
            <button class="btn btn-ghost btn-xs text-error shrink-0" onclick={onremove} aria-label="Remove widget">✕</button>
        </div>

        {@render summary?.()}

        {@render rangeSelector?.()}

        {#if loading}
            <div class="flex justify-center py-8"><span class="loading loading-spinner loading-sm"></span></div>
        {:else if error}
            <div class="alert alert-error text-sm"><span>{error}</span></div>
        {:else}
            {@render chart?.()}
        {/if}
    </div>
</div>
