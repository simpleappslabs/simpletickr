import type { PortfolioValuePoint } from '../api/types.gen';

export interface PeriodGain {
	amount: number;
	pct: number | null;
}

function gainAt(point: PortfolioValuePoint): number {
	return point.value! - point.invested!;
}

// Baseline/latest are the first/last points with usable data, not necessarily the range's literal
// first/last date - a portfolio that didn't exist yet at the range start, or a price/FX gap at
// either edge, shouldn't blank out the whole metric.
export function computePeriodGain(points: PortfolioValuePoint[]): PeriodGain | null {
	const usable = points.filter((p) => p.value != null && p.invested != null);
	if (usable.length === 0) return null;

	const baseline = usable[0];
	const latest = usable[usable.length - 1];

	const amount = gainAt(latest) - gainAt(baseline);
	const pct = baseline.invested! > 0 ? (amount / baseline.invested!) * 100 : null;

	return { amount, pct };
}

// Shared with PortfolioSummary's "Unrealized gain" stat so a portfolio's gain figures read the
// same way everywhere they appear, regardless of the viewer's browser locale.
export function formatGainNumber(n: number): string {
	return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
