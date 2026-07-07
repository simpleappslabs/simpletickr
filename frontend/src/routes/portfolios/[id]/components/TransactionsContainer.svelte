<script lang="ts">
    import type { Asset, Holding, Transaction } from '$lib/api/types.gen';
    import { removeTransaction } from '$lib/api/sdk.gen';
    import TransactionDialog from './TransactionDialog.svelte';
    import CryptoTradeDialog from './CryptoTradeDialog.svelte';
    import TransactionsTable from '$lib/transaction/TransactionsTable.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';

    let { portfolioId, assets, holdings, transactions, onchange, createOpen = $bindable(false), tradeOpen = $bindable(false) }: {
        portfolioId: number;
        assets: Asset[];
        holdings: Holding[];
        transactions: Transaction[];
        onchange: () => void;
        createOpen?: boolean;
        tradeOpen?: boolean;
    } = $props();

    let editingTransaction = $state<Transaction | null>(null);
    let deletingTransaction = $state<Transaction | null>(null);
    let deleteSubmitting = $state(false);
    let deleteError = $state<string | null>(null);

    const modalOpen = $derived(createOpen || editingTransaction !== null);

    function openEdit(t: Transaction) {
        editingTransaction = t;
    }

    function closeModal() {
        createOpen = false;
        tradeOpen = false;
        editingTransaction = null;
    }

    async function handleTransactionSuccess() {
        closeModal();
        onchange();
    }

    async function handleDelete() {
        if (!deletingTransaction) return;
        deleteSubmitting = true;
        deleteError = null;
        const { error: err } = await removeTransaction({ path: { portfolioId, id: deletingTransaction.id } });
        if (err) {
            deleteError = 'Failed to delete transaction.';
        } else {
            deletingTransaction = null;
            onchange();
        }
        deleteSubmitting = false;
    }
</script>

{#if transactions.length > 0}
    <h2 class="text-lg font-semibold">Recent transactions</h2>
    <TransactionsTable
        {transactions}
        {assets}
        onedit={openEdit}
        ondelete={(t) => { deleteError = null; deletingTransaction = t; }}
    />
    <div class="flex justify-end pt-2">
        <a href="/transactions?portfolioId={portfolioId}" class="btn btn-ghost btn-sm">See all transactions →</a>
    </div>
{/if}

<TransactionDialog
    open={modalOpen}
    {portfolioId}
    {assets}
    {holdings}
    transaction={editingTransaction}
    onsuccess={handleTransactionSuccess}
    oncancel={closeModal}
/>

<CryptoTradeDialog
    open={tradeOpen}
    {portfolioId}
    {assets}
    onsuccess={() => { tradeOpen = false; onchange(); }}
    oncancel={() => { tradeOpen = false; }}
/>

<ConfirmModal
    open={deletingTransaction !== null}
    title="Delete transaction"
    submitting={deleteSubmitting}
    error={deleteError}
    onconfirm={handleDelete}
    oncancel={() => deletingTransaction = null}
>
    {#if deletingTransaction?.tradeId != null}
        This transaction is part of a crypto trade. Deleting it will remove <strong>both legs</strong> of the trade and affect your holdings.
    {:else}
        Are you sure you want to delete this transaction? This will affect your holdings.
    {/if}
</ConfirmModal>
