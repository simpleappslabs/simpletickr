<script lang="ts">
    import type { Asset, Listing } from '$lib/api/types.gen';

    interface ListingOption {
        listing: Listing;
        assetName: string;
    }

    interface Props {
        assets: Asset[];
        value?: number;  // listingId
    }

    let { assets, value = $bindable(0) }: Props = $props();

    let query = $state('');
    let open = $state(false);
    let highlighted = $state(-1);
    let selectedId = $state(-1);
    let containerEl: HTMLDivElement | undefined = $state();

    const allOptions = $derived<ListingOption[]>(
        assets.flatMap(a => a.listings.map(l => ({ listing: l, assetName: a.name })))
    );

    function displayFor(listingId: number): string {
        const opt = allOptions.find(o => o.listing.id === listingId);
        if (!opt) return '';
        const { listing, assetName } = opt;
        return listing.exchange
            ? `${listing.ticker} — ${assetName} [${listing.exchange}]`
            : `${listing.ticker} — ${assetName}`;
    }

    $effect.pre(() => {
        if (value !== selectedId) {
            selectedId = value;
            query = displayFor(value);
        }
    });

    const filtered = $derived(
        value === 0
            ? (query.trim().length === 0
                ? allOptions.slice(0, 8)
                : allOptions
                    .filter(o =>
                        o.listing.ticker.toLowerCase().includes(query.toLowerCase()) ||
                        o.assetName.toLowerCase().includes(query.toLowerCase())
                    )
                    .slice(0, 8))
            : []
    );

    const matched = $derived(
        value === 0
            ? (query.trim().length === 0
                ? allOptions
                : allOptions.filter(o =>
                    o.listing.ticker.toLowerCase().includes(query.toLowerCase()) ||
                    o.assetName.toLowerCase().includes(query.toLowerCase())
                ))
            : []
    );
    const hasMore = $derived(matched.length > 8);

    function handleInput(e: Event) {
        query = (e.currentTarget as HTMLInputElement).value;
        selectedId = 0;
        value = 0;
        open = true;
        highlighted = -1;
    }

    function select(opt: ListingOption) {
        value = opt.listing.id;
        selectedId = opt.listing.id;
        const { listing, assetName } = opt;
        query = listing.exchange
            ? `${listing.ticker} — ${assetName} [${listing.exchange}]`
            : `${listing.ticker} — ${assetName}`;
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
                {#each filtered as opt, i}
                    <li>
                        <button
                            type="button"
                            class="w-full text-left px-4 py-2 flex items-center gap-2 hover:bg-base-200"
                            class:bg-base-200={i === highlighted}
                            onmousedown={(e) => { e.preventDefault(); select(opt); }}
                        >
                            <span class="font-mono font-semibold text-sm">{opt.listing.ticker}</span>
                            <span class="text-base-content/60 text-sm truncate">{opt.assetName}</span>
                            {#if opt.listing.exchange}
                                <span class="text-base-content/40 text-xs shrink-0">{opt.listing.exchange}</span>
                            {/if}
                            <span class="ml-auto text-xs text-base-content/40 shrink-0">{opt.listing.currency}</span>
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
