<script lang="ts">
    import {
        analyzeBoleroImport,
        importBoleroTransactions,
        createAssetImportMapping,
        deleteAssetImportMapping,
    } from '$lib/api/sdk.gen';
    import type { Asset, BoleroAnalysisResult, ImportResult } from '$lib/api/types.gen';
    import AssetAutocomplete from '$lib/AssetAutocomplete.svelte';

    let { portfolioId, assets, open, onclose, onimported }: {
        portfolioId: number;
        assets: Asset[];
        open: boolean;
        onclose: () => void;
        onimported: () => void;
    } = $props();

    type InstrumentState = {
        mappingId: number | null;
        mappedAssetId: number | null;
        pickerListingId: number;
        saving: boolean;
        saveError: string | null;
        removing: boolean;
    };

    let step = $state<1 | 2 | 3>(1);
    let file = $state<File | null>(null);
    let analysisResult = $state<BoleroAnalysisResult | null>(null);
    let importResult = $state<ImportResult | null>(null);
    let loading = $state(false);
    let error = $state<string | null>(null);
    let instrumentStates = $state<Record<string, InstrumentState>>({});

    function assetNameFor(assetId: number): string {
        return assets.find(a => a.id === assetId)?.name ?? `Asset #${assetId}`;
    }

    function assetIdForListing(listingId: number): number | null {
        for (const a of assets) {
            if (a.listings.some(l => l.id === listingId)) return a.id;
        }
        return null;
    }

    async function handleFileChange(e: Event) {
        const input = e.currentTarget as HTMLInputElement;
        const f = input.files?.[0];
        if (!f) return;
        file = f;
        loading = true;
        error = null;

        const { data, error: err } = await analyzeBoleroImport({ body: { file: f } });
        loading = false;

        if (err || !data) {
            error = 'Failed to analyze file. Make sure it is a valid Bolero XLS export.';
            file = null;
            return;
        }

        analysisResult = data;
        const states: Record<string, InstrumentState> = {};
        for (const inst of data.instruments) {
            states[inst.externalName] = {
                mappingId: inst.mapping?.id ?? null,
                mappedAssetId: inst.mapping?.assetId ?? null,
                pickerListingId: 0,
                saving: false,
                saveError: null,
                removing: false,
            };
        }
        instrumentStates = states;
        step = 2;
    }

    async function saveMapping(externalName: string) {
        const state = instrumentStates[externalName];
        if (!state || state.pickerListingId === 0) return;

        const assetId = assetIdForListing(state.pickerListingId);
        if (!assetId) return;

        state.saving = true;
        state.saveError = null;
        const { data, error: err } = await createAssetImportMapping({
            body: { broker: 'bolero', externalName, assetId },
        });
        state.saving = false;

        if (err || !data) {
            state.saveError = 'Failed to save. A mapping for this instrument may already exist.';
        } else {
            state.mappingId = data.id;
            state.mappedAssetId = data.assetId;
            state.pickerListingId = 0;
        }
        instrumentStates = { ...instrumentStates };
    }

    async function removeMapping(externalName: string) {
        const state = instrumentStates[externalName];
        if (!state?.mappingId) return;

        state.removing = true;
        await deleteAssetImportMapping({ path: { id: state.mappingId } });
        state.mappingId = null;
        state.mappedAssetId = null;
        state.removing = false;
        instrumentStates = { ...instrumentStates };
    }

    async function handleImport() {
        if (!file) return;
        loading = true;
        error = null;

        const { data, error: err } = await importBoleroTransactions({
            path: { portfolioId },
            body: { file },
        });
        loading = false;

        if (err || !data) {
            error = 'Import failed unexpectedly. Please try again.';
            return;
        }

        importResult = data;
        step = 3;
        if (data.imported > 0) onimported();
    }

    function handleClose() {
        step = 1;
        file = null;
        analysisResult = null;
        importResult = null;
        error = null;
        instrumentStates = {};
        onclose();
    }

    const unmappedCount = $derived(
        analysisResult
            ? analysisResult.instruments.filter(i => !instrumentStates[i.externalName]?.mappedAssetId).length
            : 0
    );
</script>

<dialog class="modal modal-bottom sm:modal-middle" class:modal-open={open}>
    <div class="modal-box w-11/12 max-w-3xl">
        <!-- Step indicator -->
        <div class="flex items-center gap-2 mb-6">
            <span class="text-lg font-bold">Import from Bolero</span>
            <span class="ml-auto text-xs text-base-content/50">Step {step} of 3</span>
        </div>

        {#if error}
            <div class="alert alert-error mb-4 text-sm">{error}</div>
        {/if}

        <!-- Step 1: file picker -->
        {#if step === 1}
            <p class="text-sm text-base-content/70 mb-4">
                Select the <code>.xlsx</code> cash movements export from your Bolero account.
            </p>
            <label class="form-control">
                <input
                    type="file"
                    accept=".xlsx"
                    class="file-input file-input-bordered w-full"
                    disabled={loading}
                    onchange={handleFileChange}
                />
            </label>
            {#if loading}
                <div class="flex items-center gap-2 mt-4 text-sm text-base-content/60">
                    <span class="loading loading-spinner loading-sm"></span>
                    Analyzing file…
                </div>
            {/if}
            <div class="modal-action">
                <button class="btn" onclick={handleClose}>Cancel</button>
            </div>
        {/if}

        <!-- Step 2: review mappings -->
        {#if step === 2 && analysisResult}
            <p class="text-sm text-base-content/70 mb-1">
                Found <strong>{analysisResult.totalRows}</strong> investment transactions across
                <strong>{analysisResult.instruments.length}</strong> instrument(s).
                {analysisResult.skippedRows} non-investment rows (deposits, refunds) will be skipped automatically.
            </p>
            {#if unmappedCount > 0}
                <p class="text-sm text-warning mb-4">
                    {unmappedCount} instrument(s) not yet mapped — those rows will be skipped on import.
                </p>
            {:else}
                <p class="text-sm text-success mb-4">All instruments mapped.</p>
            {/if}

            <div class="overflow-x-auto">
                <table class="table table-sm">
                    <thead>
                        <tr>
                            <th>Bolero instrument</th>
                            <th class="text-right">Rows</th>
                            <th>simpletickr asset</th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each analysisResult.instruments as inst}
                            {@const state = instrumentStates[inst.externalName]}
                            <tr>
                                <td class="font-mono text-xs max-w-xs break-words">{inst.externalName}</td>
                                <td class="text-right text-base-content/60">{inst.rowCount}</td>
                                <td>
                                    {#if state?.mappedAssetId}
                                        <div class="flex items-center gap-2">
                                            <span class="text-success text-xs">✓</span>
                                            <span class="text-sm">{assetNameFor(state.mappedAssetId)}</span>
                                            <button
                                                class="btn btn-xs btn-ghost text-error"
                                                disabled={state.removing}
                                                onclick={() => removeMapping(inst.externalName)}
                                            >
                                                {state.removing ? '…' : 'Remove'}
                                            </button>
                                        </div>
                                    {:else}
                                        <div class="flex items-center gap-2">
                                            <div class="flex-1 min-w-0">
                                                <AssetAutocomplete {assets} bind:value={state.pickerListingId} />
                                            </div>
                                            <button
                                                class="btn btn-xs btn-primary shrink-0"
                                                disabled={state.pickerListingId === 0 || state.saving}
                                                onclick={() => saveMapping(inst.externalName)}
                                            >
                                                {state.saving ? '…' : 'Save'}
                                            </button>
                                        </div>
                                        {#if state?.saveError}
                                            <p class="text-error text-xs mt-1">{state.saveError}</p>
                                        {/if}
                                    {/if}
                                </td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>

            <div class="modal-action">
                <button class="btn" onclick={handleClose}>Cancel</button>
                <button
                    class="btn btn-primary"
                    disabled={loading}
                    onclick={handleImport}
                >
                    {#if loading}
                        <span class="loading loading-spinner loading-sm"></span>
                        Importing…
                    {:else}
                        Import
                    {/if}
                </button>
            </div>
        {/if}

        <!-- Step 3: result -->
        {#if step === 3 && importResult}
            <div class="flex gap-6 mb-6">
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title">Imported</div>
                    <div class="stat-value text-success">{importResult.imported}</div>
                </div>
                <div class="stat bg-base-200 rounded-box p-4">
                    <div class="stat-title">Skipped</div>
                    <div class="stat-value text-base-content/60">{importResult.skipped}</div>
                </div>
            </div>

            {#if importResult.rows.some(r => r.status === 'SKIPPED')}
                <details>
                    <summary class="cursor-pointer text-sm text-base-content/70 mb-2">
                        Show skipped rows ({importResult.rows.filter(r => r.status === 'SKIPPED').length})
                    </summary>
                    <div class="overflow-x-auto mt-2 max-h-64">
                        <table class="table table-xs">
                            <thead>
                                <tr><th>Row</th><th>Reason</th></tr>
                            </thead>
                            <tbody>
                                {#each importResult.rows.filter(r => r.status === 'SKIPPED') as row}
                                    <tr>
                                        <td class="text-right font-mono">{row.line}</td>
                                        <td class="text-base-content/70">{row.reason}</td>
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                </details>
            {/if}

            <div class="modal-action">
                <button class="btn btn-primary" onclick={handleClose}>Done</button>
            </div>
        {/if}
    </div>

    <form method="dialog" class="modal-backdrop">
        <button onclick={handleClose}>close</button>
    </form>
</dialog>
