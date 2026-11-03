import type { TrafficView } from '../api/types';
import { fromDays, fromView, rate } from './traffic';

describe('fromView', () => {
  it('keeps the counters and labels each bucket with its time span', () => {
    // Given
    const view: TrafficView = {
      window: '15m', bucketSeconds: 5, total: 7, unmatched: 2, averageRps: 0.35, peakRps: 3, firstAt: 0, lastAt: 5000,
      points: [{ at: 0, requests: 7, unmatched: 2 }],
    };

    // When
    const summary = fromView(view);

    // Then
    expect(summary).toMatchObject({ total: 7, unmatched: 2, average: '0.3', peak: '3', axis: ['15 min ago', '7.5 min', 'now'] });
    expect(summary.points[0]).toMatchObject({ requests: 7, unmatched: 2 });
    expect(summary.points[0].label).toContain('–');
  });
});

describe('fromDays', () => {
  it('averages over the days that saw traffic and finds the busiest one', () => {
    // When
    const summary = fromDays([
      { date: '2026-11-01', requests: 0, unmatched: 0 },
      { date: '2026-11-02', requests: 30, unmatched: 3 },
      { date: '2026-11-03', requests: 10, unmatched: 0 },
    ]);

    // Then
    expect(summary).toMatchObject({ total: 40, unmatched: 3, average: '20', peak: '30' });
    expect(summary.points).toHaveLength(3);
  });
});

describe('rate', () => {
  it('rounds requests per second for display', () => {
    // When
    const shown = [0, 0.04, 2.345, 12.6].map(rate);

    // Then
    expect(shown).toEqual(['0', '<0.1', '2.3', '13']);
  });
});
