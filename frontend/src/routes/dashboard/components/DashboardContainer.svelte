<script lang="ts">
    import '$lib/client';
    import { listDashboardWidgets, addDashboardWidget, removeDashboardWidget } from '$lib/api/sdk.gen';
    import type { DashboardWidget, DashboardWidgetType } from '$lib/api/types.gen';
    import { onMount } from 'svelte';
    import ListingPriceWidgetContainer from './ListingPriceWidgetContainer.svelte';
    import PortfolioValueWidgetContainer from './PortfolioValueWidgetContainer.svelte';
    import AddWidgetModal from './AddWidgetModal.svelte';

    let widgets = $state<DashboardWidget[]>([]);
    let loading = $state(true);
    let addModalOpen = $state(false);

    onMount(async () => {
        const { data } = await listDashboardWidgets();
        widgets = data ?? [];
        loading = false;
    });

    async function handleAddSubmit(type: DashboardWidgetType, targetId: number, range: string) {
        const { data, error } = await addDashboardWidget({
            body: { type, config: { targetId, range } },
        });
        if (error || !data) throw new Error();
        widgets = [...widgets, data];
        addModalOpen = false;
    }

    async function handleRemove(id: number) {
        await removeDashboardWidget({ path: { id } });
        widgets = widgets.filter(w => w.id !== id);
    }
</script>

<div class="space-y-6">
    <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold">Dashboard</h1>
        <button class="btn btn-primary btn-sm" onclick={() => { addModalOpen = true; }}>
            + Add widget
        </button>
    </div>

    {#if loading}
        <div class="flex justify-center py-16"><span class="loading loading-spinner loading-lg"></span></div>
    {:else if widgets.length === 0}
        <div class="flex flex-col items-center justify-center py-16 gap-3">
            <p class="text-base-content/40 italic text-sm">No widgets yet. Add one to get started.</p>
            <button class="btn btn-primary btn-sm" onclick={() => { addModalOpen = true; }}>Add widget</button>
        </div>
    {:else}
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            {#each widgets as widget (widget.id)}
                {#if widget.type === 'LISTING_PRICE'}
                    <ListingPriceWidgetContainer {widget} onremove={() => handleRemove(widget.id)} />
                {:else}
                    <PortfolioValueWidgetContainer {widget} onremove={() => handleRemove(widget.id)} />
                {/if}
            {/each}
        </div>
    {/if}
</div>

<AddWidgetModal open={addModalOpen} onsubmit={handleAddSubmit} oncancel={() => { addModalOpen = false; }} />
