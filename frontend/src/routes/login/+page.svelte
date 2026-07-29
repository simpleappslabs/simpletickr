<script lang="ts">
	import { goto } from '$app/navigation';
	import { login } from '$lib/api/sdk.gen';
	import { authState } from '$lib/authState.svelte';
	import '$lib/client';

	let username = $state('');
	let password = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);

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

				{#if error}
					<div class="alert alert-error text-sm"><span>{error}</span></div>
				{/if}

				<button type="submit" class="btn btn-primary w-full" disabled={submitting}>
					{submitting ? 'Logging in…' : 'Log in'}
				</button>
			</form>
		</div>
	</div>
</div>
