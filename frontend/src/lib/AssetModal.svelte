<script lang="ts">
    import { createAsset, updateAsset } from '$lib/api/sdk.gen';
    import type { Asset, AssetType } from '$lib/api/types.gen';

    const ASSET_TYPES: AssetType[] = ['STOCK', 'ETF', 'CRYPTO', 'OTHER'];

    interface Props {
        open: boolean;
        asset?: Asset | null;
        onSuccess: (asset: Asset) => void;
        onCancel: () => void;
    }

    const { open, asset = null, onSuccess, onCancel }: Props = $props();

    let ticker = $state('');
    let name = $state('');
    let type = $state<AssetType>('STOCK');
    let currency = $state('USD');
    let submitting = $state(false);
    let error = $state<string | null>(null);

    $effect(() => {
        if (open) {
            ticker = asset?.ticker ?? '';
            name = asset?.name ?? '';
            type = asset?.type ?? 'STOCK';
            currency = asset?.currency ?? 'USD';
            error = null;
        }
    });

    const canSubmit = $derived(ticker.trim() !== '' && name.trim() !== '' && currency.trim() !== '' && !submitting);

    async function handleSubmit(e: Event) {
        e.preventDefault();
        submitting = true;
        error = null;

        const body = {
            ticker: ticker.trim().toUpperCase(),
            name: name.trim(),
            type,
            currency: currency.trim().toUpperCase(),
        };

        const { data, error: err, response } = asset
            ? await updateAsset({ path: { id: asset.id }, body })
            : await createAsset({ body });

        if (err) {
            error = response.status === 409
                ? 'A ticker with that symbol already exists.'
                : asset ? 'Failed to update asset.' : 'Failed to add asset.';
            submitting = false;
            return;
        }
        submitting = false;
        onSuccess(data!);
    }
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{asset ? 'Edit asset' : 'Add asset'}</h3>
        <form onsubmit={handleSubmit} class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
                <fieldset class="fieldset">
                    <legend class="fieldset-legend">Ticker</legend>
                    <input
                        class="input w-full"
                        type="text"
                        placeholder="e.g. AAPL"
                        bind:value={ticker}
                        disabled={submitting}
                        required
                    />
                </fieldset>
                <fieldset class="fieldset">
                    <legend class="fieldset-legend">Currency</legend>
                    <input
                        class="input w-full"
                        type="text"
                        placeholder="e.g. USD"
                        bind:value={currency}
                        disabled={submitting}
                        required
                    />
                </fieldset>
            </div>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">Name</legend>
                <input
                    class="input w-full"
                    type="text"
                    placeholder="e.g. Apple Inc."
                    bind:value={name}
                    disabled={submitting}
                    required
                />
            </fieldset>
            <fieldset class="fieldset">
                <legend class="fieldset-legend">Type</legend>
                <select class="select w-full" bind:value={type} disabled={submitting}>
                    {#each ASSET_TYPES as t}
                        <option value={t}>{t}</option>
                    {/each}
                </select>
            </fieldset>

            {#if error}
                <div class="alert alert-error text-sm"><span>{error}</span></div>
            {/if}

            <div class="modal-action mt-6">
                <button type="button" class="btn btn-ghost" disabled={submitting} onclick={onCancel}>
                    Cancel
                </button>
                <button type="submit" class="btn btn-primary" disabled={!canSubmit}>
                    {#if submitting}
                        <span class="loading loading-spinner loading-sm"></span>
                    {:else}
                        {asset ? 'Save' : 'Add asset'}
                    {/if}
                </button>
            </div>
        </form>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onCancel}>close</button>
    </form>
</dialog>