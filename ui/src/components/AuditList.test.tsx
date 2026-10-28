import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AuditEntry } from '../api/types';
import { renderWithShell } from '../test/render';
import { AuditList } from './AuditList';

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString();

const entry = (over: Partial<AuditEntry>): AuditEntry => ({
  id: 'e1', at: minutesAgo(5), actorId: 'u1', actor: 'Sam Patel', action: 'created', target: 'stub',
  targetName: 'GET /orders', details: '', orgId: 'o1', teamId: 't1', projectId: 'p1', projectName: 'Shop', ...over,
});

const entries = [
  entry({ id: 'e1' }),
  entry({ id: 'e2', at: minutesAgo(30), actorId: 'u2', actor: 'Maria Silva', action: 'deleted', targetName: 'GET /legacy' }),
  entry({ id: 'e3', at: minutesAgo(60 * 24 * 10), action: 'updated', targetName: 'POST /orders', details: 'status 201' }),
];

describe('AuditList', () => {
  it('shows each change with its exact time and a summary', () => {
    // When
    renderWithShell(<AuditList entries={entries} retentionDays={90} showProject />);

    // Then
    expect(screen.getByText('3 changes')).toBeInTheDocument();
    expect(screen.getByText(/1 created/)).toBeInTheDocument();
    expect(screen.getByText(/1 deleted/)).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')[0].querySelector('time')?.textContent).toMatch(/^\d{2}.\d{2}.\d{2}$/);
    expect(screen.getByText('Today')).toBeInTheDocument();
  });

  it('filters by kind of change, person and time range', async () => {
    // Given
    renderWithShell(<AuditList entries={entries} retentionDays={90} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Deleted' }));

    // Then
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText(/GET \/legacy/)).toBeInTheDocument();

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await userEvent.selectOptions(screen.getByLabelText('Person'), 'u1');
    await userEvent.selectOptions(screen.getByLabelText('Time range'), 'week');

    // Then
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText(/GET \/orders/)).toBeInTheDocument();
  });

  it('opens the full details of an entry', async () => {
    // Given
    renderWithShell(<AuditList entries={entries} retentionDays={90} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /POST \/orders/ }));

    // Then
    const details = screen.getAllByRole('term')[0].closest('dl') as HTMLElement;
    expect(within(details).getByText('UTC')).toBeInTheDocument();
    expect(within(details).getByText(entries[2].at)).toBeInTheDocument();
    expect(within(details).getByText('status 201')).toBeInTheDocument();
    expect(within(details).getByRole('link', { name: 'Shop' })).toHaveAttribute('href', '/projects/p1/activity');
  });

  it('explains an empty log', () => {
    // When
    renderWithShell(<AuditList entries={[]} retentionDays={90} />);

    // Then
    expect(screen.getByText(/Nothing has changed yet/)).toBeInTheDocument();
  });
});
