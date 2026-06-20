<script lang="ts">
    import type { AssetDetail } from '$lib/api/types.gen';
    import AssetForm from '$lib/asset/AssetForm.svelte';

    interface Props {
        open: boolean;
        asset?: AssetDetail | null;
        onSuccess: (asset: AssetDetail) => void;
        onCancel: () => void;
    }

    const { open, asset = null, onSuccess, onCancel }: Props = $props();
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') onCancel(); }} />

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box max-w-lg">
        {#if open}
            <AssetForm {asset} {onSuccess} {onCancel} />
        {/if}
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={onCancel}>close</button>
    </form>
</dialog>
