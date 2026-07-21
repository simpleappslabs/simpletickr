import { expect, request, test, type Locator, type Page } from '@playwright/test';

const BACKEND_URL = 'http://localhost:8081';

let sourceAccountName: string;
let destinationAccountName: string;

function uniqueName(prefix: string) {
	return `${prefix} ${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

test.describe('Transfers', () => {
	test.beforeAll(async () => {
		sourceAccountName = uniqueName('Transfer Source');
		destinationAccountName = uniqueName('Transfer Destination');
		const ctx = await request.newContext();
		await ctx.post(`${BACKEND_URL}/accounts`, { data: { name: sourceAccountName, accountType: 'BROKERAGE' } });
		await ctx.post(`${BACKEND_URL}/accounts`, { data: { name: destinationAccountName, accountType: 'BROKERAGE' } });
		await ctx.dispose();
	});

	function openDialog(page: Page): Locator {
		return page.locator('dialog.modal-open .modal-box');
	}

	async function selectAssetByTicker(page: Page, ticker: string) {
		const input = openDialog(page).getByRole('group', { name: 'Asset' }).getByRole('textbox');
		await input.fill(ticker);
		await page.getByRole('button', { name: new RegExp(`^${ticker}`) }).first().click();
	}

	async function selectAccountByName(page: Page, groupName: string, accountName: string) {
		const input = openDialog(page).getByRole('group', { name: groupName }).getByRole('textbox');
		await input.fill(accountName);
		const option = page.locator('[data-account-dropdown] button', { hasText: accountName });
		await option.first().waitFor({ state: 'visible' });
		await option.first().click();
	}

	async function createPortfolio(page: Page, name: string) {
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await page.locator('dialog.modal-open .modal-box input[type="text"]').fill(name);
		await page.getByRole('button', { name: 'Create', exact: true }).click();
		await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
	}

	async function createPortfolioAndNavigate(page: Page, name = uniqueName('Transfer Test')) {
		await createPortfolio(page, name);
		await page.getByRole('link', { name, exact: true }).click();
		await expect(page).toHaveURL(/\/portfolios\/\d+/);
		return name;
	}

	async function buyAsset(page: Page, ticker: string, quantity: string, accountName: string) {
		await page.getByRole('button', { name: /Record transaction/ }).click();
		const dialog = openDialog(page);
		await expect(dialog).toBeVisible();

		await selectAssetByTicker(page, ticker);
		await selectAccountByName(page, 'Account', accountName);
		await dialog.getByRole('group', { name: 'Type' }).getByRole('combobox').selectOption('BUY');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill(quantity);
		await dialog.getByRole('group', { name: 'Price per unit' }).getByRole('spinbutton').fill('180.00');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-01-15');
		await dialog.getByRole('button', { name: 'Record', exact: true }).click();

		await expect(dialog).not.toBeVisible();
	}

	async function openTransferDialog(page: Page): Promise<Locator> {
		await page.getByRole('button', { name: '⇄ Transfer' }).click();
		const dialog = openDialog(page);
		await expect(dialog).toBeVisible();
		return dialog;
	}

	test('transfers an asset between accounts in the same portfolio', async ({ page }) => {
		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await selectAssetByTicker(page, 'AAPL');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('4');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-02-01');
		await selectAccountByName(page, 'From account', sourceAccountName);
		await selectAccountByName(page, 'To account', destinationAccountName);
		await dialog.getByRole('button', { name: 'Record transfer', exact: true }).click();

		await expect(dialog).not.toBeVisible();
		await expect(page.getByRole('cell', { name: 'TRANSFER_OUT' }).first()).toBeVisible();
		await expect(page.getByRole('cell', { name: 'TRANSFER_IN' }).first()).toBeVisible();
	});

	test('in-kind fee reduces the quantity received', async ({ page }) => {
		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await selectAssetByTicker(page, 'AAPL');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await dialog.getByRole('group', { name: /^Fee/ }).getByRole('spinbutton').fill('1');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-02-01');
		await selectAccountByName(page, 'From account', sourceAccountName);
		await selectAccountByName(page, 'To account', destinationAccountName);

		await expect(dialog.getByText('Received at destination').locator('..')).toContainText('4');

		await dialog.getByRole('button', { name: 'Record transfer', exact: true }).click();
		await expect(dialog).not.toBeVisible();

		const inRow = page.getByRole('row', { name: /TRANSFER_IN/ });
		await expect(inRow.getByRole('cell').nth(3)).toHaveText('4.00');
	});

	test('transfers an asset to a different portfolio', async ({ page }) => {
		const destinationPortfolioName = uniqueName('Transfer Dest');
		await createPortfolio(page, destinationPortfolioName);

		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await selectAssetByTicker(page, 'AAPL');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('3');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-02-01');
		await selectAccountByName(page, 'From account', sourceAccountName);
		await selectAccountByName(page, 'To account', destinationAccountName);

		const portfolioSelect = dialog.getByRole('group', { name: 'To portfolio' }).getByRole('combobox');
		await expect(portfolioSelect.locator('option', { hasText: destinationPortfolioName })).toHaveCount(1);
		await portfolioSelect.selectOption({ label: destinationPortfolioName });

		await dialog.getByRole('button', { name: 'Record transfer', exact: true }).click();
		await expect(dialog).not.toBeVisible();

		await page.goto('/');
		await page.getByRole('link', { name: destinationPortfolioName, exact: true }).click();
		await expect(page).toHaveURL(/\/portfolios\/\d+/);
		await expect(page.getByRole('cell', { name: 'TRANSFER_IN' }).first()).toBeVisible();
	});

	test('submit is disabled until required fields are filled', async ({ page }) => {
		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await expect(dialog.getByRole('button', { name: 'Record transfer', exact: true })).toBeDisabled();

		await selectAssetByTicker(page, 'AAPL');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('4');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-02-01');
		await expect(dialog.getByRole('button', { name: 'Record transfer', exact: true })).toBeDisabled();

		await selectAccountByName(page, 'From account', sourceAccountName);
		await expect(dialog.getByRole('button', { name: 'Record transfer', exact: true })).toBeDisabled();

		await selectAccountByName(page, 'To account', destinationAccountName);
		await expect(dialog.getByRole('button', { name: 'Record transfer', exact: true })).toBeEnabled();
	});

	test('modal closes when cancel is clicked', async ({ page }) => {
		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await dialog.getByRole('button', { name: 'Cancel' }).click();
		await expect(dialog).not.toBeVisible();
	});
});