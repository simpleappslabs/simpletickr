<script lang="ts">
    import { onMount, untrack } from 'svelte';
    import { page } from '$app/state';
    import { goto } from '$app/navigation';
    import { listTransactions, listPortfolios, listAssets } from '$lib/api/sdk.gen';
    import type { Asset, Portfolio, Transaction, TransactionType } from '$lib/api/types.gen';
    import TransactionsTable from '$lib/transaction/TransactionsTable.svelte';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';
    import '$lib/client';

    let portfolioId = $state<number | undefined>(undefined);
    let listingId = $state(0);
    let selectedType = $state<TransactionType | ''>('');
    let dateFrom = $state('');
    let dateTo = $state('');
    let currentPage = $state(0);

    let portfolios = $state<Portfolio[]>([]);
    let assets = $state<Asset[]>([]);
    let transactions = $state<Transaction[]>([]);
    let totalPages = $state(0);
    let totalElements = $state(0);
    let loading = $state(true);
    let error = $state<string | null>(null);

    function syncFromUrl() {
        const params = page.url.searchParams;
        portfolioId = params.has('portfolioId') ? Number(params.get('portfolioId')) : undefined;
        listingId = params.has('listingId') ? Number(params.get('listingId')) : 0;
        selectedType = (params.get('type') as TransactionType) || '';
        dateFrom = params.get('dateFrom') ?? '';
        dateTo = params.get('dateTo') ?? '';
        currentPage = params.has('page') ? Number(params.get('page')) : 0;
    }

    function buildUrl(overridePage?: number): string {
        const params = new URLSearchParams();
        if (portfolioId != null) params.set('portfolioId', String(portfolioId));
        if (listingId > 0) params.set('listingId', String(listingId));
        if (selectedType) params.set('type', selectedType);
        if (dateFrom) params.set('dateFrom', dateFrom);
        if (dateTo) params.set('dateTo', dateTo);
        const p = overridePage ?? currentPage;
        if (p > 0) params.set('page', String(p));
        const qs = params.toString();
        return qs ? `/transactions?${qs}` : '/transactions';
    }

    async function fetchTransactions() {
        loading = true;
        error = null;
        const { data, error: err } = await listTransactions({
            query: {
                portfolioId,
                listingId: listingId > 0 ? listingId : undefined,
                type: selectedType || undefined,
                dateFrom: dateFrom || undefined,
                dateTo: dateTo || undefined,
                page: currentPage,
                size: 25,
            },
        });
        if (err) {
            error = 'Failed to load transactions.';
        } else {
            transactions = data?.items ?? [];
            totalPages = data?.totalPages ?? 0;
            totalElements = data?.totalElements ?? 0;
        }
        loading = false;
    }

    async function applyFilters() {
        await goto(buildUrl(0), { replaceState: false });
    }

    async function handlePageChange(newPage: number) {
        await goto(buildUrl(newPage), { replaceState: false });
    }

    async function resetFilters() {
        portfolioId = undefined;
        listingId = 0;
        selectedType = '';
        dateFrom = '';
        dateTo = '';
        await goto('/transactions', { replaceState: false });
    }

    let urlKey = $derived(page.url.searchParams.toString());

    $effect(() => {
        urlKey; // track only urlKey — state var reads inside untrack() are not dependencies
        untrack(() => {
            syncFromUrl();
            fetchTransactions();
        });
    });

    onMount(async () => {
        const [portfoliosRes, assetsRes] = await Promise.all([
            listPortfolios(),
            listAssets(),
        ]);
        portfolios = portfoliosRes.data ?? [];
        assets = assetsRes.data ?? [];
    });
</script>

<div class="max-w-4xl mx-auto p-4 sm:p-6 space-y-6">
    <h1 class="text-xl sm:text-2xl font-bold">Transactions</h1>

    <div class="flex flex-wrap gap-3 items-end">
        <label class="flex flex-col gap-1">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Portfolio</span>
            <select class="select select-bordered select-sm" bind:value={portfolioId} onchange={applyFilters}>
                <option value={undefined}>All portfolios</option>
                {#each portfolios as p}
                    <option value={p.id}>{p.name}</option>
                {/each}
            </select>
        </label>

        <div class="flex flex-col gap-1">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Listing</span>
            <div class="w-64">
                <AssetAutocomplete {assets} bind:value={listingId} onselect={applyFilters} clearable />
            </div>
        </div>

        <label class="flex flex-col gap-1">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Type</span>
            <select class="select select-bordered select-sm" bind:value={selectedType} onchange={applyFilters}>
                <option value="">All types</option>
                <option value="BUY">BUY</option>
                <option value="SELL">SELL</option>
                <option value="SPLIT">SPLIT</option>
            </select>
        </label>

        <label class="flex flex-col gap-1">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">From</span>
            <input type="date" class="input input-bordered input-sm" bind:value={dateFrom} onchange={applyFilters} />
        </label>

        <label class="flex flex-col gap-1">
            <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">To</span>
            <input type="date" class="input input-bordered input-sm" bind:value={dateTo} onchange={applyFilters} />
        </label>

        <button class="btn btn-ghost btn-sm" onclick={resetFilters}>Reset</button>
    </div>

    {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
    {:else if error}
        <div class="alert alert-error"><span>{error}</span></div>
    {:else}
        <p class="text-sm text-base-content/50">{totalElements} transaction{totalElements === 1 ? '' : 's'}</p>

        {#if transactions.length > 0}
            <TransactionsTable
                {transactions}
                {assets}
                portfolios={portfolioId == null ? portfolios : undefined}
            />
        {:else}
            <p class="text-base-content/40 italic text-sm">No transactions match the selected filters.</p>
        {/if}

        {#if totalPages > 1}
            <div class="flex justify-center items-center gap-2 pt-2">
                <button
                    class="btn btn-ghost btn-sm"
                    disabled={currentPage === 0}
                    onclick={() => handlePageChange(currentPage - 1)}
                >«</button>
                <span class="text-sm text-base-content/60">Page {currentPage + 1} of {totalPages}</span>
                <button
                    class="btn btn-ghost btn-sm"
                    disabled={currentPage >= totalPages - 1}
                    onclick={() => handlePageChange(currentPage + 1)}
                >»</button>
            </div>
        {/if}
    {/if}
</div>
