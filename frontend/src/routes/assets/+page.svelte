<script lang="ts">
  import { onMount } from 'svelte';
  import { listAssets, deleteAsset } from '$lib/api/sdk.gen';
  import type { Asset } from '$lib/api/types.gen';
  import AssetModal from '$lib/AssetModal.svelte';
  import '$lib/client';

  let assets = $state<Asset[]>([]);
  let loading = $state(true);
  let error = $state<string | null>(null);

  let modalOpen = $state(false);
  let editingAsset = $state<Asset | null>(null);
  let deletingAsset = $state<Asset | null>(null);
  let deleteSubmitting = $state(false);
  let deleteError = $state<string | null>(null);

  async function load() {
    loading = true;
    error = null;
    const { data, error: err } = await listAssets();
    if (err) {
      error = 'Failed to load assets.';
    } else {
      assets = data ?? [];
    }
    loading = false;
  }

  async function handleDelete() {
    if (!deletingAsset) return;
    deleteSubmitting = true;
    deleteError = null;
    const { error: err } = await deleteAsset({ path: { id: deletingAsset.id } });
    if (err) {
      deleteError = 'Failed to delete asset.';
      deleteSubmitting = false;
      return;
    }
    assets = assets.filter((a) => a.id !== deletingAsset!.id);
    deletingAsset = null;
    deleteSubmitting = false;
  }

  onMount(load);
</script>

<div class="max-w-4xl mx-auto p-6 space-y-8">
  <div class="flex items-center gap-3">
    <h1 class="text-2xl font-bold flex-1">Assets</h1>
    <button class="btn btn-primary btn-sm" onclick={() => { editingAsset = null; modalOpen = true; }}>+ Add asset</button>
  </div>

  <section class="space-y-3">
    <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Catalog</h2>

    {#if loading}
      <span class="loading loading-spinner loading-sm"></span>
    {:else if error}
      <div class="alert alert-error"><span>{error}</span></div>
    {:else if assets.length === 0}
      <p class="text-base-content/40 italic text-sm">No assets yet.</p>
    {:else}
      <div class="overflow-x-auto">
        <table class="table table-zebra w-full">
          <thead>
            <tr>
              <th>Ticker</th>
              <th>Name</th>
              <th>Type</th>
              <th>Currency</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {#each assets as asset}
              <tr>
                <td class="font-mono font-semibold">{asset.listings[0]?.ticker ?? '—'}</td>
                <td>{asset.name}</td>
                <td><span class="badge badge-ghost">{asset.type}</span></td>
                <td>{asset.listings[0]?.currency ?? '—'}</td>
                <td class="text-right">
                  <button
                    class="btn btn-ghost btn-xs"
                    title="Edit"
                    onclick={() => { editingAsset = asset; modalOpen = true; }}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button
                    class="btn btn-ghost btn-xs text-error"
                    title="Delete"
                    onclick={() => { deleteError = null; deletingAsset = asset; }}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                      <path d="M10 11v6M14 11v6"/>
                      <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                    </svg>
                  </button>
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </section>
</div>

<AssetModal
  open={modalOpen}
  asset={editingAsset}
  onSuccess={(saved) => {
    assets = editingAsset
      ? assets.map((a) => a.id === saved.id ? saved : a)
      : [...assets, saved];
    modalOpen = false;
    editingAsset = null;
  }}
  onCancel={() => { modalOpen = false; editingAsset = null; }}
/>

<!-- Delete confirmation modal -->
<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={deletingAsset !== null}>
  <div class="modal-box">
    <h3 class="text-lg font-bold mb-2">Delete asset</h3>
    <p class="text-base-content/70 mb-6">
      Are you sure you want to delete <strong>{deletingAsset?.name}</strong>?
    </p>
    {#if deleteError}
      <div class="alert alert-error mb-4"><span>{deleteError}</span></div>
    {/if}
    <div class="modal-action">
      <button class="btn btn-ghost" disabled={deleteSubmitting} onclick={() => deletingAsset = null}>
        Cancel
      </button>
      <button class="btn btn-error" disabled={deleteSubmitting} onclick={handleDelete}>
        {deleteSubmitting ? 'Deleting…' : 'Delete'}
      </button>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop">
    <button onclick={() => deletingAsset = null}>close</button>
  </form>
</dialog>
