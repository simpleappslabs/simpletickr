<script lang="ts">
    import type { Account, Asset, Portfolio } from '$lib/api/types.gen';
    import TransferForm from './TransferForm.svelte';

    let { open, portfolioId, assets, accounts, portfolios, onsuccess, oncancel }: {
        open: boolean;
        portfolioId: number;
        assets: Asset[];
        accounts: Account[];
        portfolios: Portfolio[];
        onsuccess: () => void;
        oncancel: () => void;
    } = $props();
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') oncancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box max-w-2xl">
        <h3 class="text-lg font-bold mb-6">Record transfer</h3>
        {#key open}
            <TransferForm
                {assets}
                {accounts}
                {portfolios}
                {portfolioId}
                onSuccess={onsuccess}
                onCancel={oncancel}
            />
        {/key}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
