import { expect, test } from '@playwright/test';

test.describe('Portfolio list', () => {
	test('shows portfolios section on load', async ({ page }) => {
		await page.goto('/');
		await expect(page.locator('h2').first()).toBeVisible();
	});

	test('creates a portfolio and shows it in the list', async ({ page }) => {
		const name = `Test Portfolio ${Date.now()}`;

		await page.goto('/');
		await page.fill('input[placeholder="Portfolio name"]', name);
		await page.click('button[type="submit"]');

		await expect(page.locator(`text=${name}`)).toBeVisible();
	});

	test('portfolio link navigates to detail page', async ({ page }) => {
		const name = `Nav Test ${Date.now()}`;

		await page.goto('/');
		await page.fill('input[placeholder="Portfolio name"]', name);
		await page.click('button[type="submit"]');
		await expect(page.locator(`text=${name}`)).toBeVisible();

		await page.click(`text=${name}`);
		await expect(page).toHaveURL(/\/portfolios\/\d+/);
	});

	test('create button is disabled with empty input', async ({ page }) => {
		await page.goto('/');
		await expect(page.locator('button[type="submit"]')).toBeDisabled();
	});
});
