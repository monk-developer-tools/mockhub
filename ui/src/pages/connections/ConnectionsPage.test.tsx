import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const bank = {
  key: 'bank', name: 'Bank API', type: 'oauth2', tokenUrl: 'https://login.bank.test/token', clientId: 'mockhub', scope: '', audience: '',
  clientAuth: 'basic', headerName: 'Authorization', headerPrefix: 'Bearer ', hasSecret: true,
  createdAt: '2026-11-04T10:00:00Z', updatedAt: '2026-11-04T10:00:00Z',
};
let role = 'manager';
let connections: unknown[] = [bank];
const createConnection = vi.fn((_o: string, body: { key: string }) => Promise.resolve({ ...bank, key: body.key }));
const testConnection = vi.fn((..._a: unknown[]) => Promise.resolve({ ok: true, headerName: 'Authorization', preview: 'Bearer eyJh…x9Q2', expiresIn: 300 }));

vi.mock('../../state/AppContext', () => ({
  atLeast: (r: string, needed: string) => ['none', 'viewer', 'editor', 'manager', 'admin'].indexOf(r) >= ['none', 'viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => ({ currentOrg: { id: 'o1', name: 'Retail' }, roleIn: () => role }),
}));
vi.mock('../../api/client', () => ({
  api: {
    connections: () => Promise.resolve(connections),
    createConnection: (o: string, body: { key: string }) => createConnection(o, body),
    testConnection: (...a: unknown[]) => testConnection(...a),
    updateConnection: vi.fn(),
    deleteConnection: vi.fn(),
  },
}));

const { ConnectionsPage } = await import('./ConnectionsPage');

describe('ConnectionsPage', () => {
  beforeEach(() => {
    role = 'manager';
    connections = [bank];
    createConnection.mockClear();
    testConnection.mockClear();
  });

  it('lists connections without secrets and tests one', async () => {
    // Given
    renderWithShell(<ConnectionsPage />);
    await screen.findByText('Bank API');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Test' }));

    // Then
    expect(await screen.findByText('Authorization: Bearer eyJh…x9Q2 · expires in 5 minutes', { exact: false })).toBeInTheDocument();
    expect(testConnection).toHaveBeenCalledWith('o1', 'bank');
    expect(screen.getByText('https://login.bank.test/token')).toBeInTheDocument();
  });

  it('adds an API key connection with a key made from its name', async () => {
    // Given
    connections = [];
    renderWithShell(<ConnectionsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /New connection/ }));

    // When
    await userEvent.click(screen.getByRole('button', { name: 'API key header' }));
    await userEvent.type(screen.getByLabelText('Name'), 'Maps API');
    await userEvent.type(screen.getByLabelText(/^Header value/), 'k-123');
    await userEvent.click(screen.getByRole('button', { name: 'Add connection' }));

    // Then
    await waitFor(() => expect(createConnection).toHaveBeenCalledWith('o1', {
      key: 'maps-api', name: 'Maps API', type: 'header', headerName: 'X-Api-Key', headerPrefix: '', secret: 'k-123',
    }));
  });

  it('explains a missing client secret before sending anything', async () => {
    // Given
    connections = [];
    renderWithShell(<ConnectionsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /New connection/ }));

    // When
    await userEvent.type(screen.getByLabelText('Name'), 'Bank');
    await userEvent.type(screen.getByLabelText('Token URL'), 'https://login.bank.test/token');
    await userEvent.type(screen.getByLabelText('Client id'), 'mockhub');
    await userEvent.click(screen.getByRole('button', { name: 'Add connection' }));

    // Then
    expect(screen.getByRole('alert')).toHaveTextContent('Enter the client secret');
    expect(createConnection).not.toHaveBeenCalled();
  });

  it('shows editors the connections without changing them', async () => {
    // Given
    role = 'editor';

    // When
    renderWithShell(<ConnectionsPage />);

    // Then
    expect(await screen.findByText('Bank API')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Test' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /New connection/ })).not.toBeInTheDocument();
  });
});
