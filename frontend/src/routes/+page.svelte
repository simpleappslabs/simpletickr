<script lang="ts">
  import { onMount } from 'svelte';
  import { listPortfolios, deletePortfolio } from '$lib/api/sdk.gen';
  import type { Portfolio } from '$lib/api/types.gen';
  import PortfolioModal from '$lib/PortfolioModal.svelte';
  import '$lib/client';

  let portfolios = $state<Portfolio[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);

  let modalOpen = $state(false);
  let editingPortfolio = $state<Portfolio | null>(null);

  let deletingPortfolio = $state<Portfolio | null>(null);
  let deleteSubmitting = $state(false);
  let deleteError = $state<string | null>(null);

  async function load() {
    loading = true;
    error = null;
    const { data, error: err } = await listPortfolios();
    if (err) {
      error = 'Failed to load portfolios.';
    } else {
      portfolios = data ?? [];
    }
    loading = false;
  }

  async function handleDelete() {
    if (!deletingPortfolio) return;
    deleteSubmitting = true;
    deleteError = null;
    const { error: err } = await deletePortfolio({ path: { id: deletingPortfolio.id } });
    if (err) {
      deleteError = 'Failed to delete portfolio.';
    } else {
      portfolios = portfolios.filter((p) => p.id !== deletingPortfolio!.id);
      deletingPortfolio = null;
    }
    deleteSubmitting = false;
  }

  onMount(load);
</script>

<div class="max-w-2xl mx-auto p-6 space-y-8">
  <div class="flex items-center gap-3">
    <h1 class="text-2xl font-bold flex-1">Portfolios</h1>
    <button class="btn btn-primary btn-sm" onclick={() => { editingPortfolio = null; modalOpen = true; }}>+ New portfolio</button>
  </div>

  {#if loading}
    <span class="loading loading-spinner loading-sm"></span>
  {:else if error}
    <div class="alert alert-error"><span>{error}</span></div>
  {:else if portfolios.length === 0}
    <p class="text-base-content/40 italic text-sm">No portfolios yet.</p>
  {:else}
    <ul class="menu bg-base-200 rounded-box w-full">
      {#each portfolios as portfolio}
        <li>
          <div class="flex items-center gap-1 pr-1">
            <a href="/portfolios/{portfolio.id}" class="flex-1">{portfolio.name}</a>
            <button
              class="btn btn-ghost btn-xs"
              title="Rename"
              onclick={() => { editingPortfolio = portfolio; modalOpen = true; }}
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </button>
            <button
              class="btn btn-ghost btn-xs text-error"
              title="Delete"
              onclick={() => { deleteError = null; deletingPortfolio = portfolio; }}
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                <path d="M10 11v6M14 11v6"/>
                <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
              </svg>
            </button>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<PortfolioModal
  open={modalOpen}
  portfolio={editingPortfolio}
  onSuccess={(saved) => {
    portfolios = editingPortfolio
      ? portfolios.map((p) => p.id === saved.id ? saved : p)
      : [...portfolios, saved];
    modalOpen = false;
    editingPortfolio = null;
  }}
  onCancel={() => { modalOpen = false; editingPortfolio = null; }}
/>

<!-- Delete confirmation modal -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={deletingPortfolio !== null}>
  <div class="modal-box">
    <h3 class="text-lg font-bold mb-2">Delete portfolio</h3>
    <p class="text-base-content/70 mb-6">
      Are you sure you want to delete <strong>{deletingPortfolio?.name}</strong>?
      All transactions will be permanently removed.
    </p>
    {#if deleteError}
      <div class="alert alert-error mb-4"><span>{deleteError}</span></div>
    {/if}
    <div class="modal-action">
      <button class="btn btn-ghost" disabled={deleteSubmitting} onclick={() => deletingPortfolio = null}>
        Cancel
      </button>
      <button class="btn btn-error" disabled={deleteSubmitting} onclick={handleDelete}>
        {deleteSubmitting ? 'Deleting…' : 'Delete'}
      </button>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop">
    <button onclick={() => deletingPortfolio = null}>close</button>
  </form>
</dialog>
