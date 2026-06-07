import { expect, test, type Page } from '@playwright/test';

async function createPortfolio(page: Page, name: string) {
	await page.getByRole('button', { name: '+ New portfolio' }).click();
	await page.locator('dialog.modal-open .modal-box input[type="text"]').fill(name);
	await page.getByRole('button', { name: 'Create', exact: true }).click();
	await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
}

test.describe('Portfolio list', () => {
	test('shows the page header on load', async ({ page }) => {
		await page.goto('/');
		await expect(page.getByRole('heading', { name: 'Portfolios' })).toBeVisible();
	});

	test('creates a portfolio and shows it in the list', async ({ page }) => {
		await page.goto('/');
		await createPortfolio(page, `Test Portfolio ${Date.now()}`);
	});

	test('portfolio link navigates to detail page', async ({ page }) => {
		const name = `Nav Test ${Date.now()}`;

		await page.goto('/');
		await createPortfolio(page, name);
		await page.getByRole('link', { name, exact: true }).click();

		await expect(page).toHaveURL(/\/portfolios\/\d+/);
	});

	test('create button is disabled with empty input', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Create', exact: true })).toBeDisabled();
	});

	test('modal closes when cancel is clicked', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: '+ New portfolio' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).toBeVisible();
		await page.getByRole('button', { name: 'Cancel' }).click();
		await expect(page.locator('dialog.modal-open .modal-box')).not.toBeVisible();
	});
});
