<script lang="ts">
    let { open, onclose, onselect }: {
        open: boolean;
        onclose: () => void;
        onselect: (broker: string) => void;
    } = $props();

    const brokers: { id: string; name: string; description: string }[] = [
        { id: 'bolero', name: 'Bolero', description: 'Belgian broker — imports cash movements (.xlsx)' },
    ];
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box">
        <h3 class="text-lg font-bold mb-4">Import transactions</h3>
        <p class="text-sm text-base-content/70 mb-4">Select your broker to continue.</p>

        <ul class="space-y-2">
            {#each brokers as broker}
                <li>
                    <button
                        class="w-full text-left p-4 rounded-box border border-base-300 hover:bg-base-200 transition-colors"
                        onclick={() => onselect(broker.id)}
                    >
                        <div class="font-semibold">{broker.name}</div>
                        <div class="text-sm text-base-content/60 mt-0.5">{broker.description}</div>
                    </button>
                </li>
            {/each}
        </ul>

        <div class="modal-action">
            <button class="btn" onclick={onclose}>Cancel</button>
        </div>
    </div>

    <form method="dialog" class="modal-backdrop">
        <button onclick={onclose}>close</button>
    </form>
</dialog>
