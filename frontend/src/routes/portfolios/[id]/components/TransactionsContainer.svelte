<script lang="ts">
    import type { Asset, Holding, Transaction } from '$lib/api/types.gen';
    import { removeTransaction } from '$lib/api/sdk.gen';
    import TransactionDialog from './TransactionDialog.svelte';
    import TransactionsTable from './TransactionsTable.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';

    let { portfolioId, assets, holdings, transactions, currentPage, totalPages, onchange, onpagechange, createOpen = $bindable(false) }: {
        portfolioId: number;
        assets: Asset[];
        holdings: Holding[];
        transactions: Transaction[];
        currentPage: number;
        totalPages: number;
        onchange: () => void;
        onpagechange: (page: number) => void;
        createOpen?: boolean;
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
    <TransactionsTable
        {transactions}
        {assets}
        onedit={openEdit}
        ondelete={(t) => { deleteError = null; deletingTransaction = t; }}
    />
    {#if totalPages > 1}
        <div class="flex justify-center items-center gap-2 pt-2">
            <button
                class="btn btn-ghost btn-sm"
                disabled={currentPage === 0}
                onclick={() => onpagechange(currentPage - 1)}
            >«</button>
            <span class="text-sm text-base-content/60">Page {currentPage + 1} of {totalPages}</span>
            <button
                class="btn btn-ghost btn-sm"
                disabled={currentPage >= totalPages - 1}
                onclick={() => onpagechange(currentPage + 1)}
            >»</button>
        </div>
    {/if}
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

<ConfirmModal
    open={deletingTransaction !== null}
    title="Delete transaction"
    submitting={deleteSubmitting}
    error={deleteError}
    onconfirm={handleDelete}
    oncancel={() => deletingTransaction = null}
>
    Are you sure you want to delete this transaction? This will affect your holdings.
</ConfirmModal>
