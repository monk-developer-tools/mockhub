import { describeRequest, templateError } from './templateError';

describe('templateError', () => {
  it('reads the Handlebars message out of the server error page', () => {
    // Given
    const page = `<html>\n<head>\n<meta http-equiv="Content-Type" content="text/html;charset=ISO-8859-1"/>\n<title>Error 500 com.google.common.util.concurrent.UncheckedExecutionException: com.github.jknack.handlebars.HandlebarsException: inline@1c2a51aa:1:30: found: &apos;}}}&apos;, expected: &apos;}}&apos;\n{&quot;id&quot;: {{jsonPath request.body &apos;$.id&apos;}}}\n                             ^\n</title>\n</head>`;

    // When
    const message = templateError(500, page);

    // Then
    expect(message).toBe(`line 1:30: found: '}}}', expected: '}}'\n{"id": {{jsonPath request.body '$.id'}}}\n                             ^`);
  });

  it('names a helper that does not exist and ignores answers that are not template errors', () => {
    // Given
    const unknown = '<title>Error 500 x: com.github.jknack.handlebars.HandlebarsException: inline@c82f8b9:1:2: could not find helper: &apos;nope&apos;</title>';

    // When / Then
    expect(templateError(500, unknown)).toBe("line 1:2: could not find helper: 'nope'");
    expect(templateError(500, '{"error": "Simulated failure"}')).toBeNull();
    expect(templateError(200, 'HandlebarsException in a body')).toBeNull();
  });
});

describe('describeRequest', () => {
  it('writes the request the template answered, without a body for GET', () => {
    // Given
    // When / Then
    expect(describeRequest('POST', '/orders/orders', 'Content-Type: application/json', '{"id": 7}')).toBe('POST /orders/orders\nContent-Type: application/json\n\n{"id": 7}');
    expect(describeRequest('GET', '/orders/1', '', 'ignored')).toBe('GET /orders/1');
  });
});
