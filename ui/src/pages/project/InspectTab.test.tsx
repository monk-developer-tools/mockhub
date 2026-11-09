import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import type { HistoryEntry, HistoryPage } from '../../api/types';
import { renderWithShell } from '../../test/render';

const entry = (id: string, patch: Partial<HistoryEntry> = {}): HistoryEntry => ({
  id, at: Date.parse('2026-11-09T10:00:00Z'), projectId: 'p1', method: 'POST', url: `/hooks/stripe?id=${id}`, absoluteUrl: '', clientIp: '10.0.0.1',
  requestHeaders: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Stripe-Signature', value: 't=1,v1=abc' }],
  requestBody: '{"type":"payment_intent.succeeded"}', requestBodyTruncated: false, status: 202,
  responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], responseBody: '{"received":true}', responseBodyTruncated: false,
  matched: true, stubId: 's1', stubName: 'Webhook receiver /hooks', durationMs: 4, ...patch,
});

let page: HistoryPage = { items: [], total: 0, truncated: false, facets: { methods: {}, statuses: {}, matched: 0, unmatched: 0, stubs: [] } };
const clearRequests = vi.fn(() => Promise.resolve());
let canEdit = true;

vi.mock('../../api/client', () => ({
  api: {
    history: () => Promise.resolve(page),
    historyEntry: (_p: string, id: string) => Promise.resolve({ entry: entry(id), callouts: [] }),
    stubs: () => Promise.resolve([{ request: { method: 'ANY', urlPathPattern: '/hooks(/.*)?' } }]),
    clearRequests: () => clearRequests(),
  },
}));
vi.mock('../../state/AppContext', () => ({ useApp: () => ({ canEdit, info: { baseUrl: 'https://m.example.com' } }) }));
vi.mock('./ProjectPage', () => ({
  useProject: () => ({ project: { id: 'p1', name: 'Webhooks', basePath: '/shop', mockUrl: 'https://m.example.com/shop', requestCount: page.total } }),
}));

const { InspectTab } = await import('./InspectTab');

function Where() {
  const location = useLocation();
  return <p>at {location.pathname}</p>;
}

function show() {
  return renderWithShell(
    <Routes>
      <Route path="/projects/:id/inspect" element={<InspectTab />} />
      <Route path="/projects/:id/stubs" element={<Where />} />
    </Routes>,
    '/projects/p1/inspect',
  );
}

describe('Inspect tab', () => {
  beforeEach(() => {
    canEdit = true;
    localStorage.clear();
    page = { items: [], total: 0, truncated: false, facets: { methods: {}, statuses: {}, matched: 0, unmatched: 0, stubs: [] } };
  });

  it('waits for the first request and sends a test to the receiver', async () => {
    // Given
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 202 }));
    vi.stubGlobal('fetch', fetchMock);
    show();

    // When
    expect(await screen.findByText('Waiting for requests')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Send test/ }));

    // Then
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('https://m.example.com/shop/hooks/test', expect.objectContaining({ method: 'POST' })));
    expect(screen.getByRole('region', { name: 'Get started' })).toHaveTextContent('Catch a request');
    vi.unstubAllGlobals();
  });

  it('shows a request beside its answer and makes a stub from it', async () => {
    // Given
    page = { ...page, items: [entry('a'), entry('b', { method: 'GET', url: '/hooks/ping', status: 404, matched: false, stubId: null, stubName: null })], total: 2 };
    show();

    // When
    await userEvent.click(await screen.findByRole('button', { name: /\/hooks\/stripe\?id=a/ }));
    const detail = screen.getByRole('region', { name: 'Request and answer' });
    await within(detail).findByText('Stripe-Signature');

    // Then
    expect(within(detail).getByText('id')).toBeInTheDocument();
    expect(within(detail).getByText(/"type": "payment_intent.succeeded"/)).toBeInTheDocument();
    expect(within(detail).getByText(/"received": true/)).toBeInTheDocument();
    expect(within(detail).getByRole('link', { name: 'Webhook receiver /hooks' })).toBeInTheDocument();
    expect(within(detail).getByRole('button', { name: /Copy as cURL/ })).toBeInTheDocument();
    await userEvent.click(within(detail).getByRole('button', { name: /Make a stub from it/ }));
    expect(await screen.findByText('at /projects/p1/stubs')).toBeInTheDocument();
  });

  it('lets viewers look without changing anything', async () => {
    // Given
    canEdit = false;
    page = { ...page, items: [entry('a')], total: 1 };

    // When
    show();
    await screen.findByRole('button', { name: /\/hooks\/stripe\?id=a/ });

    // Then
    expect(screen.queryByRole('button', { name: /Send test/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Clear/ })).not.toBeInTheDocument();
  });
});
