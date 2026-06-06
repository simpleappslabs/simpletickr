<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<div class="navbar bg-base-200">
	<div class="max-w-4xl mx-auto w-full px-6 flex">
		<div class="flex-1">
			<a href="/" class="text-lg font-bold">simpletickr</a>
		</div>
		<nav class="flex-none">
			<ul class="menu menu-horizontal gap-1">
				<li><a href="/">Portfolios</a></li>
				<li><a href="/assets">Assets</a></li>
			</ul>
		</nav>
	</div>
</div>

{@render children()}
