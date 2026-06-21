<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {goto} from '$app/navigation';
    import {getPortfolio, getHoldings, listAssets, listTransactions, deletePortfolio} from '$lib/api/sdk.gen';
    import type {Asset, Holding, Portfolio, Transaction} from '$lib/api/types.gen';
    import PortfolioSummary from './components/PortfolioSummary.svelte';
    import PortfolioChart from './components/PortfolioChart.svelte';
    import HoldingsTable from './components/HoldingsTable.svelte';
    import TransactionsContainer from './components/TransactionsContainer.svelte';
    import PortfolioModal from '$lib/PortfolioModal.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';
    import '$lib/client';

    let portfolio = $state<Portfolio | null>(null);
    let holdings = $state<Holding[]>([]);
    let transactions = $state<Transaction[]>([]);
    let assets = $state<Asset[]>([]);
    let loading = $state(true);
    let notFound = $state(false);
    let error = $state<string | null>(null);

    let createTransactionOpen = $state(false);

    let renameModalOpen = $state(false);
    let renamePortfolio = $state<Portfolio | null>(null);

    let deletePortfolioOpen = $state(false);
    let deletePortfolioSubmitting = $state(false);
    let deletePortfolioError = $state<string | null>(null);

    async function refreshData() {
        if (!portfolio) return;
        const [holdingsRes, transactionsRes] = await Promise.all([
            getHoldings({path: {id: portfolio.id}}),
            listTransactions({query: {portfolioId: portfolio.id}}),
        ]);
        holdings = holdingsRes.data ?? [];
        transactions = transactionsRes.data ?? [];
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
            listTransactions({query: {portfolioId: id}}),
        ]);

        if (portfolioRes.error) {
            notFound = true;
            loading = false;
            return;
        }

        portfolio = portfolioRes.data ?? null;
        holdings = holdingsRes.data ?? [];
        assets = assetsRes.data ?? [];
        transactions = transactionsRes.data ?? [];
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
            <button class="btn btn-primary btn-sm" onclick={() => createTransactionOpen = true}>+ Record transaction</button>
        {/if}
    </div>

    {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
    {:else if notFound}
        <div class="alert alert-error"><span>Portfolio not found.</span></div>
    {:else if error}
        <div class="alert alert-error"><span>{error}</span></div>
    {:else}
        <PortfolioSummary {holdings} />

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
            onchange={refreshData}
            bind:createOpen={createTransactionOpen}
        />
    {/if}
</div>

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
