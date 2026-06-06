import { expect, test } from '@playwright/test';

test.describe('Asset browser', () => {
	test('shows seeded assets in the table', async ({ page }) => {
		await page.goto('/assets');

		await expect(page.locator('text=AAPL')).toBeVisible();
		await expect(page.locator('text=BTC')).toBeVisible();
		await expect(page.locator('text=VWCE')).toBeVisible();
	});

	test('adds a new asset and shows it in the table', async ({ page }) => {
		const ticker = `T${Date.now().toString().slice(-8)}`;

		await page.goto('/assets');

		await page.fill('input[placeholder*="Ticker"]', ticker);
		await page.fill('input[placeholder*="Currency"]', 'USD');
		await page.fill('input[placeholder*="Name"]', `Test Asset ${ticker}`);
		await page.selectOption('select', 'STOCK');
		await page.click('button[type="submit"]');

		await expect(page.getByRole('cell', { name: ticker, exact: true })).toBeVisible();
	});

	test('submit is disabled with empty ticker', async ({ page }) => {
		await page.goto('/assets');
		await expect(page.locator('button[type="submit"]')).toBeDisabled();
	});

	test('navbar links are present', async ({ page }) => {
		await page.goto('/assets');
		await expect(page.locator('nav a', { hasText: 'Portfolios' })).toBeVisible();
		await expect(page.locator('nav a', { hasText: 'Assets' })).toBeVisible();
	});
});
