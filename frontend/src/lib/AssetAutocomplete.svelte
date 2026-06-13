<script lang="ts">
    import type { Asset } from '$lib/api/types.gen';

    interface Props {
        assets: Asset[];
        value?: number;
    }

    let { assets, value = $bindable(0) }: Props = $props();

    let query = $state('');
    let open = $state(false);
    let highlighted = $state(-1);
    let selectedId = $state(-1); // -1 sentinel forces initial sync
    let containerEl: HTMLDivElement | undefined = $state();

    // Sync query from value before DOM updates (covers initial render and external resets).
    // The selectedId guard prevents overwriting what the user is typing — handleInput sets
    // both value and selectedId to 0 atomically, so the effect sees them equal and skips.
    $effect.pre(() => {
        if (value !== selectedId) {
            selectedId = value;
            const a = assets.find(a => a.id === value);
            query = a ? `${a.ticker} — ${a.name}` : '';
        }
    });

    const matched = $derived(
        value === 0
            ? (query.trim().length === 0
                ? assets
                : assets.filter(a =>
                    a.ticker.toLowerCase().includes(query.toLowerCase()) ||
                    a.name.toLowerCase().includes(query.toLowerCase())
                ))
            : []
    );

    const filtered = $derived(matched.slice(0, 8));
    const hasMore = $derived(matched.length > 8);

    function handleInput(e: Event) {
        query = (e.currentTarget as HTMLInputElement).value;
        selectedId = 0;
        value = 0;
        open = true;
        highlighted = -1;
    }

    function select(asset: Asset) {
        value = asset.id;
        selectedId = asset.id;
        query = `${asset.ticker} — ${asset.name}`;
        open = false;
        highlighted = -1;
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (!open) { open = true; return; }
            highlighted = Math.min(highlighted + 1, filtered.length - 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            highlighted = Math.max(highlighted - 1, 0);
        } else if (e.key === 'Enter' && open && highlighted >= 0) {
            e.preventDefault();
            if (filtered[highlighted]) select(filtered[highlighted]);
        } else if (e.key === 'Escape') {
            open = false;
            highlighted = -1;
        }
    }

    function handleBlur(e: FocusEvent) {
        if (!containerEl?.contains(e.relatedTarget as Node)) {
            open = false;
            highlighted = -1;
        }
    }
</script>

<div class="relative" bind:this={containerEl}>
    <input
        class="input w-full"
        type="text"
        placeholder="Search by ticker or name..."
        value={query}
        oninput={handleInput}
        onkeydown={handleKeydown}
        onblur={handleBlur}
        onfocus={() => { open = true; }}
        autocomplete="off"
    />
    {#if open && value === 0}
        {#if filtered.length > 0}
            <ul class="absolute z-50 mt-1 w-full bg-base-100 border border-base-300 rounded-box shadow-lg max-h-60 overflow-y-auto">
                {#each filtered as asset, i}
                    <li>
                        <button
                            type="button"
                            class="w-full text-left px-4 py-2 flex items-center gap-2 hover:bg-base-200"
                            class:bg-base-200={i === highlighted}
                            onmousedown={(e) => { e.preventDefault(); select(asset); }}
                        >
                            <span class="font-mono font-semibold text-sm">{asset.ticker}</span>
                            <span class="text-base-content/60 text-sm truncate">{asset.name}</span>
                            <span class="ml-auto text-xs text-base-content/40 shrink-0">{asset.type}</span>
                        </button>
                    </li>
                {/each}
                {#if hasMore}
                    <li class="px-4 py-2 text-xs text-base-content/40 italic">Type for more…</li>
                {/if}
            </ul>
        {:else if query.trim().length > 0}
            <div class="absolute z-50 mt-1 w-full bg-base-100 border border-base-300 rounded-box shadow-lg px-4 py-3 text-sm text-base-content/60">
                No assets found.
            </div>
        {/if}
    {/if}
</div>
