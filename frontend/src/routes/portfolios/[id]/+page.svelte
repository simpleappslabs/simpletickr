<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {goto} from '$app/navigation';
    import {getPortfolio, getHoldings, listAssets, listTransactions, deletePortfolio, syncPortfolioPrices} from '$lib/api/sdk.gen';
    import type {Asset, Holding, Portfolio, Transaction, TransactionPage} from '$lib/api/types.gen';
    import PortfolioSummary from './components/PortfolioSummary.svelte';
    import PortfolioChart from './components/PortfolioChart.svelte';
    import HoldingsTable from './components/HoldingsTable.svelte';
    import TransactionsContainer from './components/TransactionsContainer.svelte';
    import ValueHistoryContainer from './components/ValueHistoryContainer.svelte';
    import BrokerSelectDialog from './components/BrokerSelectDialog.svelte';
    import BoleroImportDialog from './components/BoleroImportDialog.svelte';
    import PortfolioModal from '$lib/PortfolioModal.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';
    import '$lib/client';

    let portfolio = $state<Portfolio | null>(null);
    let holdings = $state<Holding[]>([]);
    let transactions = $state<Transaction[]>([]);
    let transactionPage = $state(0);
    let transactionTotalPages = $state(0);
    let assets = $state<Asset[]>([]);
    let loading = $state(true);
    let notFound = $state(false);
    let error = $state<string | null>(null);

    let createTransactionOpen = $state(false);
    let brokerSelectOpen = $state(false);
    let boleroImportOpen = $state(false);
    let syncingPrices = $state(false);
    let syncPricesError = $state<string | null>(null);

    function handleBrokerSelect(broker: string) {
        brokerSelectOpen = false;
        if (broker === 'bolero') boleroImportOpen = true;
    }

    async function handleSyncPrices() {
        if (!portfolio) return;
        syncingPrices = true;
        syncPricesError = null;
        const { error } = await syncPortfolioPrices({ path: { id: portfolio.id } });
        if (error) {
            syncPricesError = 'Price sync failed.';
        } else {
            await refreshData();
        }
        syncingPrices = false;
    }

    let renameModalOpen = $state(false);
    let renamePortfolio = $state<Portfolio | null>(null);

    let deletePortfolioOpen = $state(false);
    let deletePortfolioSubmitting = $state(false);
    let deletePortfolioError = $state<string | null>(null);

    async function refreshData(page = transactionPage) {
        if (!portfolio) return;
        const [holdingsRes, transactionsRes] = await Promise.all([
            getHoldings({path: {id: portfolio.id}}),
            listTransactions({query: {portfolioId: portfolio.id, page, size: 25}}),
        ]);
        holdings = holdingsRes.data ?? [];
        const txPage = transactionsRes.data;
        transactions = txPage?.items ?? [];
        transactionPage = txPage?.page ?? 0;
        transactionTotalPages = txPage?.totalPages ?? 0;
    }

    async function handlePageChange(page: number) {
        await refreshData(page);
    }

    async function handleDeletePortfolio() {
        if (!portfolio) return;
        deletePortfolioSubmitting = true;
        deletePortfolioError = null;
        const {error: err} = await deletePortfolio({path: {id: portfolio.id}});
        if (err) {
            deletePortfolioError = 'Failed to delete portfolio.';
            deletePortfolioSubmitting = false;
        } else {
            goto('/');
        }
    }

    onMount(async () => {
        const id = Number(page.params.id);

        const [portfolioRes, holdingsRes, assetsRes, transactionsRes] = await Promise.all([
            getPortfolio({path: {id}}),
            getHoldings({path: {id}}),
            listAssets(),
            listTransactions({query: {portfolioId: id, page: 0, size: 25}}),
        ]);

        if (portfolioRes.error) {
            notFound = true;
            loading = false;
            return;
        }

        portfolio = portfolioRes.data ?? null;
        holdings = holdingsRes.data ?? [];
        assets = assetsRes.data ?? [];
        const txPage = transactionsRes.data;
        transactions = txPage?.items ?? [];
        transactionPage = txPage?.page ?? 0;
        transactionTotalPages = txPage?.totalPages ?? 0;
        loading = false;
    });
</script>

<div class="max-w-4xl mx-auto p-6 space-y-8">
    <div class="flex items-center gap-3">
        <a href="/" class="btn btn-ghost btn-sm">← Portfolios</a>
        {#if portfolio}
            <h1 class="text-2xl font-bold flex-1">{portfolio.name}</h1>
            <button class="btn btn-ghost btn-sm" title="Rename" onclick={() => { renamePortfolio = portfolio; renameModalOpen = true; }}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
            </button>
            <button class="btn btn-ghost btn-sm text-error" title="Delete portfolio" onclick={() => { deletePortfolioError = null; deletePortfolioOpen = true; }}>
                <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                    <path d="M10 11v6M14 11v6"/>
                    <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                </svg>
            </button>
            <a href="/portfolios/{portfolio.id}/realized-gains" class="btn btn-ghost btn-sm">Realized gains</a>
            <button class="btn btn-outline btn-sm" onclick={() => brokerSelectOpen = true}>Import</button>
            <button class="btn btn-outline btn-sm" onclick={handleSyncPrices} disabled={syncingPrices}>
                {#if syncingPrices}
                    <span class="loading loading-spinner loading-xs"></span> Syncing…
                {:else}
                    Sync prices
                {/if}
            </button>
            <button class="btn btn-primary btn-sm" onclick={() => createTransactionOpen = true}>+ Record transaction</button>
        {/if}
    </div>

    {#if syncPricesError}
        <div class="alert alert-error py-2"><span>{syncPricesError}</span></div>
    {/if}

    {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
    {:else if notFound}
        <div class="alert alert-error"><span>Portfolio not found.</span></div>
    {:else if error}
        <div class="alert alert-error"><span>{error}</span></div>
    {:else}
        <PortfolioSummary {holdings} />

        <ValueHistoryContainer portfolioId={portfolio!.id} />

        {#if holdings.length === 0}
            <p class="text-base-content/40 italic text-sm">No holdings yet. Record a transaction to get started.</p>
        {:else}
            <div class="flex flex-col lg:flex-row gap-6 items-start">
                <PortfolioChart {holdings} />
                <HoldingsTable {holdings} />
            </div>
        {/if}

        <TransactionsContainer
            portfolioId={portfolio!.id}
            {assets}
            {transactions}
            currentPage={transactionPage}
            totalPages={transactionTotalPages}
            onchange={refreshData}
            onpagechange={handlePageChange}
            bind:createOpen={createTransactionOpen}
        />
    {/if}
</div>

{#if portfolio}
    <BrokerSelectDialog
        open={brokerSelectOpen}
        onclose={() => brokerSelectOpen = false}
        onselect={handleBrokerSelect}
    />

    <BoleroImportDialog
        portfolioId={portfolio.id}
        {assets}
        open={boleroImportOpen}
        onclose={() => boleroImportOpen = false}
        onimported={refreshData}
    />
{/if}

<PortfolioModal
    open={renameModalOpen}
    portfolio={renamePortfolio}
    onSuccess={(updated) => { portfolio = updated; renamePortfolio = null; renameModalOpen = false; }}
    onCancel={() => { renamePortfolio = null; renameModalOpen = false; }}
/>

<ConfirmModal
    open={deletePortfolioOpen}
    title="Delete portfolio"
    submitting={deletePortfolioSubmitting}
    error={deletePortfolioError}
    onconfirm={handleDeletePortfolio}
    oncancel={() => deletePortfolioOpen = false}
>
    Are you sure you want to delete <strong>{portfolio?.name}</strong>?
    All transactions will be permanently removed.
</ConfirmModal>
