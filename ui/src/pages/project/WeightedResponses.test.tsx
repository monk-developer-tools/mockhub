import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { emptyForm, type StubForm } from '../../model/stubForm';
import { renderWithShell } from '../../test/render';
import { WeightedResponses } from './WeightedResponses';

let latest: StubForm;

function Host({ readOnly = false, initial = emptyForm() }: { readOnly?: boolean; initial?: StubForm }) {
  const [form, setForm] = useState(initial);
  latest = form;
  return <WeightedResponses form={form} update={(patch) => setForm((f) => ({ ...f, ...patch }))} readOnly={readOnly} soap={false} />;
}

describe('WeightedResponses', () => {
  it('adds another response and shows the share of calls each gets', async () => {
    // Given
    renderWithShell(<Host />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Add another response/ }));
    const other = screen.getByRole('group', { name: 'Other response 1' });
    await userEvent.clear(within(other).getByLabelText('Weight of other response 1'));
    await userEvent.type(within(other).getByLabelText('Weight of other response 1'), '3');

    // Then
    expect(latest.alternatives).toHaveLength(1);
    expect(latest.alternatives[0].weight).toBe('3');
    expect(screen.getByText(/about 25% of calls/)).toBeInTheDocument();
    expect(within(other).getByText(/about 75% of calls/)).toBeInTheDocument();
  });

  it('removes a response', async () => {
    // Given
    renderWithShell(<Host initial={{ ...emptyForm(), alternatives: [{ weight: '1', status: '503', headers: [], body: '' }] }} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Remove other response 1' }));

    // Then
    expect(latest.alternatives).toEqual([]);
    expect(screen.queryByRole('group', { name: 'Other response 1' })).not.toBeInTheDocument();
  });

  it('shows the responses read-only to viewers', () => {
    // When
    renderWithShell(<Host readOnly initial={{ ...emptyForm(), alternatives: [{ weight: '1', status: '503', headers: [], body: '' }] }} />);

    // Then
    expect(screen.getByLabelText('Weight of other response 1')).toBeDisabled();
    expect(screen.queryByRole('button', { name: /Add another response/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remove other response 1' })).not.toBeInTheDocument();
  });
});
