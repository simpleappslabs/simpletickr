<script lang="ts">
    import type { Account, AccountRequest, AccountType } from '$lib/api/types.gen';

    const ACCOUNT_TYPES: { value: AccountType; label: string }[] = [
        { value: 'BROKERAGE', label: 'Brokerage' },
        { value: 'CRYPTO', label: 'Crypto Exchange' },
        { value: 'BANK', label: 'Bank' },
        { value: 'RETIREMENT', label: 'Retirement' },
        { value: 'OTHER', label: 'Other' },
    ];

    let { account = null, submitting = false, error = null, onsubmit, oncancel }: {
        account?: Account | null;
        submitting?: boolean;
        error?: string | null;
        onsubmit: (body: AccountRequest) => void;
        oncancel: () => void;
    } = $props();

    let formName = $state(account?.name ?? '');
    let formBroker = $state(account?.broker ?? '');
    let formAccountType = $state<AccountType>(account?.accountType ?? 'BROKERAGE');
    let formCurrency = $state(account?.currency ?? '');
    let formAccountNumber = $state(account?.accountNumber ?? '');
    let formInstitution = $state(account?.institution ?? '');

    function handleSubmit(e: SubmitEvent) {
        e.preventDefault();
        onsubmit({
            name: formName,
            broker: formBroker || undefined,
            accountType: formAccountType,
            currency: formCurrency || undefined,
            accountNumber: formAccountNumber || undefined,
            institution: formInstitution || undefined,
        });
    }
</script>

<form class="space-y-4" onsubmit={handleSubmit}>
    <fieldset class="fieldset">
        <legend class="fieldset-legend">Name</legend>
        <input class="input w-full" type="text" required bind:value={formName} placeholder="e.g. Fidelity Brokerage" />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Type</legend>
        <select class="select w-full" bind:value={formAccountType}>
            {#each ACCOUNT_TYPES as t}
                <option value={t.value}>{t.label}</option>
            {/each}
        </select>
    </fieldset>

    <div class="grid grid-cols-2 gap-4">
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Broker <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input w-full" type="text" bind:value={formBroker} placeholder="e.g. Fidelity" />
        </fieldset>
        <fieldset class="fieldset">
            <legend class="fieldset-legend">Currency <span class="text-base-content/40 font-normal">(optional)</span></legend>
            <input class="input w-full" type="text" maxlength="3" bind:value={formCurrency} placeholder="USD" />
        </fieldset>
    </div>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Account number <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <input class="input w-full" type="text" bind:value={formAccountNumber} placeholder="e.g. ****1234" />
    </fieldset>

    <fieldset class="fieldset">
        <legend class="fieldset-legend">Institution <span class="text-base-content/40 font-normal">(optional)</span></legend>
        <input class="input w-full" type="text" bind:value={formInstitution} placeholder="e.g. Fidelity Investments" />
    </fieldset>

    {#if error}
        <div class="alert alert-error text-sm"><span>{error}</span></div>
    {/if}

    <div class="modal-action mt-6">
        <button type="button" class="btn btn-ghost" onclick={oncancel}>Cancel</button>
        <button type="submit" class="btn btn-primary" disabled={submitting || !formName}>
            {#if submitting}
                <span class="loading loading-spinner loading-sm"></span>
            {:else}
                {account ? 'Update' : 'Create'}
            {/if}
        </button>
    </div>
</form>
