<script lang="ts">
    import type { Account, Asset, Holding, Transaction, Transfer } from '$lib/api/types.gen';
    import { removeTransaction, removeTransfer } from '$lib/api/sdk.gen';
    import TransactionDialog from './TransactionDialog.svelte';
    import CryptoTradeDialog from './CryptoTradeDialog.svelte';
    import TransferDialog from './TransferDialog.svelte';
    import TransactionsTable from '$lib/transaction/TransactionsTable.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';

    let { portfolioId, assets, accounts, holdings, transactions, transfers, onchange, createOpen = $bindable(false), tradeOpen = $bindable(false), transferOpen = $bindable(false) }: {
        portfolioId: number;
        assets: Asset[];
        accounts: Account[];
        holdings: Holding[];
        transactions: Transaction[];
        transfers: Transfer[];
        onchange: () => void;
        createOpen?: boolean;
        tradeOpen?: boolean;
        transferOpen?: boolean;
    } = $props();

    let editingTransaction = $state<Transaction | null>(null);
    let deletingTransaction = $state<Transaction | null>(null);
    let deleteSubmitting = $state(false);
    let deleteError = $state<string | null>(null);

    let deletingTransfer = $state<Transfer | null>(null);
    let deleteTransferSubmitting = $state(false);
    let deleteTransferError = $state<string | null>(null);

    const modalOpen = $derived(createOpen || editingTransaction !== null);

    function openEdit(t: Transaction) {
        editingTransaction = t;
    }

    function closeModal() {
        createOpen = false;
        tradeOpen = false;
        transferOpen = false;
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

    async function handleDeleteTransfer() {
        if (!deletingTransfer) return;
        deleteTransferSubmitting = true;
        deleteTransferError = null;
        const { error: err } = await removeTransfer({ path: { portfolioId, id: deletingTransfer.id } });
        if (err) {
            deleteTransferError = 'Failed to delete transfer.';
        } else {
            deletingTransfer = null;
            onchange();
        }
        deleteTransferSubmitting = false;
    }
</script>

{#if transactions.length > 0 || transfers.length > 0}
    <h2 class="text-lg font-semibold">Recent transactions</h2>
    <TransactionsTable
        {transactions}
        {transfers}
        {assets}
        onedit={openEdit}
        ondelete={(t) => { deleteError = null; deletingTransaction = t; }}
        ondeletetransfer={(t) => { deleteTransferError = null; deletingTransfer = t; }}
    />
    <div class="flex justify-end pt-2">
        <a href="/transactions?portfolioId={portfolioId}" class="btn btn-ghost btn-sm">See all transactions →</a>
    </div>
{/if}

<TransactionDialog
    open={modalOpen}
    {portfolioId}
    {assets}
    {accounts}
    {holdings}
    transaction={editingTransaction}
    onsuccess={handleTransactionSuccess}
    oncancel={closeModal}
/>

<CryptoTradeDialog
    open={tradeOpen}
    {portfolioId}
    {assets}
    {accounts}
    onsuccess={() => { tradeOpen = false; onchange(); }}
    oncancel={() => { tradeOpen = false; }}
/>

<TransferDialog
    open={transferOpen}
    {portfolioId}
    {assets}
    {accounts}
    onsuccess={() => { transferOpen = false; onchange(); }}
    oncancel={() => { transferOpen = false; }}
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

<ConfirmModal
    open={deletingTransfer !== null}
    title="Delete transfer"
    submitting={deleteTransferSubmitting}
    error={deleteTransferError}
    onconfirm={handleDeleteTransfer}
    oncancel={() => deletingTransfer = null}
>
    Are you sure you want to delete this transfer?
</ConfirmModal>
