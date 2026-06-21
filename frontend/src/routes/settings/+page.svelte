<script lang="ts">
  import { onMount } from 'svelte';
  import { getSettings, updateSettings } from '$lib/api/sdk.gen';
  import '$lib/client';

  let baseCurrency = $state('');
  let loading = $state(true);
  let submitting = $state(false);
  let loadError = $state<string | null>(null);
  let saveError = $state<string | null>(null);
  let saved = $state(false);

  onMount(async () => {
    const { data, error } = await getSettings();
    if (error) {
      loadError = 'Failed to load settings.';
    } else {
      baseCurrency = data?.baseCurrency ?? '';
    }
    loading = false;
  });

  async function handleSubmit() {
    submitting = true;
    saveError = null;
    saved = false;
    const { error } = await updateSettings({ body: { baseCurrency } });
    if (error) {
      saveError = 'Failed to save settings. Make sure the currency code is a valid ISO 4217 code (e.g. EUR, USD, GBP).';
    } else {
      saved = true;
    }
    submitting = false;
  }
</script>

<div class="max-w-2xl mx-auto p-6 space-y-8">
  <h1 class="text-2xl font-bold">Settings</h1>

  {#if loading}
    <span class="loading loading-spinner loading-sm"></span>
  {:else if loadError}
    <div class="alert alert-error"><span>{loadError}</span></div>
  {:else}
    <form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-6">
      <section class="space-y-4">
        <h2 class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Portfolio</h2>
        <div class="flex flex-col gap-1 max-w-xs">
          <label class="text-sm font-medium" for="base-currency">Base currency</label>
          <p class="text-xs text-base-content/50">All holdings and gains are converted to this currency.</p>
          <input
            id="base-currency"
            type="text"
            class="input input-bordered w-28 font-mono uppercase"
            maxlength="3"
            placeholder="EUR"
            bind:value={baseCurrency}
            oninput={(e) => { baseCurrency = (e.currentTarget as HTMLInputElement).value.toUpperCase(); }}
            required
          />
        </div>
      </section>

      {#if saveError}
        <div class="alert alert-error text-sm"><span>{saveError}</span></div>
      {/if}
      {#if saved}
        <div class="alert alert-success text-sm"><span>Settings saved.</span></div>
      {/if}

      <button type="submit" class="btn btn-primary btn-sm" disabled={submitting}>
        {submitting ? 'Saving…' : 'Save'}
      </button>
    </form>
  {/if}
</div>
