import assert from 'node:assert/strict';
import { test } from 'node:test';
import { binLink, opsFor, duplicate, flow, forgetBin, rememberBin, renameBin, routeSummary, parseBinHash, previewRequest, headerLines, mergeRequests, move, outcomeText, outcomeTone, parseHeaderLines, recipe, routingFromForm, snippets, summary, tunnelCommand } from '../assets/js/tools/bin.js';

test('summarises a rule in one line', () => {
  // Given
  const rule = recipe('event', 'invoice.paid');

  // When
  const line = summary(rule);

  // Then
  assert.equal(line, 'POST $.type equals invoice.paid → 202');
});

test('summarises weighted, delayed and catch-all rules', () => {
  // Given
  const flaky = recipe('flaky');
  const slow = recipe('slow');
  const header = { method: 'ANY', conditions: [{ target: 'header', key: 'X-Event', op: 'exists', value: '' }], answers: [{ status: 204, delayMs: 0, weight: 1 }] };

  // When
  const lines = [summary(flaky), summary(slow), summary(header)];

  // Then
  assert.deepEqual(lines, ['any request → 200 (3) or 503 (1)', 'any request → 200 after 5s', 'header X-Event is present → 204']);
});

test('starting points carry templates only where they use helpers', () => {
  // Given
  const ids = ['fail', 'slow', 'event', 'echo', 'flaky'];

  // When
  const rules = ids.map((id) => recipe(id));

  // Then
  assert.deepEqual(rules.map((r) => r.answers.some((a) => a.template)), [false, false, true, true, false]);
  assert.equal(rules[0].answers[0].status, 500);
  assert.equal(rules[1].answers[0].delayMs, 5000);
});

test('reorders and duplicates rules without touching the original list', () => {
  // Given
  const rules = [{ id: 'a', name: 'A' }, { id: 'b', name: 'B' }, { id: 'c', name: 'C' }];

  // When
  const moved = move(rules, 2, 0);
  const copied = duplicate(rules, 0);

  // Then
  assert.deepEqual(moved.map((r) => r.id), ['c', 'a', 'b']);
  assert.deepEqual(copied.map((r) => r.name), ['A', 'A (copy)', 'B', 'C']);
  assert.equal(copied[1].id, undefined);
  assert.deepEqual(rules.map((r) => r.id), ['a', 'b', 'c']);
  assert.deepEqual(move(rules, 0, 9).map((r) => r.id), ['a', 'b', 'c']);
});

test('reads and writes headers as Name: value lines', () => {
  // Given
  const text = 'X-Signature: abc:def\n  bad line\nContent-Type: application/json\n: nameless';

  // When
  const headers = parseHeaderLines(text);

  // Then
  assert.deepEqual(headers, { 'X-Signature': 'abc:def', 'Content-Type': 'application/json' });
  assert.equal(headerLines(headers), 'X-Signature: abc:def\nContent-Type: application/json');
});

test('describes how each request was answered', () => {
  // Given
  const rule = { by: 'rule', rule: 'Fail with 500', status: 500, millis: 3 };
  const forwarded = { by: 'forward', rule: 'api.example.com', status: 201, millis: 120 };
  const failed = { by: 'tunnel-error', rule: '', status: 502, millis: 1 };
  const plain = { by: 'default', rule: '', status: 404, millis: 0 };

  // When
  const texts = [rule, forwarded, failed, plain].map(outcomeText);
  const tones = [rule, forwarded, failed, plain].map(outcomeTone);

  // Then
  assert.deepEqual(texts, ['500 · Rule: Fail with 500 · 3 ms', '201 · Forwarded: api.example.com · 120 ms', '502 · Tunnel failed · 1 ms', '404 · Default answer · 0 ms']);
  assert.deepEqual(tones, ['bad', 'ok', 'bad', 'warn']);
});

test('merges new and older pages newest first without repeats', () => {
  // Given
  const shown = [{ seq: 5 }, { seq: 4 }];

  // When
  const merged = mergeRequests(shown, [{ seq: 6 }, { seq: 4, again: true }, { seq: 1 }]);

  // Then
  assert.deepEqual(merged.map((r) => r.seq), [6, 5, 4, 1]);
  assert.equal(merged[2].again, true);
});

test('shows the path a webhook takes for each routing mode', () => {
  // Given
  const forward = { mode: 'forward', target: 'https://api.example.com', pathPrefix: '/hooks' };

  // When
  const paths = [flow(forward, true, 2), flow({ mode: 'tunnel' }, false, 2), flow({ mode: 'mock' }, true, 0)];

  // Then
  assert.deepEqual(paths, [
    ['Sender', 'Your bin', '2 rules', 'api.example.com/hooks'],
    ['Sender', 'Your bin', 'Your machine'],
    ['Sender', 'Your bin', 'Default answer'],
  ]);
});

test('builds routing from the form and code samples for the bin URL', () => {
  // Given
  const form = { mode: 'forward', target: ' https://api.example.com ', pathPrefix: '', insecure: 'on', requestHeaders: 'Authorization: Bearer x', responseHeaders: '' };

  // When
  const routing = routingFromForm(form);
  const code = snippets('https://demo.test/__public/bins/abc');

  // Then
  assert.deepEqual(routing, { mode: 'forward', target: 'https://api.example.com', pathPrefix: '', insecure: true, requestHeaders: { Authorization: 'Bearer x' }, responseHeaders: {} });
  assert.deepEqual(Object.keys(code), ['curl', 'js', 'python', 'java']);
  assert.ok(Object.values(code).every((c) => c.includes('https://demo.test/__public/bins/abc/payments')));
  assert.equal(tunnelCommand('https://demo.test', 'abc', 'tok'), 'java -jar mockhub.jar tunnel --server https://demo.test --token bin_abc_tok --name bin-abc --to http://localhost:3000');
});

test('a bin link carries the bin key and reads back', () => {
  // Given
  const link = binLink('https://demo.test', '/tools/webhook-bin/', 'abcdefghijk2', 'a'.repeat(32));

  // When
  const parsed = parseBinHash(new URL(link).hash);

  // Then
  assert.equal(link, `https://demo.test/tools/webhook-bin/#bin=abcdefghijk2.${'a'.repeat(32)}`);
  assert.deepEqual(parsed, { id: 'abcdefghijk2', token: 'a'.repeat(32), viewOnly: false });
  assert.equal(parseBinHash('#bin=short.key'), null);
  assert.equal(parseBinHash(''), null);
});

test('previews against the latest request, or a sample when there is none', () => {
  // Given
  const latest = { method: 'PUT', path: '/hooks', query: 'a=1', headers: { 'X-Event': 'push' }, body: '{"id": 9}' };

  // When
  const fromLatest = previewRequest(latest);
  const fromSample = previewRequest(null);

  // Then
  assert.deepEqual(fromLatest, { method: 'PUT', url: '/hooks?a=1', headers: { 'X-Event': 'push' }, body: '{"id": 9}' });
  assert.equal(fromSample.url, '/orders?currency=EUR');
  assert.ok(fromSample.body.includes('ord_1'));
});

test('the dynamic starting point fills its answer from the request', () => {
  // Given
  const rule = recipe('dynamic');

  // When
  const answer = rule.answers[0];

  // Then
  assert.equal(answer.template, true);
  assert.ok(answer.body.includes("{{jsonPath request.body '$.id'}}"));
  assert.equal(answer.headers['X-Request-Id'], "{{randomValue type='UUID'}}");
});

test('a view-only link reads back as view-only', () => {
  // Given
  const link = binLink('https://demo.test', '/tools/webhook-bin/', 'abcdefghijk2', `v${'b'.repeat(31)}`, true);

  // When
  const parsed = parseBinHash(new URL(link).hash);

  // Then
  assert.equal(link, `https://demo.test/tools/webhook-bin/#view=abcdefghijk2.v${'b'.repeat(31)}`);
  assert.deepEqual(parsed, { id: 'abcdefghijk2', token: `v${'b'.repeat(31)}`, viewOnly: true });
});

test('sums up where a request goes in one line', () => {
  // Given
  const forward = { mode: 'forward', target: 'https://api.example.com', pathPrefix: '/hooks' };

  // When
  const lines = [routeSummary(forward, true, 2, false), routeSummary({ mode: 'tunnel' }, false, 2, true), routeSummary({ mode: 'mock' }, true, 0, false), routeSummary({ mode: 'tunnel' }, true, 1, false)];

  // Then
  assert.deepEqual(lines, ['2 rules, then api.example.com/hooks', 'your machine (tunnel connected)', 'default answer', '1 rule, then your machine (tunnel not connected)']);
});

test('remembers each bin once with a name of its own', () => {
  // Given
  const first = rememberBin([], { id: 'aaaaaaaaaaa1', token: 't1' }, 1);
  const two = rememberBin(first, { id: 'bbbbbbbbbbb2', token: 't2' }, 2);

  // When
  const again = rememberBin(two, { id: 'aaaaaaaaaaa1', token: 't1' }, 3);
  const afterRemoval = rememberBin(forgetBin(again, 'aaaaaaaaaaa1'), { id: 'ccccccccccc3', token: 't3' }, 4);

  // Then
  assert.deepEqual(again.map((b) => b.name), ['Bin 1', 'Bin 2']);
  assert.deepEqual(afterRemoval.map((b) => [b.id, b.name]), [['bbbbbbbbbbb2', 'Bin 2'], ['ccccccccccc3', 'Bin 3']]);
});

test('renames a bin but never to an empty name', () => {
  // Given
  const list = rememberBin([], { id: 'aaaaaaaaaaa1', token: 't1' }, 1);

  // When
  const renamed = renameBin(list, 'aaaaaaaaaaa1', '  Stripe staging  ');
  const blank = renameBin(renamed, 'aaaaaaaaaaa1', '   ');

  // Then
  assert.equal(renamed[0].name, 'Stripe staging');
  assert.equal(blank[0].name, 'Stripe staging');
  assert.equal(renameBin(list, 'aaaaaaaaaaa1', 'x'.repeat(60))[0].name.length, 40);
});

test('summarises the new operators, leaving out the value where none is needed', () => {
  // Given
  const rule = {
    method: 'QUERY',
    conditions: [
      { target: 'path', op: 'template', value: '/users/:id' },
      { target: 'header', key: 'X-Debug', op: 'absent', value: '' },
      { target: 'form', key: 'status', op: 'not_equals', value: 'draft' },
      { target: 'soap_action', op: 'not_contains', value: 'Delete' },
    ],
    answers: [{ status: 200, delayMs: 0, weight: 1 }],
  };

  // When
  const line = summary(rule);

  // Then
  assert.equal(line, "QUERY path matches pattern /users/:id and header X-Debug is absent and form field status doesn't equal draft and soap action doesn't contain Delete → 200");
});

test('offers a path pattern only when the rule looks at the path', () => {
  // Given
  const pattern = (target) => opsFor(target).some((o) => o.value === 'template');

  // When
  const onPath = pattern('path');
  const onHeader = pattern('header');

  // Then
  assert.equal(onPath, true);
  assert.equal(onHeader, false);
  assert.ok(opsFor('xml').some((o) => o.value === 'absent'));
});
