import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { ContractView, DriftPatch, DriftView } from '../../model/contract';
import { renderWithShell } from '../../test/render';

const attached: ContractView = {
  attached: true, title: 'Orders API', version: '2.0', dialect: 'OpenAPI 3.0.3', source: 'import', fileName: 'orders.yaml',
  updatedAt: '2026-11-05T10:00:00Z', updatedBy: 'root', notifyUrl: null, stripPrefix: null, prefixes: [],
  operations: [{ method: 'GET', path: '/orders/{id}' }],
};
const drift: DriftView = {
  attached: true, lastCheckedAt: '2026-11-05T10:01:00Z', scanned: 12, capped: false, open: 2, expected: 1,
  findings: [
    { key: 'k1', kind: 'UNDOCUMENTED_PATH', label: 'Path not in the contract', side: 'request', method: 'GET', path: '/refunds', detail: '',
      count: 4, firstSeen: '2026-11-05T10:00:00Z', lastSeen: '2026-11-05T10:00:30Z', expected: false, example: { id: 'e1', url: '/shop/refunds', status: 200 } },
    { key: 'k2', kind: 'RESPONSE_BODY_SCHEMA', label: "Response body doesn't match its schema", side: 'response', method: 'GET', path: '/orders/{id}',
      detail: '200 $.total: string found, number expected', count: 2, firstSeen: '2026-11-05T10:00:00Z', lastSeen: '2026-11-05T10:00:30Z',
      expected: false, example: { id: 'e2', url: '/shop/orders/7', status: 200 } },
    { key: 'k3', kind: 'UNDOCUMENTED_HEADER', label: 'Header not in the contract', side: 'request', method: 'GET', path: '/orders/{id}',
      detail: 'X-Debug', count: 1, firstSeen: '2026-11-05T10:00:00Z', lastSeen: '2026-11-05T10:00:30Z', expected: true,
      example: { id: 'e3', url: '/shop/orders/1', status: 200 } },
  ],
};
const patch: DriftPatch = {
  summary: 'Documents refunds', operations: [{ op: 'add', path: '/paths/~1refunds', value: {} }],
  before: '{\n  "paths" : {\n    "/orders/{id}" : { }\n  }\n}', after: '{\n  "paths" : {\n    "/orders/{id}" : { },\n    "/refunds" : { }\n  }\n}',
};

let contract: ContractView = attached;
const saveContract = vi.fn((..._a: unknown[]) => Promise.resolve(attached));
const markFinding = vi.fn((..._a: unknown[]) => Promise.resolve(drift));
const driftPatch = vi.fn((..._a: unknown[]) => Promise.resolve(patch));
const recheckDrift = vi.fn((..._a: unknown[]) => Promise.resolve({ ...drift, findings: [], open: 0, expected: 0 }));
const checkDrift = vi.fn((..._a: unknown[]) => Promise.resolve(drift));

vi.mock('../../api/client', () => ({
  api: {
    contract: () => Promise.resolve(contract),
    drift: () => Promise.resolve(contract.attached ? drift : { ...drift, attached: false, findings: [], open: 0, expected: 0 }),
    saveContract: (...a: unknown[]) => saveContract(...a),
    checkDrift: (...a: unknown[]) => checkDrift(...a),
    recheckDrift: (...a: unknown[]) => recheckDrift(...a),
    markFinding: (...a: unknown[]) => markFinding(...a),
    driftPatch: (...a: unknown[]) => driftPatch(...a),
    setContractNotify: () => Promise.resolve(attached),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit: true, aiStatus: { enabled: true } }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', basePath: '/shop' } }) }));

const { ContractTab } = await import('./ContractTab');

describe('ContractTab', () => {
  beforeEach(() => {
    contract = attached;
    [saveContract, markFinding, driftPatch, recheckDrift, checkDrift].forEach((f) => f.mockClear());
  });

  it('shows open drift grouped by kind, request problems first', async () => {
    // When
    renderWithShell(<ContractTab />);

    // Then
    expect(await screen.findByText(/Orders API/)).toBeInTheDocument();
    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(headings[0]).toContain('Path not in the contract');
    expect(headings[1]).toContain("Response body doesn't match its schema");
    expect(screen.getByText('200 $.total: string found, number expected')).toBeInTheDocument();
    expect(screen.queryByText('X-Debug')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See calls for GET /refunds' })).toHaveAttribute('href', '/projects/p1/requests?method=GET&q=%2Fshop%2Frefunds');
  });

  it('shows expected findings under their own filter and reopens them', async () => {
    // Given
    renderWithShell(<ContractTab />);
    await userEvent.click(await screen.findByRole('button', { name: /^Expected/ }));

    // When
    const list = screen.getByRole('list', { name: 'Header not in the contract' });
    await userEvent.click(within(list).getByRole('button', { name: 'Reopen' }));

    // Then
    expect(markFinding).toHaveBeenCalledWith('p1', 'k3', false);
  });

  it('marks a finding as expected', async () => {
    // Given
    renderWithShell(<ContractTab />);
    const list = await screen.findByRole('list', { name: 'Path not in the contract' });

    // When
    await userEvent.click(within(list).getByRole('button', { name: 'Mark expected' }));

    // Then
    expect(markFinding).toHaveBeenCalledWith('p1', 'k1', true);
  });

  it('asks for a patch for the chosen findings, shows the diff and applies it', async () => {
    // Given
    renderWithShell(<ContractTab />);
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Choose GET /refunds' }));

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Suggest a patch for 1' }));
    const dialog = await screen.findByRole('dialog', { name: 'Suggested contract patch' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Apply to the contract' }));

    // Then
    expect(driftPatch).toHaveBeenCalledWith('p1', ['k1']);
    expect(within(dialog).getByLabelText('Patch')).toHaveTextContent(/\+\s+"\/refunds" : \{ \}/);
    await waitFor(() => expect(saveContract).toHaveBeenCalledWith('p1', { content: patch.after, source: 'ai-patch' }));
    expect(recheckDrift).toHaveBeenCalledWith('p1');
  });

  it('offers to attach a contract when the project has none', async () => {
    // Given
    contract = { attached: false };
    renderWithShell(<ContractTab />);
    await userEvent.click(await screen.findByRole('button', { name: 'Paste' }));

    // When
    await userEvent.type(screen.getByRole('textbox', { name: /OpenAPI 3 or Swagger 2/ }), 'openapi: 3.0.0');
    await userEvent.type(screen.getByRole('textbox', { name: /Prefix the traffic leaves out/ }), '/api');
    await userEvent.click(screen.getByRole('button', { name: 'Attach contract' }));

    // Then
    expect(screen.getByText('No contract yet')).toBeInTheDocument();
    await waitFor(() => expect(saveContract).toHaveBeenCalledWith('p1', { content: 'openapi: 3.0.0', source: 'editor', fileName: undefined, stripPrefix: '/api' }));
    expect(checkDrift).toHaveBeenCalledWith('p1');
  });
});
