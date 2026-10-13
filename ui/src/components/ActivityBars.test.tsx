import { fireEvent, render, screen } from '@testing-library/react';
import { ActivityBars } from './ActivityBars';

describe('ActivityBars', () => {
  it('summarises the hour and offers a table for screen readers', () => {
    // When
    render(<ActivityBars counts={[0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4]} label="Orders requests" />);

    // Then
    expect(screen.getByRole('figure')).toHaveAccessibleName('Orders requests: 7 requests in the last hour');
    expect(screen.getByRole('table', { hidden: true })).toHaveTextContent('last 5 min4');
  });

  it('shows a tooltip for the hovered bucket', () => {
    // Given
    const { container } = render(<ActivityBars counts={[1, 3]} label="x" />);

    // When
    fireEvent.mouseEnter(container.querySelectorAll('svg g')[1]);

    // Then
    expect(screen.getByRole('tooltip')).toHaveTextContent('3 requestslast 5 min');
  });
});
