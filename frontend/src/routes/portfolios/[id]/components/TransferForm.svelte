<script lang="ts">
    import { recordTransfer } from '$lib/api/sdk.gen';
    import type { Account, Asset } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';
    import AccountAutocomplete from '$lib/AccountAutocomplete.svelte';

    interface Props {
        assets: Asset[];
        accounts: Account[];
        portfolioId: number;
        onSuccess: () => void;
        onCancel: () => void;
    }

    const { assets, accounts, portfolioId, onSuccess, onCancel }: Props = $props();

    let listingId = $state(0);
    let quantity = $state('');
    let assetFeeQuantity = $state('');
    let formDate = $state(new Date().toISOString().slice(0, 10));
    let sourceAccountId = $state(0);
    let destinationAccountId = $state(0);
    let formNotes = $state('');
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    const destinationAccounts = $derived(accounts.filter(a => a.id !== sourceAccountId));

    const feeValid = $derived(
        assetFeeQuantity === '' || (quantity !== '' && Number(assetFeeQuantity) < Number(quantity))
    );

    const canSubmit = $derived(
        listingId > 0 && quantity !== '' && formDate !== '' &&
        sourceAccountId > 0 && destinationAccountId > 0 && sourceAccountId !== destinationAccountId &&
        feeValid &&
        !formSubmitting
    );

    async function submit(e: SubmitEvent) {
        e.preventDefault();
        formSubmitting = true;
        formError = null;
        const { error: err } = await recordTransfer({
            path: { portfolioId },
            body: {
                listingId,
                quantity: Number(quantity),
                assetFeeQuantity: assetFeeQuantity !== '' ? Number(assetFeeQuantity) : undefined,
                date: formDate,
                sourceAccountId,
                destinationAccountId,
                notes: formNotes || undefined,
            },
        });
        formSubmitting = false;
        if (err) {
            const detail = (err as { detail?: string })?.detail;
            formError = detail ?? 'Failed to record transfer.';
        } else {
            onSuccess();
        }
    }
</script>

<form class="space-y-4" onsubmit={submit}>
    <div class="alert text-xs py-2">
        <span>Transfers move an asset between accounts within this portfolio. No price is recorded — cost basis is derived automatically from your existing purchase history.</span>
    </div>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Asset</legend>
        <AssetAutocomplete {assets} bind:value={listingId} />
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Quantity</legend>
            <input class="input w-full" type="number" min="0" step="any" placeholder="0.00" bind:value={quantity} required />
        </fieldset>

        <fieldset class="fieldset">
            <legend class="fieldset-legend">Date</legend>
            <input
                class="input w-full"
                type="date"
                required
                value={formDate}
                onchange={(e) => { formDate = (e.currentTarget as HTMLInputElement).value; }}
            />
        </fieldset>
    </div>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Fee <span class="text-base-content/40 font-normal">(in same asset, optional)</span></legend>
        <input class="input w-full" type="number" min="0" step="any" placeholder="0.00" bind:value={assetFeeQuantity} />
        <p class="text-xs text-base-content/50 mt-1">
            E.g. crypto gas — this amount is lost, not received anywhere. Cash/fiat transfer fees are not supported.
        </p>
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">From account</legend>
        <AccountAutocomplete {accounts} bind:value={sourceAccountId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">To account</legend>
        <AccountAutocomplete accounts={destinationAccounts} bind:value={destinationAccountId} />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Notes <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <textarea class="textarea w-full" rows="2" placeholder="Any context" bind:value={formNotes}></textarea>
    </fieldset>

    {#if formError}
        <div class="alert alert-error text-sm"><span>{formError}</span></div>
    {/if}

    <div class="modal-action mt-6">
        <button type="button" class="btn btn-ghost" onclick={onCancel}>Cancel</button>
        <button type="submit" class="btn btn-primary" disabled={!canSubmit}>
            {#if formSubmitting}
                <span class="loading loading-spinner loading-sm"></span>
            {:else}
                Record transfer
            {/if}
        </button>
    </div>
</form>
