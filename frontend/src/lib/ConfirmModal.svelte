<script lang="ts">
    import type { Snippet } from 'svelte';

    let { open, title, submitting, error, onconfirm, oncancel, children }: {
        open: boolean;
        title: string;
        submitting: boolean;
        error: string | null;
        onconfirm: () => void;
        oncancel: () => void;
        children: Snippet;
    } = $props();
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-2">{title}</h3>
        <p class="text-base-content/70 mb-6">
            {@render children()}
        </p>
        {#if error}
            <div class="alert alert-error mb-4"><span>{error}</span></div>
        {/if}
        <div class="modal-action">
            <button class="btn btn-ghost" disabled={submitting} onclick={oncancel}>
                Cancel
            </button>
            <button class="btn btn-error" disabled={submitting} onclick={onconfirm}>
                {submitting ? 'Deleting…' : 'Delete'}
            </button>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button onclick={oncancel}>close</button>
    </form>
</dialog>
