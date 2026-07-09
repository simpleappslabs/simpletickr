import { expect, request, test, type Page } from '@playwright/test';

const BACKEND_URL = 'http://localhost:8081';

test.describe('Transaction recording', () => {
	test.beforeAll(async () => {
		const ctx = await request.newContext();
		await ctx.post(`${BACKEND_URL}/accounts`, {
			data: { name: 'Test Brokerage', accountType: 'BROKERAGE' },
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
		// Portal-mounted dropdown identified by data-account-dropdown; wait for accounts to load
		const firstOption = page.locator('[data-account-dropdown] button').first();
		await firstOption.waitFor({ state: 'visible' });
		await firstOption.click();
	}

	async function createPortfolioAndNavigate(page: Page) {
		const name = `Txn Test ${Date.now()}`;
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await page.locator('dialog.modal-open .modal-box input[type="text"]').fill(name);
		await page.getByRole('button', { name: 'Create', exact: true }).click();
		await page.getByRole('link', { name, exact: true }).click();
		await expect(page).toHaveURL(/\/portfolios\/\d+/);
		return name;
	}

	test('records a transaction and shows the holding', async ({ page }) => {
		await createPortfolioAndNavigate(page);

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
		await expect(page.getByRole('cell', { name: 'AAPL', exact: true }).first()).toBeVisible();
	});

	test('submit is disabled until required fields are filled', async ({ page }) => {
		await createPortfolioAndNavigate(page);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();

		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeDisabled();

		await selectAssetByTicker(page, 'AAPL');
		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeDisabled();

		await page.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await page.getByRole('group', { name: 'Price per unit' }).getByRole('spinbutton').fill('180.00');
		await page.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-01-15');
		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeDisabled();

		await selectFirstAccount(page);
		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeEnabled();
	});

	test('modal closes when cancel is clicked', async ({ page }) => {
		await createPortfolioAndNavigate(page);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();

		await page.getByRole('button', { name: 'Cancel' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).not.toBeVisible();
	});
});
