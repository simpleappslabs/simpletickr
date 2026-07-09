<script lang="ts">
    import type { Account, AccountRequest } from '$lib/api/types.gen';
    import AccountForm from './AccountForm.svelte';

    let { open, account = null, submitting = false, error = null, onsubmit, oncancel }: {
        open: boolean;
        account?: Account | null;
        submitting?: boolean;
        error?: string | null;
        onsubmit: (body: AccountRequest) => void;
        oncancel: () => void;
    } = $props();
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') oncancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-6">{account ? 'Edit account' : 'New account'}</h3>
        {#key account?.id ?? 'new'}
            <AccountForm {account} {submitting} {error} {onsubmit} {oncancel} />
        {/key}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
