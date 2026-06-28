<script lang="ts">
    import type { Asset, Listing } from '$lib/api/types.gen';

    interface ListingItem { listing: Listing; asset: Asset; }

    interface Props {
        assets: Asset[];
        value?: number;  // listingId
        autofocus?: boolean;
    }

    let { assets, value = $bindable(0), autofocus = false }: Props = $props();

    let query = $state('');
    let open = $state(false);
    let highlighted = $state(-1);
    let selectedId = $state(-1);
    let containerEl: HTMLDivElement | undefined = $state();
    let inputEl: HTMLInputElement | undefined = $state();
    let dropdownLeft = $state(0);
    let dropdownTop = $state(0);
    let dropdownWidth = $state(0);

    // Moves node to document.body so it escapes any overflow/stacking context
    function portal(node: HTMLElement) {
        document.body.appendChild(node);
        return { destroy() { node.remove(); } };
    }

    function syncDropdownPos() {
        if (!inputEl) return;
        const r = inputEl.getBoundingClientRect();
        dropdownLeft = r.left;
        dropdownTop = r.bottom;
        dropdownWidth = r.width;
    }

    $effect.pre(() => {
        if (value !== selectedId) {
            selectedId = value;
            query = displayFor(value);
        }
    });

    function displayFor(listingId: number): string {
        if (listingId === 0) return '';
        for (const a of assets) {
            const l = a.listings.find(l => l.id === listingId);
            if (l) return l.exchange ? `${l.ticker} — ${a.name} · ${l.exchange}` : `${l.ticker} — ${a.name}`;
        }
        return '';
    }

    const q = $derived(query.trim().toLowerCase());

    const allMatching = $derived(
        value !== 0 ? [] :
        q.length === 0 ? assets :
        assets.filter(a =>
            a.name.toLowerCase().includes(q) ||
            a.listings.some(l => l.ticker.toLowerCase().includes(q))
        )
    );

    const matchingAssets = $derived(allMatching.slice(0, 8));
    const hasMore = $derived(allMatching.length > 8);

    const visibleListings = $derived<ListingItem[]>(
        matchingAssets.flatMap(a => a.listings.map(l => ({ listing: l, asset: a })))
    );

    const highlightMap = $derived(
        new Map(visibleListings.map((item, i) => [item.listing.id, i]))
    );

    function handleInput(e: Event) {
        query = (e.currentTarget as HTMLInputElement).value;
        selectedId = 0;
        value = 0;
        open = true;
        highlighted = -1;
        syncDropdownPos();
    }

    function select(listing: Listing, asset: Asset) {
        value = listing.id;
        selectedId = listing.id;
        query = listing.exchange ? `${listing.ticker} — ${asset.name} · ${listing.exchange}` : `${listing.ticker} — ${asset.name}`;
        open = false;
        highlighted = -1;
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (!open) { open = true; syncDropdownPos(); return; }
            highlighted = Math.min(highlighted + 1, visibleListings.length - 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            highlighted = Math.max(highlighted - 1, 0);
        } else if (e.key === 'Enter' && open && highlighted >= 0) {
            e.preventDefault();
            const item = visibleListings[highlighted];
            if (item) select(item.listing, item.asset);
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
        bind:this={inputEl}
        class="input w-full"
        type="text"
        placeholder="Search by ticker or name..."
        value={query}
        oninput={handleInput}
        onkeydown={handleKeydown}
        onblur={handleBlur}
        onfocus={() => { syncDropdownPos(); open = true; }}
        autocomplete="off"
        {autofocus}
    />

    {#if open && value === 0 && matchingAssets.length > 0}
        <ul
            use:portal
            style="position: fixed; top: {dropdownTop}px; left: {dropdownLeft}px; width: {dropdownWidth}px;"
            class="z-[9999] bg-base-100 border border-base-300 rounded-box shadow-lg max-h-72 overflow-y-auto"
        >
            {#each matchingAssets as asset}
                <li class="px-4 pt-2 pb-0.5">
                    <span class="text-xs font-semibold text-base-content/50 uppercase tracking-wide">{asset.name}</span>
                </li>

                {#each asset.listings as listing}
                    <li>
                        <button
                            type="button"
                            class="w-full text-left pl-6 pr-4 py-1.5 flex items-center gap-2 hover:bg-base-200"
                            class:bg-base-200={highlightMap.get(listing.id) === highlighted}
                            onmousedown={(e) => { e.preventDefault(); select(listing, asset); }}
                        >
                            <span class="text-base-content/30 text-xs" aria-hidden="true">↳</span>
                            <span class="font-mono font-semibold text-sm">{listing.ticker}</span>
                            {#if listing.exchange}
                                <span class="text-base-content/60 text-sm">{listing.exchange}</span>
                            {/if}
                            <span class="ml-auto text-xs text-base-content/40 shrink-0">{listing.currency}</span>
                        </button>
                    </li>
                {/each}
            {/each}

            {#if hasMore}
                <li class="px-4 py-2 text-xs text-base-content/40 italic border-t border-base-200 mt-1">Type for more…</li>
            {/if}
        </ul>
    {:else if open && value === 0 && q.length > 0}
        <div
            use:portal
            style="position: fixed; top: {dropdownTop}px; left: {dropdownLeft}px; width: {dropdownWidth}px;"
            class="z-[9999] bg-base-100 border border-base-300 rounded-box shadow-lg px-4 py-3 text-sm text-base-content/60"
        >
            No assets found.
        </div>
    {/if}
</div>
