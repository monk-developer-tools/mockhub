import { render, screen } from '@testing-library/react';
import { StubRoute } from './StubRoute';

describe('StubRoute', () => {
  it('shows method, path and status', () => {
    // When
    render(<StubRoute stub={{ request: { method: 'POST', urlPath: '/orders' }, response: { status: 201 } }} />);

    // Then
    expect(screen.getByText('POST')).toHaveClass('jack', 'POST');
    expect(screen.getByText('/orders')).toBeInTheDocument();
    expect(screen.getByText('201')).toHaveClass('status-plug', 's2');
  });

  it('marks regex paths, faults and proxies', () => {
    // When
    const { rerender } = render(<StubRoute stub={{ request: { method: 'GET', urlPathPattern: '/a/.*' }, response: { fault: 'EMPTY_RESPONSE' } }} />);

    // Then
    expect(screen.getByText('/a/.*')).toHaveClass('pattern');
    expect(screen.getByText('fault')).toBeInTheDocument();

    // When
    rerender(<StubRoute stub={{ request: { method: 'ANY' }, response: { proxyBaseUrl: 'https://x' } }} />);

    // Then
    expect(screen.getByText('*')).toBeInTheDocument();
    expect(screen.getByText('proxy')).toBeInTheDocument();
  });

  it('shows gRPC stubs with their method path and gRPC status', () => {
    // When
    render(<StubRoute basePath="/shop" stub={{
      request: { method: 'POST', urlPath: '/library.v1.BookService/GetBook' },
      response: { status: 200, headers: { 'grpc-status-name': 'NOT_FOUND' } },
      metadata: { mockhub: { type: 'grpc' } },
    }} />);

    // Then
    expect(screen.getByText('gRPC')).toHaveClass('jack', 'gRPC');
    expect(screen.getByText('/library.v1.BookService/GetBook')).toHaveAttribute('title', '/library.v1.BookService/GetBook');
    expect(screen.getByText('NOT_FOUND')).toHaveClass('status-plug', 's4');
  });
});
