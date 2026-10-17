import { fireEvent, screen } from '@testing-library/react';
import { useState } from 'react';
import type { Project, Stub } from '../../api/types';
import { renderWithShell } from '../../test/render';
import { StubEditor } from './StubEditor';

const project = { id: 'p1', name: 'Shop', slug: 'shop', basePath: '/shop', mockUrl: 'http://x/shop', color: '#000', stubCount: 1, soapStubCount: 0, requestCount: 0, unmatchedCount: 0, activity: [] } as unknown as Project;
const stub: Stub = { id: 's1', request: { method: 'GET', urlPath: '/a' }, response: { status: 200, jsonBody: { ok: true } } };

function Host({ canEdit = true }: { canEdit?: boolean }) {
  const [, setDirty] = useState(false);
  return <StubEditor project={project} projects={[project]} stub={stub} isNew={false} canEdit={canEdit} onSaved={() => undefined} onDeleted={() => undefined} onDirtyChange={setDirty} />;
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
    renderWithShell(<StubEditor project={project} projects={[project]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} usage={usage} />);

    // Then
    expect(screen.getByRole('switch', { name: 'Stub is on' })).toBeChecked();
    expect(screen.getByText('3 calls in the request log, last just now')).toBeInTheDocument();
  });

  it('marks a disabled stub as off', () => {
    // Given
    const off: Stub = { ...stub, metadata: { mockhub: { disabled: true } } };

    // When
    renderWithShell(<StubEditor project={project} projects={[project]} stub={off} isNew={false} canEdit onSaved={() => undefined}
      onDeleted={() => undefined} onDirtyChange={() => undefined} />);

    // Then
    expect(screen.getByRole('switch', { name: 'Stub is on' })).not.toBeChecked();
    expect(screen.getByText(/Requests skip this stub/)).toBeInTheDocument();
  });

  it('describes a failure rate and offers AI only when it is on', () => {
    // Given
    renderWithShell(<StubEditor project={project} projects={[project]} stub={stub} isNew={false} canEdit onSaved={() => undefined}
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
});
