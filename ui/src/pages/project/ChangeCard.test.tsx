import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ProposedChange, Stub } from '../../api/types';
import { ChangeCard, setupLine } from './ChangeCard';

const change = (action: ProposedChange['action'], stub: unknown, summary = 'A change'): ProposedChange => ({
  id: 'c1',
  action,
  stubId: null,
  stub: stub as Stub,
  before: null,
  summary,
});

describe('Staged setup changes', () => {
  it('describes a contract, a gRPC service and a CRUD resource in plain words', () => {
    // When
    const contract = setupLine(change('contract', { title: 'Pet Store', version: '1.0' }));
    const grpc = setupLine(change('grpc', { name: 'cart.proto' }));
    const crud = setupLine(change('crud', { resource: 'customers', idField: 'id', seed: [{}, {}] }));

    // Then
    expect(contract).toBe('Compare traffic with “Pet Store” 1.0');
    expect(grpc).toBe('Serve the services in cart.proto');
    expect(crud).toBe('/customers: list, get, create, update and delete by id, starting with 2 records');
  });

  it('shows the .proto of a staged gRPC service when opened', async () => {
    // Given
    render(
      <ul>
        <ChangeCard change={change('grpc', { name: 'cart.proto', proto: 'service Carts {}' }, 'Serve carts')} basePath="/shop" canApply meta="" onApply={() => {}} onDiscard={() => {}} />
      </ul>,
    );

    // When
    await userEvent.click(screen.getByRole('button', { name: /\.proto/ }));

    // Then
    expect(screen.getByText('gRPC')).toBeInTheDocument();
    expect(screen.getByLabelText('Details')).toHaveTextContent('service Carts {}');
    expect(screen.getByRole('button', { name: 'Apply: Serve carts' })).toBeEnabled();
  });
});
