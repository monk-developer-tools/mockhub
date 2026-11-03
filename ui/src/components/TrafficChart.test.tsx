import { fireEvent, render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { TrafficChart, type TrafficPoint } from './TrafficChart';

const points: TrafficPoint[] = [0, 1, 2, 3].map((i) => ({ label: `t${i}`, requests: i + 1, unmatched: i === 2 ? 1 : 0, from: i * 1000, to: (i + 1) * 1000 }));

describe('TrafficChart', () => {
  it('reports the time span dragged across', () => {
    // Given
    const onSelect = vi.fn();
    const { container } = render(<TrafficChart points={points} axis={['a', 'b', 'c']} label="Traffic" onSelect={onSelect} />);
    const bars = container.querySelectorAll('svg g');

    // When
    fireEvent.mouseEnter(bars[1]);
    fireEvent.mouseDown(bars[1]);
    fireEvent.mouseEnter(bars[3]);
    fireEvent.mouseUp(bars[3]);

    // Then
    expect(onSelect).toHaveBeenCalledWith(1000, 4000);
  });

  it('describes a hovered bar and lists the busy buckets for screen readers', () => {
    // Given
    const { container } = render(<TrafficChart points={points} axis={['a', 'b', 'c']} label="Traffic" />);

    // When
    fireEvent.mouseEnter(container.querySelectorAll('svg g')[2]);

    // Then
    expect(screen.getByRole('tooltip')).toHaveTextContent('3 requests1 unmatchedt2');
    expect(screen.getByRole('table', { name: 'Traffic' }).querySelectorAll('tr')).toHaveLength(4);
  });
});
