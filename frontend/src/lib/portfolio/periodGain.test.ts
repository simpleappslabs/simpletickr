import { describe, expect, it } from 'vitest';
import { computePeriodGain } from './periodGain';
import type { PortfolioValuePoint } from '../api/types.gen';

function point(date: string, value?: number, invested?: number): PortfolioValuePoint {
	return { date, value, invested };
}

describe('computePeriodGain', () => {
	it('returns the change in (value - invested) between the first and last usable points', () => {
		const points = [
			point('2026-01-01', 1000, 1000),
			point('2026-02-01', 1100, 1000),
			point('2026-03-01', 1300, 1100),
		];

		const result = computePeriodGain(points);

		// gain at end: 1300 - 1100 = 200; gain at start: 1000 - 1000 = 0 -> amount = 200
		// pct against baseline invested (1000): 200 / 1000 * 100 = 20
		expect(result).toEqual({ amount: 200, pct: 20 });
	});

	it('skips leading points with no data when picking the baseline', () => {
		const points = [
			point('2026-01-01', undefined, undefined),
			point('2026-01-02', undefined, undefined),
			point('2026-01-03', 500, 400),
			point('2026-01-04', 600, 400),
		];

		const result = computePeriodGain(points);

		// baseline is the first usable point (Jan 3): gain 100; latest (Jan 4): gain 200 -> amount 100
		expect(result).toEqual({ amount: 100, pct: 25 });
	});

	it('skips trailing points with no data when picking the latest', () => {
		const points = [
			point('2026-01-01', 500, 400),
			point('2026-01-02', 600, 400),
			point('2026-01-03', undefined, undefined),
		];

		const result = computePeriodGain(points);

		expect(result).toEqual({ amount: 100, pct: 25 });
	});

	it('omits the percentage when the baseline has no invested capital', () => {
		const points = [point('2026-01-01', 0, 0), point('2026-01-02', 50, 0)];

		const result = computePeriodGain(points);

		expect(result).toEqual({ amount: 50, pct: null });
	});

	it('returns null when there is no usable data at all', () => {
		const points = [point('2026-01-01', undefined, undefined), point('2026-01-02', undefined, undefined)];

		expect(computePeriodGain(points)).toBeNull();
	});

	it('returns null for an empty points array', () => {
		expect(computePeriodGain([])).toBeNull();
	});

	it('returns a zero amount when there is only one usable point', () => {
		const points = [point('2026-01-01', 500, 400)];

		expect(computePeriodGain(points)).toEqual({ amount: 0, pct: 0 });
	});
});
