<script lang="ts">
	import { privacyState } from '$lib/privacyState.svelte';

	let {
		value,
		currency,
		suffix = '',
		decimals = 2,
		signed = false,
		colorize = false,
		fallback = '—',
	}: {
		value: number | null | undefined;
		currency?: string;
		suffix?: string;
		decimals?: number;
		signed?: boolean;
		colorize?: boolean;
		fallback?: string;
	} = $props();

	const formatted = $derived(
		value == null
			? null
			: value.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }),
	);

	const colorClass = $derived(
		colorize && value != null && !privacyState.masked
			? value >= 0 ? 'text-success' : 'text-error'
			: '',
	);
</script>

<span class={colorClass}>
	{#if value == null}
		{fallback}
	{:else if privacyState.masked}
		••••••{suffix}{currency ? ` ${currency}` : ''}
	{:else}
		{signed && value >= 0 ? '+' : ''}{formatted}{suffix}{currency ? ` ${currency}` : ''}
	{/if}
</span>
