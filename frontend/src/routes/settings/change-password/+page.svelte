<script lang="ts">
	import { changePassword } from '$lib/api/sdk.gen';
	import '$lib/client';

	let currentPassword = $state('');
	let newPassword = $state('');
	let confirmPassword = $state('');
	let submitting = $state(false);
	let error = $state<string | null>(null);
	let saved = $state(false);

	let mismatch = $derived(confirmPassword.length > 0 && newPassword !== confirmPassword);

	async function handleSubmit() {
		if (mismatch) return;
		submitting = true;
		error = null;
		saved = false;
		const { error: changeError } = await changePassword({
			body: { currentPassword, newPassword },
		});
		if (changeError) {
			error = 'Current password is incorrect.';
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
	<h1 class="text-2xl font-bold">Change password</h1>

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
</div>
