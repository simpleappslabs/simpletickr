<script lang="ts">
    import { createPortfolio, updatePortfolio } from '$lib/api/sdk.gen';
    import type { Portfolio } from '$lib/api/types.gen';

    interface Props {
        open: boolean;
        portfolio?: Portfolio | null;
        onSuccess: (portfolio: Portfolio) => void;
        onCancel: () => void;
    }

    const { open, portfolio = null, onSuccess, onCancel }: Props = $props();

    let name = $state('');
    let submitting = $state(false);
    let error = $state<string | null>(null);
    let inputEl: HTMLInputElement | undefined = $state();

    $effect(() => {
        if (open) {
            name = portfolio?.name ?? '';
            error = null;
            setTimeout(() => inputEl?.focus(), 0);
        }
    });

    async function handleSubmit(e: Event) {
        e.preventDefault();
        if (!name.trim()) return;
        submitting = true;
        error = null;
        const { data, error: err } = portfolio
            ? await updatePortfolio({ path: { id: portfolio.id }, body: { name: name.trim() } })
            : await createPortfolio({ body: { name: name.trim() } });
        if (err || !data) {
            error = portfolio ? 'Failed to rename portfolio.' : 'Failed to create portfolio.';
            submitting = false;
            return;
        }
        submitting = false;
        onSuccess(data);
    }
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') onCancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{portfolio ? 'Rename portfolio' : 'New portfolio'}</h3>
        <form onsubmit={handleSubmit} class="space-y-4">
            <input
                bind:this={inputEl}
                type="text"
                placeholder="Portfolio name"
                bind:value={name}
                disabled={submitting}
                required
                class="input input-bordered w-full"
            />
            {#if error}
                <div class="alert alert-error"><span>{error}</span></div>
            {/if}
            <div class="modal-action">
                <button type="button" class="btn btn-ghost" disabled={submitting} onclick={onCancel}>
                    Cancel
                </button>
                <button type="submit" class="btn btn-primary" disabled={submitting || !name.trim()}>
                    {#if submitting}
                        <span class="loading loading-spinner loading-sm"></span>
                    {:else}
                        {portfolio ? 'Save' : 'Create'}
                    {/if}
                </button>
            </div>
        </form>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onCancel}>close</button>
    </form>
</dialog>
