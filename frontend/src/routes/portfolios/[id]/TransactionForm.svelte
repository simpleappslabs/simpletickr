<script lang="ts">
    import { recordTransaction, amendTransaction } from '$lib/api/sdk.gen';
    import type { Asset, Transaction, TransactionType } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';

    interface Props {
        assets: Asset[];
        portfolioId: number;
        transaction?: Transaction | null;
        onSuccess: () => void;
        onCancel: () => void;
    }

    const { assets, portfolioId, transaction = null, onSuccess, onCancel }: Props = $props();

    let formListingId = $state(0);
    let formType = $state<TransactionType>('BUY');
    let formQuantity = $state('');
    let formPrice = $state('');
    let formDate = $state('');
    let formFees = $state('');
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    $effect(() => {
        if (transaction) {
            formListingId = transaction.listingId;
            formType = transaction.type;
            formQuantity = String(transaction.quantity);
            formPrice = String(transaction.price);
            formDate = transaction.date;
            formFees = transaction.fees != null ? String(transaction.fees) : '';
        } else {
            formListingId = 0;
            formType = 'BUY';
            formQuantity = '';
            formPrice = '';
            formDate = new Date().toISOString().slice(0, 10);
            formFees = '';
        }
        formError = null;
    });

    const canSubmit = $derived(
        formListingId > 0 && formQuantity !== '' && formPrice !== '' && formDate !== '' && !formSubmitting
    );

    async function submit(e: SubmitEvent) {
        e.preventDefault();
        formSubmitting = true;
        formError = null;

        const body = {
            listingId: formListingId,
            type: formType,
            quantity: Number(formQuantity),
            price: Number(formPrice),
            date: formDate,
            fees: formFees ? Number(formFees) : undefined,
        };

        const res = transaction
            ? await amendTransaction({ path: { portfolioId, id: transaction.id }, body })
            : await recordTransaction({ path: { portfolioId }, body });

        if (res.error) {
            formError = transaction ? 'Failed to update transaction.' : 'Failed to record transaction.';
            formSubmitting = false;
            return;
        }

        formSubmitting = false;
        onSuccess();
    }
</script>

<form class="space-y-4" onsubmit={submit}>
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete {assets} bind:value={formListingId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Type</legend>
        <select class="select w-full" bind:value={formType}>
            <option value="BUY">Buy</option>
            <option value="SELL">Sell</option>
        </select>
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Quantity</legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formQuantity} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Price per unit</legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formPrice} required />
        </fieldset>
    </div>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Date</legend>
            <input class="input w-full" type="date" bind:value={formDate} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Fees <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input w-full" type="number" min="0" step="any"
                   placeholder="0.00" bind:value={formFees} />
        </fieldset>
    </div>

    {#if formError}
        <div class="alert alert-error text-sm"><span>{formError}</span></div>
    {/if}

    <div class="modal-action mt-6">
        <button type="button" class="btn btn-ghost" onclick={onCancel}>Cancel</button>
        <button type="submit" class="btn btn-primary" disabled={!canSubmit}>
            {#if formSubmitting}
                <span class="loading loading-spinner loading-sm"></span>
            {:else}
                {transaction ? 'Update' : 'Record'}
            {/if}
        </button>
    </div>
</form>
