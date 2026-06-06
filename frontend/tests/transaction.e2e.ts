import { expect, test, type Page } from '@playwright/test';

test.describe('Transaction recording', () => {
	async function selectAssetByTicker(page: Page, ticker: string) {
		const select = page.getByRole('group', { name: 'Asset' }).getByRole('combobox');
		const value = await select.locator('option', { hasText: ticker }).getAttribute('value');
		await select.selectOption(value!);
	}

	async function createPortfolioAndNavigate(page: Page) {
		const name = `Txn Test ${Date.now()}`;
		await page.goto('/');
		await page.fill('input[placeholder="Portfolio name"]', name);
		await page.click('button[type="submit"]');
		await expect(page.locator(`text=${name}`)).toBeVisible();
		await page.click(`text=${name}`);
		await expect(page).toHaveURL(/\/portfolios\/\d+/);
		return name;
	}

	test('records a transaction and shows the holding', async ({ page }) => {
		await createPortfolioAndNavigate(page);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('.modal-box')).toBeVisible();

		await selectAssetByTicker(page, 'AAPL');
		await page.getByRole('group', { name: 'Type' }).getByRole('combobox').selectOption('BUY');
		await page.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await page.getByRole('group', { name: 'Price per unit' }).getByRole('spinbutton').fill('180.00');
		await page.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-01-15');

		await page.getByRole('button', { name: 'Record', exact: true }).click();

		await expect(page.locator('.modal-box')).not.toBeVisible();
		await expect(page.getByRole('cell', { name: 'AAPL', exact: true })).toBeVisible();
	});

	test('submit is disabled until required fields are filled', async ({ page }) => {
		await createPortfolioAndNavigate(page);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('.modal-box')).toBeVisible();

		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeDisabled();

		await selectAssetByTicker(page, 'AAPL');
		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeDisabled();

		await page.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await page.getByRole('group', { name: 'Price per unit' }).getByRole('spinbutton').fill('180.00');
		await page.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-01-15');

		await expect(page.getByRole('button', { name: 'Record', exact: true })).toBeEnabled();
	});

	test('modal closes when cancel is clicked', async ({ page }) => {
		await createPortfolioAndNavigate(page);

		await page.getByRole('button', { name: /Record transaction/ }).click();
		await expect(page.locator('.modal-box')).toBeVisible();

		await page.getByRole('button', { name: 'Cancel' }).click();
		await expect(page.locator('.modal-box')).not.toBeVisible();
	});
});
