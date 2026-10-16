import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import type { Organization, Team } from '../api/types';
import { AccessEditor, NO_ACCESS, summarise, type Access } from './AccessEditor';

const org = { id: 'o1', name: 'Payments' } as Organization;
const teams = [{ id: 't1', orgId: 'o1', name: 'Checkout' }, { id: 't2', orgId: 'o1', name: 'Search' }] as Team[];
let last: Access = NO_ACCESS;

function Harness() {
  const [value, setValue] = useState<Access>(NO_ACCESS);
  last = value;
  return <AccessEditor tree={[{ org, teams }]} value={value} onChange={setValue} />;
}

describe('AccessEditor', () => {
  it('sets organisation-wide and per-team roles', async () => {
    // Given
    render(<Harness />);

    // When
    await userEvent.selectOptions(screen.getByLabelText('Organisation-wide role in Payments'), 'viewer');
    await userEvent.selectOptions(screen.getByLabelText('Role in team Checkout'), 'owner');

    // Then
    expect(last).toEqual({ memberships: [{ orgId: 'o1', role: 'viewer' }], teamMemberships: [{ teamId: 't1', role: 'owner' }] });
    expect(screen.getByLabelText('Role in team Search')).toHaveTextContent('Viewer through Payments');
    expect(summarise(last)).toBe('1 organisation-wide role and 1 team role');
  });

  it('removes a role again', async () => {
    // Given
    render(<Harness />);
    await userEvent.selectOptions(screen.getByLabelText('Role in team Search'), 'editor');

    // When
    await userEvent.selectOptions(screen.getByLabelText('Role in team Search'), '');

    // Then
    expect(last.teamMemberships).toEqual([]);
    expect(summarise(last)).toBe('No access yet');
  });
});
