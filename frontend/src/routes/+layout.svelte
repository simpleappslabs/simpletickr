<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import { onMount } from 'svelte';

	let { children } = $props();

	const THEMES = [
		{ value: 'simpletickr', label: 'Simpletickr' },
		{ value: 'tidewater', label: 'Tidewater' },
		{ value: 'coastal', label: 'Coastal' },
		{ value: 'terminal', label: 'Terminal' },
	] as const;

	let theme = $state<string>('simpletickr');

	onMount(() => {
		const stored = localStorage.getItem('theme');
		if (stored && THEMES.some(t => t.value === stored)) {
			theme = stored;
		}
		document.documentElement.setAttribute('data-theme', theme);
	});

	$effect(() => {
		document.documentElement.setAttribute('data-theme', theme);
		localStorage.setItem('theme', theme);
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<div class="navbar bg-base-200">
	<div class="max-w-4xl mx-auto w-full px-6 flex">
		<div class="flex-1">
			<a href="/" class="text-lg font-bold">simpletickr</a>
		</div>
		<nav class="flex-none flex items-center gap-3">
			<ul class="menu menu-horizontal gap-1">
				<li><a href="/">Portfolios</a></li>
				<li><a href="/assets">Assets</a></li>
				<li><a href="/admin">Admin</a></li>
				<li>
					<select
							class="select select-ghost select-sm text-sm"
							bind:value={theme}
							aria-label="Theme"
					>
						{#each THEMES as t}
							<option value={t.value}>{t.label}</option>
						{/each}
					</select>
				</li>
			</ul>
		</nav>
	</div>
</div>

{@render children()}
