<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import { onMount } from 'svelte';
	import { fly, fade } from 'svelte/transition';
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { getCurrentUser, logout } from '$lib/api/sdk.gen';
	import { authState } from '$lib/authState.svelte';
	import '$lib/client';

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

	onMount(async () => {
		const { data, error } = await getCurrentUser();
		authState.username = error ? null : (data?.username ?? null);
		authState.checked = true;
	});

	async function handleLogout() {
		await logout();
		authState.username = null;
		await goto('/login');
	}

	// Reacts to every navigation, not just the initial load — otherwise clicking into a
	// protected route while logged out (or a stale session expiring mid-session) would leave
	// the page blank instead of bouncing back to /login.
	$effect(() => {
		if (authState.checked && !authState.username && $page.url.pathname !== '/login') {
			goto('/login');
		}
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
	<div class="max-w-5xl mx-auto w-full px-4 sm:px-6 flex items-center">
		<div class="flex-1">
			<a href="/" class="flex items-center gap-2 text-lg font-bold">
				<svg xmlns="http://www.w3.org/2000/svg" class="size-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<path d="M2 18.5L6.5 13L11 15.5L16.5 9L22 6.5"/>
					<circle cx="22" cy="6.5" r="2.25" fill="currentColor" stroke="none"/>
				</svg>
				simpletickr
			</a>
		</div>
		<nav class="hidden sm:flex flex-none items-center gap-2">
			{#if authState.username}
				<ul class="menu menu-horizontal flex-nowrap gap-1">
					<li><a href="/" class={$page.url.pathname === '/' ? 'bg-primary text-primary-content font-medium' : ''}>Portfolios</a></li>
					<li><a href="/transactions" class={$page.url.pathname.startsWith('/transactions') ? 'bg-primary text-primary-content font-medium' : ''}>Transactions</a></li>
					<li><a href="/dashboard" class={$page.url.pathname.startsWith('/dashboard') ? 'bg-primary text-primary-content font-medium' : ''}>Dashboard</a></li>
					<li><a href="/settings" class={$page.url.pathname.startsWith('/settings') ? 'bg-primary text-primary-content font-medium' : ''}>Settings</a></li>
				</ul>
			{/if}
			<div class="dropdown dropdown-end">
				<button tabindex="0" class="btn btn-ghost btn-sm btn-square" aria-label="Theme">
					<svg xmlns="http://www.w3.org/2000/svg" class="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<circle cx="13.5" cy="6.5" r=".5" fill="currentColor"/>
						<circle cx="17.5" cy="10.5" r=".5" fill="currentColor"/>
						<circle cx="8.5" cy="7.5" r=".5" fill="currentColor"/>
						<circle cx="6.5" cy="12.5" r=".5" fill="currentColor"/>
						<path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"/>
					</svg>
				</button>
				<ul tabindex="0" class="dropdown-content menu bg-base-200 rounded-box shadow z-10 p-2 w-40">
					{#each THEMES as t}
						<li>
							<button class={theme === t.value ? 'active' : ''} onclick={() => { theme = t.value; (document.activeElement as HTMLElement)?.blur(); }}>
								{t.label}
							</button>
						</li>
					{/each}
				</ul>
			</div>
			{#if authState.username}
				<div class="dropdown dropdown-end">
					<button tabindex="0" class="btn btn-ghost btn-sm">
						{authState.username}
					</button>
					<ul tabindex="0" class="dropdown-content menu bg-base-200 rounded-box shadow z-10 p-2 w-48">
						<li><a href="/settings/change-password">Change password</a></li>
						<li><button onclick={handleLogout}>Log out</button></li>
					</ul>
				</div>
			{/if}
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

{#if authState.checked && (authState.username || $page.url.pathname === '/login')}
	{@render children()}
{/if}

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
		{#if authState.username}
			<ul class="menu menu-vertical p-4 gap-1 flex-1 text-base">
				<li><a href="/" class={$page.url.pathname === '/' ? 'bg-primary text-primary-content font-medium' : ''} onclick={() => mobileMenuOpen = false}>Portfolios</a></li>
				<li><a href="/transactions" class={$page.url.pathname.startsWith('/transactions') ? 'bg-primary text-primary-content font-medium' : ''} onclick={() => mobileMenuOpen = false}>Transactions</a></li>
				<li><a href="/dashboard" class={$page.url.pathname.startsWith('/dashboard') ? 'bg-primary text-primary-content font-medium' : ''} onclick={() => mobileMenuOpen = false}>Dashboard</a></li>
				<li><a href="/settings" class={$page.url.pathname.startsWith('/settings') ? 'bg-primary text-primary-content font-medium' : ''} onclick={() => mobileMenuOpen = false}>Settings</a></li>
			</ul>
		{/if}
		<div class="p-4 border-t border-base-300">
			<p class="text-xs text-base-content/50 uppercase tracking-widest mb-2">Theme</p>
			<select class="select select-bordered select-sm w-full" bind:value={theme} aria-label="Theme">
				{#each THEMES as t}
					<option value={t.value}>{t.label}</option>
				{/each}
			</select>
		</div>
		{#if authState.username}
			<div class="p-4 border-t border-base-300 space-y-2">
				<p class="text-xs text-base-content/50 uppercase tracking-widest">{authState.username}</p>
				<a href="/settings/change-password" class="btn btn-ghost btn-sm w-full justify-start" onclick={() => mobileMenuOpen = false}>Change password</a>
				<button class="btn btn-ghost btn-sm w-full justify-start" onclick={() => { mobileMenuOpen = false; handleLogout(); }}>Log out</button>
			</div>
		{/if}
	</nav>
{/if}
