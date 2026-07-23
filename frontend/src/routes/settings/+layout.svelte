<script lang="ts">
	import { page } from '$app/stores';

	let { children } = $props();

	const tabs = [
		{ label: 'General', href: '/settings' },
		{ label: 'Accounts', href: '/settings/accounts' },
		{ label: 'Assets', href: '/settings/assets' },
		{ label: 'Sync', href: '/settings/sync' },
	];

	function isActive(href: string) {
		return href === '/settings'
			? $page.url.pathname === '/settings'
			: $page.url.pathname.startsWith(href);
	}
</script>

<div class="max-w-5xl mx-auto px-4 sm:px-6 pt-6">
	<div role="tablist" class="tabs tabs-border">
		{#each tabs as tab}
			<a
				role="tab"
				href={tab.href}
				class="tab"
				class:tab-active={isActive(tab.href)}
			>{tab.label}</a>
		{/each}
	</div>
</div>

{@render children()}
