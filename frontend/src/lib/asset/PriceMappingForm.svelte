<script lang="ts">
    export interface Mapping { provider: string; externalId: string; }

    interface Props { mappings: Mapping[]; }
    const { mappings }: Props = $props();

    function add() { mappings.push({ provider: '', externalId: '' }); }
    function remove(i: number) { mappings.splice(i, 1); }
</script>

<div class="space-y-1.5">
    <div class="text-xs font-semibold uppercase tracking-widest text-base-content/50">Provider mappings</div>
    {#each mappings as m, i}
        <div class="grid grid-cols-[1fr_1fr_auto] gap-2 items-center">
            <input class="input input-sm" type="text" placeholder="Provider" bind:value={m.provider} />
            <input class="input input-sm" type="text" placeholder="External ID" bind:value={m.externalId} />
            <button type="button" class="btn btn-ghost btn-xs text-error" onclick={() => remove(i)}>×</button>
        </div>
    {/each}
    <button type="button" class="btn btn-ghost btn-xs" onclick={add}>+ Add mapping</button>
</div>
