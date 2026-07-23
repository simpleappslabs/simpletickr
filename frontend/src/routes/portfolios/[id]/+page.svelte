<script lang="ts">
    import {onMount} from 'svelte';
    import {page} from '$app/state';
    import {goto} from '$app/navigation';
    import {getPortfolio, getHoldings, getPortfolioValuationSummary, getAccountAllocation, listAssets, listTransactions, listTransfersForPortfolio, deletePortfolio, syncPortfolioPrices, listAccounts} from '$lib/api/sdk.gen';
    import type {Account, AccountAllocation, Asset, Holding, Portfolio, PortfolioValuationSummary, Transaction, TransactionPage, Transfer} from '$lib/api/types.gen';
    import PortfolioSummary from './components/PortfolioSummary.svelte';
    import PortfolioChart from './components/PortfolioChart.svelte';
    import AccountAllocationChart from './components/AccountAllocationChart.svelte';
    import HoldingsTable from './components/HoldingsTable.svelte';
    import TransactionsContainer from './components/TransactionsContainer.svelte';
    import ValueHistoryContainer from './components/ValueHistoryContainer.svelte';
    import BrokerSelectDialog from './components/BrokerSelectDialog.svelte';
    import BoleroImportDialog from './components/BoleroImportDialog.svelte';
    import PortfolioModal from '$lib/PortfolioModal.svelte';
    import ConfirmModal from '$lib/ConfirmModal.svelte';
    import PriceHistoryModal from '$lib/asset/PriceHistoryModal.svelte';
    import '$lib/client';

    let portfolio = $state<Portfolio | null>(null);
    let holdings = $state<Holding[]>([]);
    let valuationSummary = $state<PortfolioValuationSummary | null>(null);
    let accountAllocation = $state<AccountAllocation[]>([]);
    let transactions = $state<Transaction[]>([]);
    let transfers = $state<Transfer[]>([]);
    let assets = $state<Asset[]>([]);
    let accounts = $state<Account[]>([]);
    let loading = $state(true);
    let notFound = $state(false);
    let chartListing = $state<{ id: number; ticker: string; currency: string } | null>(null);
    let error = $state<string | null>(null);

    let createTransactionOpen = $state(false);
    let cryptoTradeOpen = $state(false);
    let transferOpen = $state(false);
    let brokerSelectOpen = $state(false);
    let boleroImportOpen = $state(false);
    let syncingPrices = $state(false);
    let syncPricesError = $state<string | null>(null);
    let lastSyncAt = $state<Date | null>(null);
    let valueHistoryKey = $state(0);

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
            valueHistoryKey++;
            lastSyncAt = new Date();
        }
        syncingPrices = false;
    }

    let renameModalOpen = $state(false);
    let renamePortfolio = $state<Portfolio | null>(null);

    let deletePortfolioOpen = $state(false);
    let deletePortfolioSubmitting = $state(false);
    let deletePortfolioError = $state<string | null>(null);

    async function refreshData() {
        if (!portfolio) return;
        const [holdingsRes, summaryRes, transactionsRes, transfersRes, accountAllocationRes] = await Promise.all([
            getHoldings({path: {id: portfolio.id}}),
            getPortfolioValuationSummary({path: {id: portfolio.id}}),
            listTransactions({query: {portfolioId: portfolio.id, page: 0, size: 20}}),
            listTransfersForPortfolio({path: {portfolioId: portfolio.id}}),
            getAccountAllocation({path: {id: portfolio.id}}),
        ]);
        holdings = holdingsRes.data ?? [];
        valuationSummary = summaryRes.data ?? null;
        transactions = transactionsRes.data?.items ?? [];
        transfers = transfersRes.data ?? [];
        accountAllocation = accountAllocationRes.data ?? [];
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

        const [portfolioRes, holdingsRes, summaryRes, assetsRes, transactionsRes, transfersRes, accountAllocationRes] = await Promise.all([
            getPortfolio({path: {id}}),
            getHoldings({path: {id}}),
            getPortfolioValuationSummary({path: {id}}),
            listAssets(),
            listTransactions({query: {portfolioId: id, page: 0, size: 20}}),
            listTransfersForPortfolio({path: {portfolioId: id}}),
            getAccountAllocation({path: {id}}),
        ]);

        if (portfolioRes.error) {
            notFound = true;
            loading = false;
            return;
        }

        portfolio = portfolioRes.data ?? null;
        holdings = holdingsRes.data ?? [];
        valuationSummary = summaryRes.data ?? null;
        assets = assetsRes.data ?? [];
        transactions = transactionsRes.data?.items ?? [];
        transfers = transfersRes.data ?? [];
        accountAllocation = accountAllocationRes.data ?? [];
        loading = false;

        // Fire-and-forget: accounts are needed only when a dialog opens, not on initial render
        listAccounts().then(res => { accounts = res.data ?? []; }).catch(() => {});
    });
</script>

<div class="max-w-4xl mx-auto p-4 sm:p-6 space-y-8">
    <div class="space-y-2">
        <div class="flex items-center gap-2">
            <a href="/" class="btn btn-ghost btn-sm shrink-0">← Portfolios</a>
            {#if portfolio}
                <h1 class="text-xl sm:text-2xl font-bold flex-1 min-w-0 truncate">{portfolio.name}</h1>
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
            {/if}
        </div>

        {#if portfolio}
            <div class="flex items-center gap-2 flex-wrap">
                <button class="btn btn-primary btn-sm shrink-0" onclick={() => createTransactionOpen = true}>+ Record transaction</button>
                <button class="btn btn-ghost btn-sm shrink-0" onclick={() => cryptoTradeOpen = true}>↔ Trade</button>
                <button class="btn btn-ghost btn-sm shrink-0" onclick={() => transferOpen = true}>⇄ Transfer</button>
                <button class="btn btn-ghost btn-sm hidden sm:inline-flex" onclick={handleSyncPrices} disabled={syncingPrices}>
                    {#if syncingPrices}
                        <span class="loading loading-spinner loading-xs"></span> Syncing…
                    {:else}
                        Sync prices
                    {/if}
                </button>
                <button class="btn btn-ghost btn-sm hidden sm:inline-flex" onclick={() => brokerSelectOpen = true}>Import</button>
                <a href="/portfolios/{portfolio.id}/realized-gains" class="btn btn-ghost btn-sm hidden sm:inline-flex">Realized gains</a>
                <details class="sm:hidden dropdown dropdown-end">
                    <summary class="btn btn-ghost btn-sm list-none" aria-label="More actions">
                        <svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>
                        </svg>
                    </summary>
                    <ul class="dropdown-content menu bg-base-200 rounded-box shadow-lg z-[1] w-48 p-2 mt-1">
                        <li><button onclick={handleSyncPrices} disabled={syncingPrices}>{syncingPrices ? 'Syncing…' : 'Sync prices'}</button></li>
                        <li><button onclick={() => brokerSelectOpen = true}>Import</button></li>
                        <li><a href="/portfolios/{portfolio.id}/realized-gains">Realized gains</a></li>
                    </ul>
                </details>
            </div>
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
        <section class="space-y-3">
            <PortfolioSummary {holdings} summary={valuationSummary} {lastSyncAt} />
        </section>

        <ValueHistoryContainer portfolioId={portfolio!.id} refreshKey={valueHistoryKey} summary={valuationSummary} />

        {#if holdings.length === 0}
            <p class="text-base-content/40 italic text-sm">No holdings yet. Record a transaction to get started.</p>
        {:else}
            <section class="space-y-3">
                <h2 class="text-lg font-semibold">Holdings</h2>
                <HoldingsTable {holdings} onchartclick={(l) => chartListing = l} />
            </section>

            <section class="space-y-3">
                <h2 class="text-lg font-semibold">
                    Allocation <span class="text-sm font-normal text-base-content/40">by market value</span>
                </h2>
                <div class="grid sm:grid-cols-2 gap-4">
                    <div class="space-y-2">
                        <h3 class="text-sm font-medium text-base-content/60">By asset</h3>
                        <PortfolioChart {holdings} />
                    </div>
                    {#if accountAllocation.length > 0}
                        <div class="space-y-2">
                            <h3 class="text-sm font-medium text-base-content/60">By account</h3>
                            <AccountAllocationChart allocations={accountAllocation} />
                        </div>
                    {/if}
                </div>
            </section>
        {/if}

        <TransactionsContainer
            portfolioId={portfolio!.id}
            {assets}
            {accounts}
            {holdings}
            {transactions}
            {transfers}
            onchange={refreshData}
            bind:createOpen={createTransactionOpen}
            bind:tradeOpen={cryptoTradeOpen}
            bind:transferOpen
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

<PriceHistoryModal
    open={chartListing !== null}
    listing={chartListing}
    onclose={() => chartListing = null}
/>

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
