<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { changePassword, getAuthConfig, getCurrentUser } from '$lib/api/sdk.gen';
	import { apiBaseUrl } from '$lib/client';
	import '$lib/client';

	let currentPassword = $state('');
	let newPassword = $state('');
	let confirmPassword = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);
	let saved = $state(false);

	let oidcEnabled = $state(false);
	let localLinked = $state(true);
	let oidcLinked = $state(false);
	let ssoMessage = $state<{ type: 'success' | 'error'; text: string } | null>(null);

	let mismatch = $derived(confirmPassword.length > 0 && newPassword !== confirmPassword);

	onMount(async () => {
		const params = $page.url.searchParams;
		if (params.get('oidcConnected')) {
			ssoMessage = { type: 'success', text: 'Single sign-on connected.' };
		} else if (params.get('oidcError') === 'already-linked') {
			ssoMessage = { type: 'error', text: 'That identity is already connected to a different account.' };
		} else if (params.get('oidcError')) {
			ssoMessage = { type: 'error', text: 'Connecting single sign-on failed. Try again.' };
		}

		const [{ data: authConfig }, { data: currentUser }] = await Promise.all([
			getAuthConfig(),
			getCurrentUser(),
		]);
		oidcEnabled = authConfig?.oidcEnabled ?? false;
		localLinked = currentUser?.localLinked ?? true;
		oidcLinked = currentUser?.oidcLinked ?? false;
	});

	async function handleSubmit() {
		if (mismatch) return;
		submitting = true;
		error = null;
		saved = false;
		const { error: changeError } = await changePassword({
			body: { currentPassword, newPassword },
		});
		if (changeError) {
			error = changeError.message ?? 'Current password is incorrect.';
		} else {
			saved = true;
			currentPassword = '';
			newPassword = '';
			confirmPassword = '';
		}
		submitting = false;
	}
</script>

<div class="max-w-5xl mx-auto p-4 sm:p-6 space-y-8">
	<h1 class="text-2xl font-bold">Security</h1>

	{#if ssoMessage}
		<div class="alert {ssoMessage.type === 'success' ? 'alert-success' : 'alert-error'} text-sm max-w-xs">
			<span>{ssoMessage.text}</span>
		</div>
	{/if}

	{#if oidcEnabled}
		<div class="space-y-2">
			<h2 class="text-lg font-semibold">Single sign-on</h2>
			{#if oidcLinked}
				<p class="text-sm">Connected via SSO.</p>
			{:else}
				<a href="{apiBaseUrl}/auth/oidc/connect" class="btn btn-outline btn-sm">Connect with SSO</a>
			{/if}
		</div>
	{/if}

	<div class="space-y-4">
		<h2 class="text-lg font-semibold">Change password</h2>
		{#if !localLinked}
			<p class="text-sm max-w-xs">
				You signed in via SSO and have no local password to change.
			</p>
		{:else}
			<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-4 max-w-xs">
			<div class="flex flex-col gap-1">
				<label class="text-sm font-medium" for="current-password">Current password</label>
				<input
					id="current-password"
					type="password"
					class="input input-bordered w-full"
					autocomplete="current-password"
					bind:value={currentPassword}
					required
				/>
			</div>
			<div class="flex flex-col gap-1">
				<label class="text-sm font-medium" for="new-password">New password</label>
				<input
					id="new-password"
					type="password"
					class="input input-bordered w-full"
					autocomplete="new-password"
					bind:value={newPassword}
					required
				/>
			</div>
			<div class="flex flex-col gap-1">
				<label class="text-sm font-medium" for="confirm-password">Confirm new password</label>
				<input
					id="confirm-password"
					type="password"
					class="input input-bordered w-full"
					autocomplete="new-password"
					bind:value={confirmPassword}
					required
				/>
				{#if mismatch}
					<p class="text-xs text-error">Passwords do not match.</p>
				{/if}
			</div>

			{#if error}
				<div class="alert alert-error text-sm"><span>{error}</span></div>
			{/if}
			{#if saved}
				<div class="alert alert-success text-sm"><span>Password changed.</span></div>
			{/if}

			<button type="submit" class="btn btn-primary btn-sm" disabled={submitting || mismatch}>
				{submitting ? 'Saving…' : 'Change password'}
			</button>
		</form>
		{/if}
	</div>
</div>
