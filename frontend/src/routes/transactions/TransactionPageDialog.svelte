<script lang="ts">
    import type { Asset, Portfolio, Transaction } from '$lib/api/types.gen';
    import TransactionForm from '../portfolios/[id]/components/TransactionForm.svelte';

    let { open, portfolios, assets, transaction, defaultPortfolioId, onsuccess, oncancel }: {
        open: boolean;
        portfolios: Portfolio[];
        assets: Asset[];
        transaction: Transaction | null;
        defaultPortfolioId: number | undefined;
        onsuccess: () => void;
        oncancel: () => void;
    } = $props();

    let internalPortfolioId = $state<number | undefined>(undefined);

    const portfolioId = $derived(
        transaction != null ? transaction.portfolioId : (defaultPortfolioId ?? internalPortfolioId)
    );

    $effect(() => {
        if (!open) internalPortfolioId = undefined;
    });
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') oncancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{transaction ? 'Edit transaction' : 'Record transaction'}</h3>

        {#if portfolioId == null}
            <div class="space-y-4">
                <fieldset class="fieldset">
                    <legend class="fieldset-legend">Portfolio</legend>
                    <select class="select w-full" bind:value={internalPortfolioId}>
                        <option value={undefined}>Select a portfolio…</option>
                        {#each portfolios as p}
                            <option value={p.id}>{p.name}</option>
                        {/each}
                    </select>
                </fieldset>
                <div class="modal-action mt-6">
                    <button type="button" class="btn btn-ghost" onclick={oncancel}>Cancel</button>
                </div>
            </div>
        {:else}
            {#key open}
                <TransactionForm
                    {assets}
                    holdings={[]}
                    {portfolioId}
                    {transaction}
                    onSuccess={onsuccess}
                    onCancel={oncancel}
                />
            {/key}
        {/if}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
