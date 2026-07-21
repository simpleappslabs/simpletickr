import type { Transaction, Transfer } from '$lib/api/types.gen';

export type LedgerEntry =
	| { kind: 'transaction'; date: string; transaction: Transaction }
	| { kind: 'transfer'; date: string; transfer: Transfer };

export function mergeLedger(transactions: Transaction[], transfers: Transfer[]): LedgerEntry[] {
	const entries: LedgerEntry[] = [
		...transactions.map((transaction): LedgerEntry => ({ kind: 'transaction', date: transaction.date, transaction })),
		...transfers.map((transfer): LedgerEntry => ({ kind: 'transfer', date: transfer.date, transfer })),
	];
	return entries.sort((a, b) => b.date.localeCompare(a.date));
}
