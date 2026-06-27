<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import { onMount } from 'svelte';
	import { fly, fade } from 'svelte/transition';

	let { children } = $props();

	const THEMES = [
		{ value: 'simpletickr', label: 'Simpletickr' },
		{ value: 'tidewater', label: 'Tidewater' },
		{ value: 'coastal', label: 'Coastal' },
		{ value: 'terminal', label: 'Terminal' },
	] as const;

	let theme = $state<string>('simpletickr');
	let mobileMenuOpen = $state(false);

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
	<div class="max-w-4xl mx-auto w-full px-4 sm:px-6 flex">
		<div class="flex-1">
			<a href="/" class="text-lg font-bold">simpletickr</a>
		</div>
		<nav class="hidden sm:flex flex-none items-center gap-3">
			<ul class="menu menu-horizontal gap-1">
				<li><a href="/">Portfolios</a></li>
				<li><a href="/assets">Assets</a></li>
				<li><a href="/admin">Admin</a></li>
				<li><a href="/settings">Settings</a></li>
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
		<button
			class="sm:hidden btn btn-ghost btn-sm"
			onclick={() => mobileMenuOpen = true}
			aria-label="Open menu"
		>
			<svg xmlns="http://www.w3.org/2000/svg" class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
				<line x1="3" y1="6" x2="21" y2="6"/>
				<line x1="3" y1="12" x2="21" y2="12"/>
				<line x1="3" y1="18" x2="21" y2="18"/>
			</svg>
		</button>
	</div>
</div>

{@render children()}

{#if mobileMenuOpen}
	<div
		class="fixed inset-0 z-40 bg-black/40 sm:hidden"
		transition:fade={{ duration: 200 }}
		onclick={() => mobileMenuOpen = false}
		aria-hidden="true"
	></div>
	<nav
		class="fixed top-0 right-0 h-full w-64 bg-base-200 shadow-xl z-50 sm:hidden flex flex-col"
		transition:fly={{ x: 256, duration: 250, opacity: 1 }}
		aria-label="Mobile menu"
	>
		<div class="flex items-center justify-between px-4 h-16 border-b border-base-300">
			<span class="font-bold">Menu</span>
			<button class="btn btn-ghost btn-sm" onclick={() => mobileMenuOpen = false} aria-label="Close menu">
				<svg xmlns="http://www.w3.org/2000/svg" class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<line x1="18" y1="6" x2="6" y2="18"/>
					<line x1="6" y1="6" x2="18" y2="18"/>
				</svg>
			</button>
		</div>
		<ul class="menu menu-vertical p-4 gap-1 flex-1 text-base">
			<li><a href="/" onclick={() => mobileMenuOpen = false}>Portfolios</a></li>
			<li><a href="/assets" onclick={() => mobileMenuOpen = false}>Assets</a></li>
			<li><a href="/admin" onclick={() => mobileMenuOpen = false}>Admin</a></li>
			<li><a href="/settings" onclick={() => mobileMenuOpen = false}>Settings</a></li>
		</ul>
		<div class="p-4 border-t border-base-300">
			<p class="text-xs text-base-content/50 uppercase tracking-widest mb-2">Theme</p>
			<select class="select select-bordered select-sm w-full" bind:value={theme} aria-label="Theme">
				{#each THEMES as t}
					<option value={t.value}>{t.label}</option>
				{/each}
			</select>
		</div>
	</nav>
{/if}
