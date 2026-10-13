import { emptyForm } from './stubForm';
import { sampleRequest, samplePath } from './sampleRequest';
import { snippet } from './snippets';

const post = {
  method: 'POST',
  path: '/orders?dry=true',
  headers: [['Content-Type', 'application/json']] as [string, string][],
  body: `{"note":"it's"}`,
};

describe('snippets', () => {
  it('builds a cURL command with escaped quotes', () => {
    expect(snippet('curl', post, 'https://mocks.acme.com/shop')).toBe(
      `curl -X POST 'https://mocks.acme.com/shop/orders?dry=true' \\\n  -H 'Content-Type: application/json' \\\n  --data-raw '{"note":"it'\\''s"}'`,
    );
  });

  it('builds fetch, Python, Java and HTTPie snippets', () => {
    expect(snippet('fetch', post, 'http://h')).toContain('body: "{\\"note\\":\\"it\'s\\"}"');
    expect(snippet('python', post, 'http://h')).toContain('headers={"Content-Type": "application/json"},');
    expect(snippet('java', post, 'http://h')).toContain('.method("POST", HttpRequest.BodyPublishers.ofString("""');
    expect(snippet('httpie', post, 'http://h')).toMatch(/^printf %s .* \| http POST 'http:\/\/h\/orders\?dry=true'/);
  });

  it('omits the body for GET requests', () => {
    // Given
    const get = { ...post, method: 'GET' };

    // When / Then
    expect(snippet('curl', get, 'http://h')).not.toContain('--data-raw');
    expect(snippet('java', get, 'http://h')).toContain('noBody()');
  });
});

describe('sampleRequest', () => {
  it('derives method, headers and body from a SOAP form', () => {
    // Given
    const form = { ...emptyForm('soap'), urlMatch: 'urlPath' as const, url: '/ws', soapAction: 'urn:Get', sampleBody: '<x/>' };

    // When
    const request = sampleRequest(form);

    // Then
    expect(request).toEqual({
      method: 'POST',
      path: '/ws',
      headers: [['Content-Type', 'text/xml; charset=utf-8'], ['SOAPAction', '"urn:Get"']],
      body: '<x/>',
    });
  });

  it('fills path templates', () => {
    expect(samplePath({ ...emptyForm(), urlMatch: 'urlPathTemplate', url: '/a/{id}' })).toBe('/a/1');
  });
});
