<script lang="ts">
    import type { Portfolio } from '$lib/api/types.gen';

    let { open, portfolios, onexport, oncancel }: {
        open: boolean;
        portfolios: Portfolio[];
        onexport: (portfolioIds?: number[]) => void;
        oncancel: () => void;
    } = $props();

    let selected = $state<Set<number>>(new Set());

    $effect(() => {
        if (open) selected = new Set(portfolios.map((p) => p.id));
    });

    const allSelected = $derived(portfolios.length > 0 && selected.size === portfolios.length);

    function toggle(id: number) {
        const next = new Set(selected);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        selected = next;
    }

    function toggleAll() {
        selected = allSelected ? new Set() : new Set(portfolios.map((p) => p.id));
    }

    function handleExport() {
        onexport(allSelected ? undefined : Array.from(selected));
    }

    function handleClose() {
        oncancel();
    }
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') handleClose(); }} />

<dialog class="modal modal-middle" class:modal-open={open}>
    <div class="modal-box w-11/12 max-w-md">
        <h3 class="text-lg font-bold mb-4">Export data</h3>

        <label class="label w-full cursor-pointer justify-start gap-2 py-1">
            <input type="checkbox" class="checkbox checkbox-sm" checked={allSelected} onchange={toggleAll} />
            <span class="label-text font-medium">All portfolios</span>
        </label>

        <div class="divider my-1"></div>

        <div class="max-h-64 overflow-y-auto flex flex-col space-y-0.5">
            {#each portfolios as portfolio}
                <label class="label w-full cursor-pointer justify-start gap-2 py-1">
                    <input
                        type="checkbox"
                        class="checkbox checkbox-sm"
                        checked={selected.has(portfolio.id)}
                        onchange={() => toggle(portfolio.id)}
                    />
                    <span class="label-text">{portfolio.name}</span>
                </label>
            {/each}
        </div>

        <p class="text-xs text-base-content/50 mt-4">
            Only the assets and accounts used by the selected portfolios will be included.
        </p>

        <div class="modal-action">
            <button class="btn" onclick={handleClose}>Cancel</button>
            <button class="btn btn-primary" disabled={selected.size === 0} onclick={handleExport}>
                Export
            </button>
        </div>
    </div>

    <form method="dialog" class="modal-backdrop">
        <button onclick={handleClose}>close</button>
    </form>
</dialog>
