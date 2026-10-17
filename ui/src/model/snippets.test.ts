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

  it('builds a C# snippet with content headers on the body', () => {
    // When
    const code = snippet('csharp', post, 'http://h');

    // Then
    expect(code).toContain('new HttpRequestMessage(new HttpMethod("POST"), "http://h/orders?dry=true")');
    expect(code).toContain(`request.Content = new StringContent(@"{""note"":""it's""}", Encoding.UTF8, "application/json");`);
    expect(code).not.toContain('TryAddWithoutValidation("Content-Type"');
  });

  it('builds an Apex snippet with escaped single quotes', () => {
    // When
    const code = snippet('apex', post, 'http://h');

    // Then
    expect(code).toContain("request.setEndpoint('http://h/orders?dry=true');");
    expect(code).toContain("request.setHeader('Content-Type', 'application/json');");
    expect(code).toContain(`request.setBody('{"note":"it\\'s"}');`);
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
  it('adds the project API key header', () => {
    // Given
    const form = { ...emptyForm('rest'), urlMatch: 'urlPath' as const, url: '/balance' };

    // When
    const sample = sampleRequest(form, { header: 'X-API-Key', value: 's3cret' });

    // Then
    expect(sample.headers).toContainEqual(['X-API-Key', 's3cret']);
  });

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
