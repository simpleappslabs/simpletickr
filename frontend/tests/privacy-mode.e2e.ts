import { expect, request, test, type Page } from '@playwright/test';

const BACKEND_URL = 'http://localhost:8081/api';

test.describe('Privacy mode', () => {
	test.beforeAll(async () => {
		const ctx = await request.newContext();
		await ctx.post(`${BACKEND_URL}/accounts`, {
			data: { name: 'Privacy Test Brokerage', accountType: 'BROKERAGE' },
		});
		await ctx.dispose();
	});

	async function selectAssetByTicker(page: Page, ticker: string) {
		const input = page.getByRole('group', { name: 'Asset' }).getByRole('textbox');
		await input.fill(ticker);
		await page.getByRole('button', { name: new RegExp(`^${ticker}`) }).first().click();
	}

	async function selectFirstAccount(page: Page) {
		const input = page.getByRole('group', { name: 'Account' }).getByRole('textbox');
		await input.click();
		const firstOption = page.locator('[data-account-dropdown] button').first();
		await firstOption.waitFor({ state: 'visible' });
		await firstOption.click();
	}

	async function createPortfolioWithTransaction(page: Page) {
		const name = `Privacy Test ${Date.now()}`;
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await page.locator('dialog.modal-open .modal-box input[type="text"]').fill(name);
		await page.getByRole('button', { name: 'Create', exact: true }).click();
		await page.getByRole('link', { name, exact: true }).click();
		await expect(page).toHaveURL(/\/portfolios\/\d+/);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();

		await selectAssetByTicker(page, 'AAPL');
		await selectFirstAccount(page);
		await page.getByRole('group', { name: 'Type' }).getByRole('combobox').selectOption('BUY');
		await page.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await page.getByRole('group', { name: 'Price per unit' }).getByRole('spinbutton').fill('180.00');
		await page.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-01-15');
		await page.getByRole('button', { name: 'Record', exact: true }).click();

		await expect(page.locator('dialog.modal-open .modal-box')).not.toBeVisible();
		await expect(page.getByRole('cell', { name: '180.00', exact: true })).toBeVisible();
	}

	// Scoped to the transaction row (identified by its exact-text ticker cell) rather than the whole
	// page, since the holdings table on the same page also renders a masked quantity for this position.
	function transactionRow(page: Page) {
		return page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'AAPL', exact: true }) });
	}

	test('toggling privacy mode masks and restores the transaction quantity and price, and persists across reload', async ({ page }) => {
		await createPortfolioWithTransaction(page);
		const row = transactionRow(page);

		await expect(row.getByRole('cell', { name: '180.00', exact: true })).toBeVisible();
		await expect(row.getByRole('cell', { name: '5.00', exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Hide amounts' }).click();
		await expect(row.getByRole('cell', { name: '••••••', exact: true })).toHaveCount(2);
		await expect(row.getByRole('cell', { name: '180.00', exact: true })).not.toBeVisible();
		await expect(row.getByRole('cell', { name: '5.00', exact: true })).not.toBeVisible();

		await page.reload();
		await expect(transactionRow(page).getByRole('cell', { name: '••••••', exact: true })).toHaveCount(2);

		await page.getByRole('button', { name: 'Show amounts' }).click();
		await expect(transactionRow(page).getByRole('cell', { name: '180.00', exact: true })).toBeVisible();
		await expect(transactionRow(page).getByRole('cell', { name: '5.00', exact: true })).toBeVisible();
	});
});
