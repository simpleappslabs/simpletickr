<script lang="ts">
    import type { Asset, Holding, Transaction } from '$lib/api/types.gen';
    import TransactionForm from './TransactionForm.svelte';

    let { open, portfolioId, assets, holdings, transaction, onsuccess, oncancel }: {
        open: boolean;
        portfolioId: number;
        assets: Asset[];
        holdings: Holding[];
        transaction: Transaction | null;
        onsuccess: () => void;
        oncancel: () => void;
    } = $props();
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{transaction ? 'Edit transaction' : 'Record transaction'}</h3>
        {#key open}
            <TransactionForm
                {assets}
                {holdings}
                {portfolioId}
                {transaction}
                onSuccess={onsuccess}
                onCancel={oncancel}
            />
        {/key}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
