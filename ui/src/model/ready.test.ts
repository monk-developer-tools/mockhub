import { describe, expect, it } from 'vitest';
import type { Stub } from '../api/types';
import { readyCalls, samplePathOf } from './ready';
import { callbackStub, echoStub, proxyStub, receiverStub } from './starts';

const URL = 'https://mocks.example.com/orders';

describe('ready calls', () => {
  it('turns stubs into commands that call them', () => {
    // Given
    const stubs = [
      { request: { method: 'GET', urlPath: '/orders' } },
      { request: { method: 'GET', urlPathTemplate: '/orders/{orderId}' } },
      { request: { method: 'POST', urlPath: '/orders' } },
      { request: { method: 'POST', urlPath: '/helloworld.Greeter/SayHello' }, metadata: { mockhub: { type: 'grpc' } } },
      { request: { method: 'GET', urlPath: '/never' } },
    ];

    // When
    const calls = readyCalls(stubs as unknown as Stub[], URL);

    // Then
    expect(calls.map((c) => c.command)).toEqual([
      'curl -i https://mocks.example.com/orders/orders',
      'curl -i https://mocks.example.com/orders/orders/42',
      `curl -i -X POST https://mocks.example.com/orders/orders -H 'Content-Type: application/json' -d '{}'`,
      "grpcurl -d '{}' mocks.example.com:443 helloworld.Greeter/SayHello",
    ]);
    expect(calls[0].url).toBe('https://mocks.example.com/orders/orders');
    expect(calls[2].url).toBeUndefined();
  });

  it('has a command for webhooks, a proxy, an echo and GraphQL', () => {
    // When
    const calls = readyCalls([
      receiverStub('/hooks'),
      callbackStub({ path: '/payments', status: 202, callbackUrl: 'https://my.app/cb', delaySeconds: 1 }),
      proxyStub('https://api.example.com'),
      echoStub('/echo'),
      { request: { method: 'POST', urlPath: '/graphql' }, response: { transformers: ['mockhub-graphql'] } },
    ], URL, 10);

    // Then
    expect(calls.map((c) => c.label)).toEqual(['POST /hooks/test', 'POST /payments', 'Any call, forwarded to https://api.example.com', 'GET /echo/test', 'GraphQL /graphql']);
    expect(samplePathOf({ urlPathPattern: '/a(b|c)' })).toBeNull();
  });
});
