<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { getAuthConfig, login } from '$lib/api/sdk.gen';
	import { authState } from '$lib/authState.svelte';
	import { apiBaseUrl } from '$lib/client';
	import '$lib/client';

	let username = $state('');
	let password = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);
	let oidcEnabled = $state(false);

	onMount(async () => {
		if ($page.url.searchParams.get('oidcError')) {
			error = 'Single sign-on failed. Try again, or log in with a local account below.';
		}
		const { data } = await getAuthConfig();
		oidcEnabled = data?.oidcEnabled ?? false;
	});

	async function handleSubmit() {
		submitting = true;
		error = null;
		const { data, error: loginError } = await login({ body: { username, password } });
		if (loginError) {
			error = 'Invalid username or password.';
			submitting = false;
			return;
		}
		authState.username = data?.username ?? null;
		authState.checked = true;
		await goto('/');
	}
</script>

<div class="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4">
	<div class="card w-full max-w-sm bg-base-200 shadow-xl">
		<div class="card-body">
			<h1 class="text-xl font-bold text-center mb-2">Log in</h1>

			{#if error}
				<div class="alert alert-error text-sm"><span>{error}</span></div>
			{/if}

			{#if oidcEnabled}
				<a href="{apiBaseUrl}/oauth2/authorization/oidc" class="btn btn-primary w-full">
					Continue with SSO
				</a>
				<div class="divider text-xs">or log in locally</div>
			{/if}

			<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-4">
				<div class="flex flex-col gap-1">
					<label class="text-sm font-medium" for="username">Username</label>
					<input
						id="username"
						type="text"
						class="input input-bordered w-full"
						autocomplete="username"
						bind:value={username}
						required
					/>
				</div>
				<div class="flex flex-col gap-1">
					<label class="text-sm font-medium" for="password">Password</label>
					<input
						id="password"
						type="password"
						class="input input-bordered w-full"
						autocomplete="current-password"
						bind:value={password}
						required
					/>
				</div>

				<button type="submit" class="btn {oidcEnabled ? 'btn-outline' : 'btn-primary'} w-full" disabled={submitting}>
					{submitting ? 'Logging in…' : 'Log in'}
				</button>
			</form>
		</div>
	</div>
</div>
