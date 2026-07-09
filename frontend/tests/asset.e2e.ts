import { expect, test } from '@playwright/test';

test.describe('Asset browser', () => {
	test('shows seeded assets in the table', async ({ page }) => {
		await page.goto('/settings/assets');

		await expect(page.locator('text=AAPL').first()).toBeVisible();
		await expect(page.locator('text=BTC').first()).toBeVisible();
		await expect(page.locator('text=VWCE').first()).toBeVisible();
	});

	test('adds a new asset and shows it in the table', async ({ page }) => {
		const ticker = `T${Date.now().toString().slice(-8)}`;

		await page.goto('/settings/assets');
		await page.getByRole('button', { name: '+ Add asset' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();

		await page.getByRole('group', { name: 'Ticker' }).getByRole('textbox').fill(ticker);
		await page.getByRole('group', { name: 'Currency' }).getByRole('textbox').fill('USD');
		await page.getByRole('group', { name: 'Name' }).getByRole('textbox').fill(`Test Asset ${ticker}`);
		await page.getByRole('group', { name: 'Type' }).getByRole('combobox').selectOption('STOCK');
		await page.getByRole('button', { name: 'Add asset', exact: true }).click();

		await expect(page.locator('dialog.modal-open .modal-box')).not.toBeVisible();
		await expect(page.getByRole('cell', { name: ticker, exact: true })).toBeVisible();
	});

	test('submit is disabled with empty fields', async ({ page }) => {
		await page.goto('/settings/assets');
		await page.getByRole('button', { name: '+ Add asset' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Add asset', exact: true })).toBeDisabled();
	});

	test('modal closes when cancel is clicked', async ({ page }) => {
		await page.goto('/settings/assets');
		await page.getByRole('button', { name: '+ Add asset' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();
		await page.getByRole('button', { name: 'Cancel' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).not.toBeVisible();
	});

	test('navbar links are present', async ({ page }) => {
		await page.goto('/settings/assets');
		await expect(page.locator('nav a', { hasText: 'Portfolios' })).toBeVisible();
		await expect(page.locator('nav a', { hasText: 'Settings' })).toBeVisible();
		await expect(page.getByRole('tab', { name: 'Assets' })).toBeVisible();
	});
});
