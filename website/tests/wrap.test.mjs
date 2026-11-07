import assert from 'node:assert/strict';
import { test } from 'node:test';
import { forwardExample, forwardRouting, forwardTarget, localTarget, tunnelRouting } from '../assets/js/tools/wrap.js';

const messageOf = (fn) => {
  try {
    fn();
    return '';
  } catch (e) {
    return e.message;
  }
};

test('reads the API to watch, with or without a scheme or a base path', () => {
  // Given
  const inputs = ['https://api.example.com', 'api.example.com/v1/', 'http://legacy.example.com:8080/api'];

  // When
  const targets = inputs.map(forwardTarget);

  // Then
  assert.deepEqual(targets, [
    { target: 'https://api.example.com', pathPrefix: '' },
    { target: 'https://api.example.com', pathPrefix: '/v1' },
    { target: 'http://legacy.example.com:8080', pathPrefix: '/api' },
  ]);
  assert.deepEqual(forwardRouting(targets[1]), { mode: 'forward', target: 'https://api.example.com', pathPrefix: '/v1', insecure: false, requestHeaders: {}, responseHeaders: {} });
  assert.deepEqual(forwardExample('https://hub.example/__public/bins/abc', targets[1]), {
    from: 'https://hub.example/__public/bins/abc/users?page=2',
    to: 'https://api.example.com/v1/users?page=2',
  });
});

test('refuses APIs it cannot watch', () => {
  // Given
  const inputs = ['', 'ftp://files.example.com', 'https://api.example.com/?key=1', 'https://exa mple.com'];

  // When
  const messages = inputs.map((input) => messageOf(() => forwardTarget(input)));

  // Then
  assert.deepEqual(messages, [
    'Enter the API to watch, like https://api.example.com',
    'Only http and https APIs can be watched.',
    'Leave out the query and #fragment; they come from each request.',
    'That is not a URL. Use one like https://api.example.com',
  ]);
});

test('reads a local service from a port, a host or a full address', () => {
  // Given
  const inputs = ['', '8080', 'localhost:5173', 'https://127.0.0.1:8443/app'];

  // When
  const targets = inputs.map(localTarget);

  // Then
  assert.deepEqual(targets, ['http://localhost:3000', 'http://localhost:8080', 'http://localhost:5173', 'https://127.0.0.1:8443']);
  assert.equal(messageOf(() => localTarget('ftp://box:21')), 'The local service must speak http or https.');
  assert.equal(tunnelRouting().mode, 'tunnel');
});
