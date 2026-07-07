<script lang="ts">
    import type { Asset } from '$lib/api/types.gen';
    import CryptoTradeForm from './CryptoTradeForm.svelte';

    let { open, portfolioId, assets, onsuccess, oncancel }: {
        open: boolean;
        portfolioId: number;
        assets: Asset[];
        onsuccess: () => void;
        oncancel: () => void;
    } = $props();
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') oncancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box max-w-2xl">
        <h3 class="text-lg font-bold mb-6">Record crypto trade</h3>
        {#key open}
            <CryptoTradeForm
                {assets}
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
