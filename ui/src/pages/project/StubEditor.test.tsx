import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { useState } from 'react';
import type { Project, Stub } from '../../api/types';
import { renderWithShell } from '../../test/render';
import { StubEditor } from './StubEditor';

const project = { id: 'p1', name: 'Shop', slug: 'shop', basePath: '/shop', mockUrl: 'http://x/shop', color: '#000', stubCount: 1, soapStubCount: 0, requestCount: 0, unmatchedCount: 0, activity: [] } as unknown as Project;
const stub: Stub = { id: 's1', request: { method: 'GET', urlPath: '/a' }, response: { status: 200, jsonBody: { ok: true } } };

function Host({ canEdit = true }: { canEdit?: boolean }) {
  const [, setDirty] = useState(false);
  return <StubEditor project={project} moveTargets={[]} stub={stub} isNew={false} canEdit={canEdit} onSaved={() => undefined} onDeleted={() => undefined} onDirtyChange={setDirty} />;
}

describe('StubEditor', () => {
  it('switches body type, delay and response mode', () => {
    // Given
    renderWithShell(<Host />);

    // When
    fireEvent.click(screen.getByRole('button', { name: 'XML' }));
    fireEvent.click(screen.getByRole('button', { name: 'Fixed' }));

    // Then
    expect(screen.getByRole('button', { name: 'XML' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Fixed' })).toHaveAttribute('aria-pressed', 'true');

    // When
    fireEvent.click(screen.getByRole('button', { name: 'Proxy' }));

    // Then
    expect(screen.getByRole('button', { name: 'Proxy' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('tells viewers the stub is read-only', () => {
    // Given
    renderWithShell(<Host canEdit={false} />);

    // When
    fireEvent.click(screen.getByRole('button', { name: 'XML' }));

    // Then
    expect(screen.getByRole('note')).toHaveTextContent('Read-only');
    expect(screen.getByRole('button', { name: 'XML' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'XML' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows calls from the request log and the on switch', () => {
    // Given
    const usage = { count: 3, lastCalledAt: new Date().toISOString() };

    // When
    renderWithShell(<StubEditor project={project} moveTargets={[]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} usage={usage} />);

    // Then
    expect(screen.getByRole('switch', { name: 'Stub is on' })).toBeChecked();
    expect(screen.getByText('3 calls in the request log, last just now')).toBeInTheDocument();
  });

  it('offers moving only when there is a project the person can edit', async () => {
    // Given
    const qa = { ...project, id: 'p2', name: 'Shop QA', basePath: '/shop-qa' };

    // When
    const { unmount } = renderWithShell(<StubEditor project={project} moveTargets={[]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} />);
    const withoutTargets = screen.queryByRole('button', { name: 'Move to another project' });
    unmount();
    renderWithShell(<StubEditor project={project} moveTargets={[qa]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} />);
    await userEvent.click(screen.getByRole('button', { name: 'Move to another project' }));

    // Then
    expect(withoutTargets).not.toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: /Shop QA/ })).toHaveTextContent('/shop-qa');
  });

  it('says where JSON urls start for a project at the root', () => {
    // Given
    const root = { ...project, basePath: '', root: true } as Project;
    renderWithShell(<StubEditor project={root} moveTargets={[]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} />);

    // When
    fireEvent.click(screen.getAllByRole('button', { name: 'JSON' })[0]);

    // Then
    expect(screen.getByText(/URLs start at the server root/)).toBeInTheDocument();
  });

  it('marks a disabled stub as off', () => {
    // Given
    const off: Stub = { ...stub, metadata: { mockhub: { disabled: true } } };

    // When
    renderWithShell(<StubEditor project={project} moveTargets={[]} stub={off} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} />);

    // Then
    expect(screen.getByRole('switch', { name: 'Stub is on' })).not.toBeChecked();
    expect(screen.getByText(/Requests skip this stub/)).toBeInTheDocument();
  });

  it('describes a failure rate and offers AI only when it is on', () => {
    // Given
    renderWithShell(<StubEditor project={project} moveTargets={[]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} aiEnabled />);

    // When
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Failure rate in percent' }), { target: { value: '10' } });

    // Then
    expect(screen.getByText(/About 10% of calls get 503/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Generate with AI/ })).toBeInTheDocument();
  });

  it('hides the AI button when the assistant is off', () => {
    // When
    renderWithShell(<Host />);

    // Then
    expect(screen.queryByRole('button', { name: /Generate with AI/ })).not.toBeInTheDocument();
  });

  it('waits for a callout and shows it in the JSON', async () => {
    // Given
    renderWithShell(<Host />);

    // When
    fireEvent.click(screen.getByRole('button', { name: 'Wait for callout' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Callout URL' }), { target: { value: 'https://issuer.test/auth' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'JSON' })[0]);

    // Then
    const json = screen.getByRole('textbox', { name: 'Stub JSON' }).textContent ?? '';
    expect(json).toContain('"mockhub-sync-callout"');
    expect(json).toContain('"url": "https://issuer.test/auth"');
    expect(json).toContain('"forward": true');
  });

  it('adds a webhook that forwards the request or sends a custom body built with dynamic values', async () => {
    // Given
    renderWithShell(<Host />);
    fireEvent.click(screen.getByRole('button', { name: /Add webhook/ }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Webhook 1 URL' }), { target: { value: 'https://partner.test/' } });

    // When
    const group = screen.getByRole('group', { name: 'Webhook 1 payload' });
    await userEvent.click(screen.getAllByRole('button', { name: /Insert in URL/ })[0]);
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search dynamic values' }), 'path variable');
    await userEvent.click(screen.getByRole('button', { name: /Path variable/ }));

    // Then
    expect(screen.getByRole('textbox', { name: 'Webhook 1 URL' })).toHaveValue('https://partner.test/{{originalRequest.path.id}}');
    expect(within(group).getByRole('button', { name: 'Custom request' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('textbox', { name: 'Webhook 1 body' })).toBeInTheDocument();

    // When
    fireEvent.click(within(group).getByRole('button', { name: 'Forward incoming request' }));

    // Then
    expect(screen.queryByRole('textbox', { name: 'Webhook 1 body' })).not.toBeInTheDocument();
    expect(screen.getByText(/sent as they arrived/)).toBeInTheDocument();
  });

  it('inserts a dynamic value into the response body and turns templating on', async () => {
    // Given
    renderWithShell(<Host />);

    // When
    await userEvent.click(screen.getByRole('button', { name: /Insert dynamic value/ }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search dynamic values' }), 'uuid');
    await userEvent.click(screen.getByRole('button', { name: /^UUID\s*{{randomValue/ }));

    // Then
    expect(screen.getByRole('checkbox', { name: /Response templating/ })).toBeChecked();
    expect(screen.getByRole('textbox', { name: 'Response body' }).textContent).toContain("{{randomValue type='UUID'}}");
  });

  it('hands a described webhook to the assistant', async () => {
    // Given
    function Probe() {
      const location = useLocation();
      return <p data-testid="prompt">{(location.state as { prompt?: string } | null)?.prompt}</p>;
    }
    renderWithShell(
      <Routes>
        <Route path="/" element={<StubEditor project={project} moveTargets={[]} stub={{ ...stub, name: 'List' }} isNew={false} canEdit onSaved={() => undefined}
          onDeleted={() => undefined} onDirtyChange={() => undefined} aiEnabled />} />
        <Route path="/projects/p1/assistant" element={<Probe />} />
      </Routes>,
    );

    // When
    await userEvent.type(screen.getByRole('textbox', { name: 'Describe a webhook for the assistant' }), 'POST the id to https://hooks.test');
    await userEvent.click(screen.getByRole('button', { name: 'Ask the assistant' }));

    // Then
    expect(screen.getByTestId('prompt')).toHaveTextContent('For the stub "List" (id s1): POST the id to https://hooks.test');
  });
});
