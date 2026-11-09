import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';

const renderTemplate = vi.fn((..._a: unknown[]) => Promise.resolve({ output: '{"orderId": "42"}' }));
const generateData = vi.fn((..._a: unknown[]) => Promise.resolve({ records: [{ id: 1, name: 'Ada' }, { id: 2, name: 'Alan' }] }));
const createDataset = vi.fn((..._a: unknown[]) => Promise.resolve({}));
const uploadDataset = vi.fn((..._a: unknown[]) => Promise.resolve({ added: 2, updated: 0, skipped: 0 }));
let role = 'editor';

vi.mock('../../api/client', () => ({
  api: {
    renderTemplate: (...a: unknown[]) => renderTemplate(...a),
    generateData: (...a: unknown[]) => generateData(...a),
    datasets: () => Promise.resolve({ datasets: [], limits: {} }),
    createDataset: (...a: unknown[]) => createDataset(...a),
    uploadDataset: (...a: unknown[]) => uploadDataset(...a),
  },
}));
vi.mock('../../state/AppContext', () => ({
  atLeast: (r: string, needed: string) => ['viewer', 'editor', 'manager', 'admin'].indexOf(r) >= ['viewer', 'editor', 'manager', 'admin'].indexOf(needed),
  useApp: () => ({ canEdit: role !== 'viewer', currentOrg: { id: 'o1', name: 'Acme' }, roleIn: () => role }),
}));

const { TemplatePlaygroundPage } = await import('./TemplatePlaygroundPage');
const { FakeDataPage } = await import('./FakeDataPage');

function Where() {
  const { pathname, state } = useLocation();
  return <p>at {pathname} with {JSON.stringify(state)}</p>;
}

describe('Tools signed in', () => {
  beforeEach(() => {
    role = 'editor';
    [renderTemplate, generateData, createDataset, uploadDataset].forEach((f) => f.mockClear());
  });

  it('renders the template as you type and opens it as a templated stub', async () => {
    // Given
    renderWithShell(
      <Routes>
        <Route path="/tools/template-playground" element={<TemplatePlaygroundPage />} />
        <Route path="/new/json" element={<Where />} />
      </Routes>,
      '/tools/template-playground',
    );

    // When
    await waitFor(() => expect(screen.getByLabelText('Output')).toHaveTextContent('"orderId": "42"'));
    await userEvent.click(screen.getByRole('button', { name: /Use as a stub/ }));

    // Then
    expect(renderTemplate).toHaveBeenCalledWith(expect.stringContaining('request.path'), expect.objectContaining({ method: 'POST', url: '/orders/42?customer=ada', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': 'abc-123' } }));
    expect(await screen.findByText(/at \/new\/json with .*"path":"\/orders\/42".*"templated":true/)).toBeInTheDocument();
  });

  it('shows template errors in plain words', async () => {
    // Given
    renderTemplate.mockResolvedValueOnce({ error: "could not find helper: 'nope'" } as never);

    // When
    renderWithShell(<TemplatePlaygroundPage />);

    // Then
    expect(await screen.findByRole('alert')).toHaveTextContent("could not find helper: 'nope'");
  });

  it('generates records, switches to CSV and saves them as a dataset', async () => {
    // Given
    renderWithShell(<FakeDataPage />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /^Generate$/ }));
    await screen.findByText('2 records');
    await userEvent.click(screen.getByRole('button', { name: 'CSV' }));
    await userEvent.click(screen.getByRole('button', { name: /Save as a dataset/ }));

    // Then
    expect(screen.getByLabelText('Generated data')).toHaveTextContent('id,name 1,Ada 2,Alan');
    await waitFor(() => expect(uploadDataset).toHaveBeenCalledWith('o1', 'people', 'json', 'replace', expect.any(Blob)));
    expect(createDataset).toHaveBeenCalledWith('o1', { key: 'people', name: 'people' });
  });

  it('only offers downloads to viewers', async () => {
    // Given
    role = 'viewer';
    renderWithShell(<FakeDataPage />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /^Generate$/ }));

    // Then
    expect(await screen.findByRole('button', { name: /Download/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Save as a dataset/ })).not.toBeInTheDocument();
    expect(screen.getByText(/With the editor role you can save/)).toBeInTheDocument();
  });
});
