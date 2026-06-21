<script lang="ts">
    import type { SyncHistoryEntry } from '$lib/api/types.gen';

    let { entries }: { entries: SyncHistoryEntry[] } = $props();

    function statusClass(status: string): string {
        if (status === 'SUCCESS') return 'badge-success';
        if (status === 'FAILED') return 'badge-error';
        return 'badge-warning';
    }

    function formatDuration(ms: number): string {
        if (ms < 1000) return '< 1s';
        const s = Math.floor(ms / 1000);
        if (s < 60) return `${s}s`;
        return `${Math.floor(s / 60)}m ${s % 60}s`;
    }

    function formatDate(iso: string): string {
        return new Date(iso).toLocaleString();
    }
</script>

{#if entries.length === 0}
    <p class="text-sm text-base-content/50">No history yet.</p>
{:else}
    <div class="overflow-x-auto">
        <table class="table table-zebra table-sm w-full">
            <thead>
                <tr>
                    <th>When</th>
                    <th>Trigger</th>
                    <th>Duration</th>
                    <th class="text-right">Synced</th>
                    <th class="text-right">Failed</th>
                    <th>Status</th>
                </tr>
            </thead>
            <tbody>
                {#each entries as e}
                    <tr>
                        <td class="tabular-nums text-xs">{formatDate(e.startedAt)}</td>
                        <td>
                            <span class="badge badge-ghost badge-sm">{e.trigger}</span>
                        </td>
                        <td class="tabular-nums text-xs">{formatDuration(e.durationMs)}</td>
                        <td class="text-right tabular-nums">{e.synced}</td>
                        <td class="text-right tabular-nums">{e.failed}</td>
                        <td>
                            <span class="badge badge-sm {statusClass(e.status)}">{e.status}</span>
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
{/if}
