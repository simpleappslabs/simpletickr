import { expect, request, test, type Locator, type Page } from '@playwright/test';

const BACKEND_URL = 'http://localhost:8081/api';

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

	async function createPortfolioAndNavigate(page: Page, name = uniqueName('Transfer Test')) {
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await page.locator('dialog.modal-open .modal-box input[type="text"]').fill(name);
		await page.getByRole('button', { name: 'Create', exact: true }).click();
		await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
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

	test('transfers an asset between accounts, no price recorded', async ({ page }) => {
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

		const transferRow = page.getByRole('row', { name: /TRANSFER/ });
		await expect(transferRow).toBeVisible();
		await expect(transferRow).toContainText(sourceAccountName);
		await expect(transferRow).toContainText(destinationAccountName);
		await expect(transferRow.getByRole('cell').nth(3)).toHaveText('4.00');
		await expect(transferRow.getByRole('cell').nth(4)).toHaveText('—');
	});

	test('records an in-kind fee alongside the transfer', async ({ page }) => {
		await createPortfolioAndNavigate(page);
		await buyAsset(page, 'AAPL', '10', sourceAccountName);

		const dialog = await openTransferDialog(page);
		await selectAssetByTicker(page, 'AAPL');
		await dialog.getByRole('group', { name: 'Quantity' }).getByRole('spinbutton').fill('5');
		await dialog.getByRole('group', { name: /^Fee/ }).getByRole('spinbutton').fill('1');
		await dialog.getByRole('group', { name: 'Date' }).locator('input[type="date"]').fill('2024-02-01');
		await selectAccountByName(page, 'From account', sourceAccountName);
		await selectAccountByName(page, 'To account', destinationAccountName);

		await dialog.getByRole('button', { name: 'Record transfer', exact: true }).click();
		await expect(dialog).not.toBeVisible();

		const transferRow = page.getByRole('row', { name: /TRANSFER/ });
		await expect(transferRow.getByRole('cell').nth(3)).toHaveText('5.00');
		await expect(transferRow.getByRole('cell').nth(5)).toHaveText('1.00');
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
