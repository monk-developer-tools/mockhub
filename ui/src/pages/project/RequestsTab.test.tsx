import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { HistoryEntry, HistoryPage } from '../../api/types';
import { renderWithShell } from '../../test/render';

const entry = (id: string, patch: Partial<HistoryEntry> = {}): HistoryEntry => ({
  id, at: Date.now(), projectId: 'p1', method: 'GET', url: `/pay/orders/${id}`, absoluteUrl: '', clientIp: '10.0.0.1',
  requestHeaders: [], requestBody: '', requestBodyTruncated: false, status: 200, responseHeaders: [], responseBody: '',
  responseBodyTruncated: false, matched: true, stubId: 's1', stubName: 'Get order', durationMs: 3, ...patch,
});

const missed = entry('b', { url: '/pay/refunds', method: 'POST', status: 404, matched: false, stubId: null, stubName: null });
const page: HistoryPage = {
  items: [entry('a'), missed],
  total: 2,
  truncated: false,
  facets: { methods: { GET: 1, POST: 1 }, statuses: { '2xx': 1, '4xx': 1 }, matched: 1, unmatched: 1, stubs: [{ id: 's1', name: 'Get order', count: 1 }] },
};

const history = vi.fn((_id: string, _query: string) => Promise.resolve(page));
const exportHistory = vi.fn((_id: string, _query: string) => Promise.resolve());
const nearMisses = vi.fn(() => Promise.resolve({ nearMisses: [{ stubMapping: { name: 'Refund order', request: { method: 'POST', urlPath: '/refunds/{id}' }, response: {} }, matchResult: { distance: 0.1 } }] }));
const callout = { id: 'c1', at: '', projectId: 'p1', stubId: 's1', stubName: 'Get order', mode: 'async', trigger: null, request: { method: 'POST', url: 'https://partner.test/hook', headers: [], body: '' }, response: { status: 204, headers: [], body: '', truncated: false }, error: null, durationMs: 9, requestId: 'a' };

vi.mock('../../api/client', () => ({
  api: {
    history: (id: string, query: string) => history(id, query),
    historyEntry: (_p: string, id: string) => Promise.resolve({ entry: id === 'a' ? { ...entry('a'), requestBody: '{"x":1}' } : missed, callouts: id === 'a' ? [callout] : [] }),
    exportHistory: (id: string, query: string) => exportHistory(id, query),
    nearMisses: () => nearMisses(),
    traffic: () => Promise.resolve({ window: '15m', bucketSeconds: 5, points: [], total: 2, unmatched: 1, averageRps: 0.1, peakRps: 1, firstAt: null, lastAt: null }),
    trafficDaily: () => Promise.resolve([]),
    stubs: () => Promise.resolve([]),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit: true }) }));
vi.mock('./ProjectPage', () => ({ useProject: () => ({ project: { id: 'p1', basePath: '/pay', mockUrl: 'http://h/pay' }, reloadProject: vi.fn() }) }));

const { RequestsTab } = await import('./RequestsTab');

const lastQuery = () => Object.fromEntries(new URLSearchParams(history.mock.calls[history.mock.calls.length - 1]?.[1]));

describe('RequestsTab', () => {
  beforeEach(() => {
    history.mockClear();
    exportHistory.mockClear();
  });

  it('lists the history with traffic figures and status quick filters', async () => {
    // When
    renderWithShell(<RequestsTab />);

    // Then
    expect(await screen.findByText('/pay/refunds')).toBeInTheDocument();
    const traffic = screen.getByRole('region', { name: 'Traffic' });
    await waitFor(() => expect(within(traffic).getByText('Unmatched', { selector: 'dt' }).nextSibling).toHaveTextContent('1'));
    expect(screen.getByText('no match')).toBeInTheDocument();
    expect(screen.getByText(/1–2 of 2 requests/)).toBeInTheDocument();
  });

  it('searches with the chosen filters', async () => {
    // Given
    renderWithShell(<RequestsTab />);
    await screen.findByText('/pay/refunds');

    // When
    await userEvent.selectOptions(screen.getByLabelText('Method'), 'POST');
    await userEvent.click(screen.getByRole('button', { name: /4xx/ }));
    await userEvent.type(screen.getByLabelText('Search'), 'refund');

    // Then
    await waitFor(() => expect(lastQuery()).toEqual({ method: 'POST', status: '4xx', q: 'refund', limit: '100' }));
    expect(screen.getByRole('button', { name: /4xx/ })).toHaveAttribute('aria-pressed', 'true');
  });

  it('exports what the filters match as HAR', async () => {
    // Given
    renderWithShell(<RequestsTab />, '/?matched=unmatched');
    await screen.findByText('/pay/refunds');

    // When
    await userEvent.click(screen.getByRole('button', { name: /Export HAR/ }));

    // Then
    expect(Object.fromEntries(new URLSearchParams(exportHistory.mock.calls[0][1]))).toEqual({ matched: 'false', limit: '500' });
  });

  it('shows the closest stubs for an unmatched request', async () => {
    // Given
    renderWithShell(<RequestsTab />);

    // When
    await userEvent.click(await screen.findByText('/pay/refunds'));

    // Then
    const drawer = screen.getByRole('complementary', { name: 'Request details' });
    expect(await within(drawer).findByText(/Refund order — 90% match/)).toBeInTheDocument();
  });

  it('shows the body and the callouts a matched request triggered', async () => {
    // Given
    renderWithShell(<RequestsTab />);

    // When
    await userEvent.click(await screen.findByText('/pay/orders/a'));

    // Then
    const drawer = screen.getByRole('complementary', { name: 'Request details' });
    expect(await within(drawer).findByText('https://partner.test/hook')).toBeInTheDocument();
    expect(within(drawer).getByText('204')).toBeInTheDocument();
    expect(within(drawer).getByText(/"x": 1/)).toBeInTheDocument();
  });
});
