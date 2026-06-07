<script lang="ts">
    import { updatePortfolio } from '$lib/api/sdk.gen';
    import type { Portfolio } from '$lib/api/types.gen';

    interface Props {
        portfolio: Portfolio | null;
        onSuccess: (updated: Portfolio) => void;
        onCancel: () => void;
    }

    const { portfolio, onSuccess, onCancel }: Props = $props();

    let name = $state('');
    let submitting = $state(false);
    let error = $state<string | null>(null);

    $effect(() => {
        if (portfolio) {
            name = portfolio.name;
            error = null;
        }
    });

    async function handleSubmit(e: Event) {
        e.preventDefault();
        if (!portfolio || !name.trim()) return;
        submitting = true;
        error = null;
        const { data, error: err } = await updatePortfolio({
            path: { id: portfolio.id },
            body: { name: name.trim() },
        });
        if (err || !data) {
            error = 'Failed to rename portfolio.';
        } else {
            onSuccess(data);
        }
        submitting = false;
    }
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={portfolio !== null}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">Rename portfolio</h3>
        <form onsubmit={handleSubmit} class="space-y-4">
            <input
                type="text"
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
                    {submitting ? 'Saving…' : 'Save'}
                </button>
            </div>
        </form>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onCancel}>close</button>
    </form>
</dialog>
