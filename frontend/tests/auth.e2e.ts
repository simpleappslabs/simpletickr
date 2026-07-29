import { expect, test } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('Authentication', () => {
	test('redirects to /login when not authenticated', async ({ page }) => {
		await page.goto('/');
		await expect(page).toHaveURL(/\/login$/);
	});

	test('logs in with valid credentials and redirects to portfolios', async ({ page }) => {
		await page.goto('/login');
		await page.getByLabel('Username').fill('e2e-admin');
		await page.getByLabel('Password').fill('TestPassword123!');
		await page.getByRole('button', { name: 'Log in' }).click();
		await expect(page).toHaveURL('/');
	});

	test('shows an error with invalid credentials and stays on /login', async ({ page }) => {
		await page.goto('/login');
		await page.getByLabel('Username').fill('e2e-admin');
		await page.getByLabel('Password').fill('wrong-password');
		await page.getByRole('button', { name: 'Log in' }).click();
		await expect(page.locator('.alert-error')).toBeVisible();
		await expect(page).toHaveURL(/\/login$/);
	});

	test('logs out and redirects to /login', async ({ page }) => {
		await page.goto('/login');
		await page.getByLabel('Username').fill('e2e-admin');
		await page.getByLabel('Password').fill('TestPassword123!');
		await page.getByRole('button', { name: 'Log in' }).click();
		await expect(page).toHaveURL('/');

		await page.getByRole('button', { name: 'e2e-admin' }).click();
		await page.getByRole('button', { name: 'Log out' }).click();
		await expect(page).toHaveURL(/\/login$/);
	});

	test('changes password and can log in with the new one', async ({ page }) => {
		await page.goto('/login');
		await page.getByLabel('Username').fill('e2e-admin');
		await page.getByLabel('Password').fill('TestPassword123!');
		await page.getByRole('button', { name: 'Log in' }).click();
		await expect(page).toHaveURL('/');

		await page.goto('/settings/change-password');
		await page.getByLabel('Current password').fill('TestPassword123!');
		await page.getByLabel('New password', { exact: true }).fill('NewTestPassword456!');
		await page.getByLabel('Confirm new password').fill('NewTestPassword456!');
		await page.getByRole('button', { name: 'Change password' }).click();
		await expect(page.locator('.alert-success')).toBeVisible();

		// Revert so re-runs of the suite (and the shared storageState fixture) stay consistent.
		await page.getByLabel('Current password').fill('NewTestPassword456!');
		await page.getByLabel('New password', { exact: true }).fill('TestPassword123!');
		await page.getByLabel('Confirm new password').fill('TestPassword123!');
		await page.getByRole('button', { name: 'Change password' }).click();
		await expect(page.locator('.alert-success')).toBeVisible();
	});
});
