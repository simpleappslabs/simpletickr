<script lang="ts">
    import { importData } from '$lib/api/sdk.gen';
    import type { DataImportAnalysis, DataImportResult } from '$lib/api/types.gen';

    let { open, onsuccess, oncancel }: {
        open: boolean;
        onsuccess: () => void;
        oncancel: () => void;
    } = $props();

    let step = $state<1 | 2 | 3>(1);
    let file = $state<File | null>(null);
    let analysis = $state<DataImportAnalysis | null>(null);
    let result = $state<DataImportResult | null>(null);
    let loading = $state(false);
    let error = $state<string | null>(null);

    async function handleAnalyze() {
        if (!file) return;
        loading = true;
        error = null;

        const { data, error: err } = await importData({ body: { file }, query: { dryRun: true } });
        loading = false;

        if (err || !data) {
            error = 'Failed to analyze file. Make sure it is a valid simpletickr export.';
            return;
        }

        analysis = data as DataImportAnalysis;
        step = 2;
    }

    async function handleApply() {
        if (!file) return;
        loading = true;
        error = null;

        const { data, error: err } = await importData({ body: { file }, query: { dryRun: false } });
        loading = false;

        if (err || !data) {
            error = 'Import failed. Please try again.';
            return;
        }

        result = data as DataImportResult;
        step = 3;
    }

    function handleClose() {
        step = 1;
        file = null;
        analysis = null;
        result = null;
        error = null;
        oncancel();
    }

    function handleDone() {
        step = 1;
        file = null;
        analysis = null;
        result = null;
        error = null;
        onsuccess();
    }

    function handleFileChange(e: Event) {
        const input = e.currentTarget as HTMLInputElement;
        file = input.files?.[0] ?? null;
    }

    const hasErrors = $derived(analysis !== null && analysis.errors.length > 0);
    const totalCreating = $derived(
        analysis
            ? analysis.assetsToCreate + analysis.listingsToCreate + analysis.portfoliosToCreate + analysis.transactionsToImport
            : 0
    );
</script>

<svelte:window onkeydown={(e) => { if (open && e.key === 'Escape') handleClose(); }} />

<dialog class="modal modal-middle" class:modal-open={open}>
    <div class="modal-box w-11/12 max-w-lg">
        <div class="flex items-center gap-2 mb-6">
            <span class="text-lg font-bold">Import data</span>
            <span class="ml-auto text-xs text-base-content/50">Step {step} of 3</span>
        </div>

        {#if error}
            <div class="alert alert-error mb-4 text-sm">{error}</div>
        {/if}

        <!-- Step 1: file upload -->
        {#if step === 1}
            <p class="text-sm text-base-content/70 mb-4">
                Select a <code>.json</code> export file previously downloaded from simpletickr.
                Existing data will not be overwritten — import is additive.
            </p>
            <label class="form-control mb-4">
                <input
                    type="file"
                    accept=".json"
                    class="file-input file-input-bordered w-full"
                    disabled={loading}
                    onchange={handleFileChange}
                />
            </label>
            <div class="modal-action">
                <button class="btn" onclick={handleClose}>Cancel</button>
                <button
                    class="btn btn-primary"
                    disabled={!file || loading}
                    onclick={handleAnalyze}
                >
                    {#if loading}
                        <span class="loading loading-spinner loading-sm"></span>
                        Analyzing…
                    {:else}
                        Analyze
                    {/if}
                </button>
            </div>
        {/if}

        <!-- Step 2: preview -->
        {#if step === 2 && analysis}
            {#if hasErrors}
                <div class="alert alert-error mb-4 text-sm">
                    <ul class="list-disc list-inside space-y-1">
                        {#each analysis.errors as e}
                            <li>{e}</li>
                        {/each}
                    </ul>
                </div>
                <p class="text-sm text-base-content/70 mb-4">
                    Fix the errors above before importing.
                </p>
                <div class="modal-action">
                    <button class="btn" onclick={handleClose}>Cancel</button>
                    <button class="btn" onclick={() => { step = 1; error = null; }}>Back</button>
                </div>
            {:else}
                <p class="text-sm text-base-content/70 mb-4">
                    Review what will be imported. Existing assets and portfolios matched by UUID will be reused — only new records are created.
                </p>
                <div class="overflow-x-auto mb-4">
                    <table class="table table-sm">
                        <thead>
                            <tr>
                                <th>Entity</th>
                                <th class="text-right">To create</th>
                                <th class="text-right">Already exist</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>Assets</td>
                                <td class="text-right">{analysis.assetsToCreate}</td>
                                <td class="text-right text-base-content/50">{analysis.assetsExisting}</td>
                            </tr>
                            <tr>
                                <td>Listings</td>
                                <td class="text-right">{analysis.listingsToCreate}</td>
                                <td class="text-right text-base-content/50">{analysis.listingsExisting}</td>
                            </tr>
                            <tr>
                                <td>Portfolios</td>
                                <td class="text-right">{analysis.portfoliosToCreate}</td>
                                <td class="text-right text-base-content/50">{analysis.portfoliosExisting}</td>
                            </tr>
                            <tr>
                                <td>Transactions</td>
                                <td class="text-right">{analysis.transactionsToImport}</td>
                                <td class="text-right text-base-content/50">{analysis.transactionsSkipped} skipped (duplicates)</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                {#if totalCreating === 0}
                    <div class="alert alert-info mb-4 text-sm">Nothing to import — all records already exist.</div>
                {/if}
                <div class="modal-action">
                    <button class="btn" onclick={handleClose}>Cancel</button>
                    <button class="btn" onclick={() => { step = 1; error = null; }}>Back</button>
                    <button
                        class="btn btn-primary"
                        disabled={loading || totalCreating === 0}
                        onclick={handleApply}
                    >
                        {#if loading}
                            <span class="loading loading-spinner loading-sm"></span>
                            Importing…
                        {:else}
                            Apply import
                        {/if}
                    </button>
                </div>
            {/if}
        {/if}

        <!-- Step 3: result -->
        {#if step === 3 && result}
            <div class="grid grid-cols-2 gap-3 mb-6">
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title text-xs">Assets created</div>
                    <div class="stat-value text-2xl">{result.assetsCreated}</div>
                </div>
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title text-xs">Listings created</div>
                    <div class="stat-value text-2xl">{result.listingsCreated}</div>
                </div>
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title text-xs">Portfolios created</div>
                    <div class="stat-value text-2xl">{result.portfoliosCreated}</div>
                </div>
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title text-xs">Transactions imported</div>
                    <div class="stat-value text-2xl text-success">{result.transactionsImported}</div>
                </div>
            </div>
            <div class="modal-action">
                <button class="btn btn-primary" onclick={handleDone}>Done</button>
            </div>
        {/if}
    </div>

    <form method="dialog" class="modal-backdrop">
        <button onclick={handleClose}>close</button>
    </form>
</dialog>
