<script lang="ts">
    import { onMount } from 'svelte';
    import { listAccounts, createAccount, updateAccount, deleteAccount } from '$lib/api/sdk.gen';
    import type { Account, AccountRequest } from '$lib/api/types.gen';
    import AccountDialog from './AccountDialog.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';
    import '$lib/client';

    let accounts = $state<Account[]>([]);
    let loading = $state(true);
    let error = $state<string | null>(null);

    let modalOpen = $state(false);
    let editingAccount = $state<Account | null>(null);
    let formSubmitting = $state(false);
    let formError = $state<string | null>(null);

    let deletingAccount = $state<Account | null>(null);
    let deleteSubmitting = $state(false);
    let deleteError = $state<string | null>(null);

    async function load() {
        loading = true;
        error = null;
        const { data, error: err } = await listAccounts();
        if (err) {
            error = 'Failed to load accounts.';
        } else {
            accounts = data ?? [];
        }
        loading = false;
    }

    function openCreate() {
        editingAccount = null;
        formError = null;
        modalOpen = true;
    }

    function openEdit(account: Account) {
        editingAccount = account;
        formError = null;
        modalOpen = true;
    }

    function closeModal() {
        modalOpen = false;
        editingAccount = null;
        formError = null;
    }

    async function handleSubmit(body: AccountRequest) {
        formSubmitting = true;
        formError = null;

        const res = editingAccount
            ? await updateAccount({ path: { id: editingAccount.id }, body })
            : await createAccount({ body });

        if (res.error) {
            formError = editingAccount ? 'Failed to update account.' : 'Failed to create account.';
            formSubmitting = false;
            return;
        }

        formSubmitting = false;
        closeModal();
        await load();
    }

    async function handleDelete() {
        if (!deletingAccount) return;
        deleteSubmitting = true;
        deleteError = null;
        const { error: err } = await deleteAccount({ path: { id: deletingAccount.id } });
        if (err) {
            const msg = (err as { detail?: string })?.detail;
            deleteError = msg ?? 'Failed to delete account.';
            deleteSubmitting = false;
            return;
        }
        deletingAccount = null;
        deleteSubmitting = false;
        await load();
    }

    onMount(load);
</script>

<div class="max-w-4xl mx-auto p-4 sm:p-6 space-y-6">
    <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold">Accounts</h1>
        <button class="btn btn-primary btn-sm" onclick={openCreate}>New account</button>
    </div>

    {#if loading}
        <div class="flex justify-center py-12">
            <span class="loading loading-spinner loading-lg"></span>
        </div>
    {:else if error}
        <div class="alert alert-error"><span>{error}</span></div>
    {:else if accounts.length === 0}
        <div class="text-center py-12 text-base-content/50">
            <p>No accounts yet.</p>
            <button class="btn btn-primary btn-sm mt-4" onclick={openCreate}>Create your first account</button>
        </div>
    {:else}
        <div class="overflow-x-auto">
            <table class="table table-zebra w-full">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Type</th>
                        <th>Broker / Institution</th>
                        <th>Currency</th>
                        <th class="text-right">Transactions</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    {#each accounts as account}
                        <tr>
                            <td class="font-medium">{account.name}</td>
                            <td>
                                <span class="badge badge-ghost badge-sm">{account.accountType}</span>
                            </td>
                            <td class="text-base-content/60 text-sm">{account.broker ?? account.institution ?? '—'}</td>
                            <td class="text-base-content/60 text-sm">{account.currency ?? '—'}</td>
                            <td class="text-right tabular-nums text-sm">{account.transactionCount}</td>
                            <td class="text-right">
                                <button class="btn btn-ghost btn-xs" title="Edit" onclick={() => openEdit(account)}>
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                                    </svg>
                                </button>
                                <button class="btn btn-ghost btn-xs text-error" title="Delete" onclick={() => { deleteError = null; deletingAccount = account; }}>
                                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <polyline points="3 6 5 6 21 6"/>
                                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                                        <path d="M10 11v6M14 11v6"/>
                                        <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                                    </svg>
                                </button>
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
</div>

<AccountDialog
    open={modalOpen}
    account={editingAccount}
    submitting={formSubmitting}
    error={formError}
    onsubmit={handleSubmit}
    oncancel={closeModal}
/>

<ConfirmModal
    open={deletingAccount !== null}
    title="Delete account"
    submitting={deleteSubmitting}
    error={deleteError}
    onconfirm={handleDelete}
    oncancel={() => { deletingAccount = null; deleteError = null; }}
>
    {#if deletingAccount && deletingAccount.transactionCount > 0}
        <strong>{deletingAccount.name}</strong> has {deletingAccount.transactionCount} transaction{deletingAccount.transactionCount === 1 ? '' : 's'}. You must reassign or delete those transactions before deleting this account.
    {:else}
        Are you sure you want to delete <strong>{deletingAccount?.name}</strong>?
    {/if}
</ConfirmModal>
