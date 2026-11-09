import { recordsToCsv } from './generator';
import { payloadStub } from './starts';
import { parseHeaders, sampleOf, urlPathOf } from './templatePlayground';

describe('template playground and test data helpers', () => {
  it('reads one header per line and skips lines without a name', () => {
    // When
    const headers = parseHeaders('Content-Type: application/json\n: nothing\nX-Trace-Id:  abc:1 \n\nbroken');

    // Then
    expect(headers).toEqual({ 'Content-Type': 'application/json', 'X-Trace-Id': 'abc:1' });
  });

  it('turns the sample URL into a stub path', () => {
    // When / Then
    expect(urlPathOf('/orders/42?customer=ada')).toBe('/orders/42');
    expect(urlPathOf('orders')).toBe('/orders');
    expect(sampleOf('GET', '  ', '', '').url).toBe('/');
  });

  it('keeps a template as a templated answer even when it is not valid JSON yet', () => {
    // When
    const stub = payloadStub({ method: 'GET', path: '/n', status: 200, contentType: 'application/json', body: '{"n": {{request.query.n}}}', templated: true });

    // Then
    expect(stub.response).toMatchObject({ body: '{"n": {{request.query.n}}}', transformers: ['response-template'] });
    expect(() => payloadStub({ method: 'GET', path: '/n', status: 200, contentType: 'application/json', body: '{"n": {{x}}}' })).toThrow('valid JSON');
  });

  it('writes nested records as CSV with dotted columns and quoted cells', () => {
    // When
    const csv = recordsToCsv([{ id: 1, name: 'Ada, Countess', address: { city: 'London' } }, { id: 2, note: 'say "hi"' }]);

    // Then
    expect(csv).toBe('id,name,address.city,note\n1,"Ada, Countess",London,\n2,,,"say ""hi"""\n');
  });
});
