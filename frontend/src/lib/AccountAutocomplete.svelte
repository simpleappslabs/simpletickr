<script lang="ts">
    import type { Account } from '$lib/api/types.gen';

    interface Props {
        accounts: Account[];
        value?: number;
        onselect?: (accountId: number) => void;
    }

    let { accounts, value = $bindable(0), onselect }: Props = $props();

    let query = $state('');
    let open = $state(false);
    let highlighted = $state(-1);
    let selectedId = $state(-1);
    let previousValue = $state(0);
    let containerEl: HTMLDivElement | undefined = $state();
    let inputEl: HTMLInputElement | undefined = $state();
    let dropdownLeft = $state(0);
    let dropdownTop = $state(0);
    let dropdownWidth = $state(0);

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

    function displayFor(accountId: number): string {
        if (accountId === 0) return '';
        const a = accounts.find(a => a.id === accountId);
        if (!a) return '';
        return a.broker ? `${a.name} (${a.broker})` : a.name;
    }

    const q = $derived(query.trim().toLowerCase());

    const allMatching = $derived(
        value !== 0 ? [] :
        q.length === 0 ? accounts :
        accounts.filter(a =>
            a.name.toLowerCase().includes(q) ||
            (a.broker ?? '').toLowerCase().includes(q) ||
            (a.institution ?? '').toLowerCase().includes(q)
        )
    );

    const visible = $derived(allMatching.slice(0, 8));
    const hasMore = $derived(allMatching.length > 8);

    function handleInput(e: Event) {
        query = (e.currentTarget as HTMLInputElement).value;
        const wasSelected = selectedId !== 0;
        selectedId = 0;
        value = 0;
        if (wasSelected) onselect?.(0);
        open = true;
        highlighted = -1;
        syncDropdownPos();
    }

    function select(account: Account) {
        value = account.id;
        selectedId = account.id;
        query = displayFor(account.id);
        open = false;
        highlighted = -1;
        previousValue = 0;
        onselect?.(account.id);
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (!open) { open = true; syncDropdownPos(); return; }
            highlighted = Math.min(highlighted + 1, visible.length - 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            highlighted = Math.max(highlighted - 1, 0);
        } else if (e.key === 'Enter' && open && highlighted >= 0) {
            e.preventDefault();
            const item = visible[highlighted];
            if (item) select(item);
        } else if (e.key === 'Escape') {
            open = false;
            highlighted = -1;
        }
    }

    function handleFocus() {
        syncDropdownPos();
        if (selectedId !== 0) {
            previousValue = selectedId;
            query = '';
            selectedId = 0;
            value = 0;
        }
        open = true;
    }

    function handleBlur(e: FocusEvent) {
        if (!containerEl?.contains(e.relatedTarget as Node)) {
            open = false;
            highlighted = -1;
            if (value === 0 && previousValue !== 0) {
                value = previousValue;
                selectedId = previousValue;
                query = displayFor(previousValue);
            } else if (value !== 0) {
                query = displayFor(value);
            }
            previousValue = 0;
        }
    }
</script>

<div class="relative" bind:this={containerEl}>
    <input
        bind:this={inputEl}
        class="input w-full"
        type="text"
        placeholder="Search accounts..."
        value={query}
        oninput={handleInput}
        onkeydown={handleKeydown}
        onblur={handleBlur}
        onfocus={handleFocus}
        onclick={handleFocus}
        autocomplete="off"
    />

    {#if open && value === 0 && visible.length > 0}
        <ul
            use:portal
            data-account-dropdown
            style="position: fixed; top: {dropdownTop}px; left: {dropdownLeft}px; width: {dropdownWidth}px;"
            class="z-[9999] bg-base-100 border border-base-300 rounded-box shadow-lg max-h-60 overflow-y-auto"
        >
            {#each visible as account, i}
                <li>
                    <button
                        type="button"
                        class="w-full text-left px-4 py-2 flex items-center gap-2 hover:bg-base-200"
                        class:bg-base-200={i === highlighted}
                        onmousedown={(e) => { e.preventDefault(); select(account); }}
                    >
                        <span class="font-medium text-sm">{account.name}</span>
                        {#if account.broker && account.broker !== account.name}
                            <span class="text-base-content/50 text-xs">{account.broker}</span>
                        {/if}
                        <span class="ml-auto text-xs text-base-content/40 shrink-0">{account.accountType}</span>
                    </button>
                </li>
            {/each}
            {#if hasMore}
                <li class="px-4 py-2 text-xs text-base-content/40 italic border-t border-base-200">Type for more…</li>
            {/if}
        </ul>
    {:else if open && value === 0 && q.length > 0}
        <div
            use:portal
            style="position: fixed; top: {dropdownTop}px; left: {dropdownLeft}px; width: {dropdownWidth}px;"
            class="z-[9999] bg-base-100 border border-base-300 rounded-box shadow-lg px-4 py-3 text-sm text-base-content/60"
        >
            No accounts found.
        </div>
    {/if}
</div>
