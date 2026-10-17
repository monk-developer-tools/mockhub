import type { SampleRequest } from './sampleRequest';

export type SnippetLanguage = 'curl' | 'httpie' | 'fetch' | 'python' | 'java' | 'csharp' | 'apex';

export const SNIPPET_LANGUAGES: { id: SnippetLanguage; label: string }[] = [
  { id: 'curl', label: 'cURL' },
  { id: 'httpie', label: 'HTTPie' },
  { id: 'fetch', label: 'JavaScript' },
  { id: 'python', label: 'Python' },
  { id: 'java', label: 'Java' },
  { id: 'csharp', label: 'C#' },
  { id: 'apex', label: 'Apex' },
];

const hasBody = (r: SampleRequest) => r.body !== '' && r.method !== 'GET' && r.method !== 'HEAD';
const shellQuote = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`;
const jsString = (s: string) => JSON.stringify(s);
const csharpText = (s: string) => `@"${s.replace(/"/g, '""')}"`;
const apexString = (s: string) => `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')}'`;
const isContentHeader = (name: string) => ['content-type', 'content-length', 'content-encoding', 'content-language'].includes(name.toLowerCase());
const javaText = (s: string) => `"""\n${s.replace(/\\/g, '\\\\').replace(/"""/g, '\\"""')}"""`;

export function snippet(language: SnippetLanguage, request: SampleRequest, baseUrl: string): string {
  const url = baseUrl + request.path;
  switch (language) {
    case 'curl':
      return [
        `curl -X ${request.method} ${shellQuote(url)}`,
        ...request.headers.map(([k, v]) => `  -H ${shellQuote(`${k}: ${v}`)}`),
        ...(hasBody(request) ? [`  --data-raw ${shellQuote(request.body)}`] : []),
      ].join(' \\\n');
    case 'httpie': {
      const parts = [`http ${request.method} ${shellQuote(url)}`, ...request.headers.map(([k, v]) => shellQuote(`${k}:${v}`))];
      const command = parts.join(' \\\n  ');
      return hasBody(request) ? `printf %s ${shellQuote(request.body)} | ${command}` : command;
    }
    case 'fetch': {
      const options = [`  method: ${jsString(request.method)}`];
      if (request.headers.length) {
        options.push(`  headers: {\n${request.headers.map(([k, v]) => `    ${jsString(k)}: ${jsString(v)}`).join(',\n')}\n  }`);
      }
      if (hasBody(request)) options.push(`  body: ${jsString(request.body)}`);
      return `const response = await fetch(${jsString(url)}, {\n${options.join(',\n')}\n});\nconsole.log(response.status, await response.text());`;
    }
    case 'python': {
      const lines = ['import requests', '', `response = requests.request(`, `    ${jsString(request.method)},`, `    ${jsString(url)},`];
      if (request.headers.length) lines.push(`    headers={${request.headers.map(([k, v]) => `${jsString(k)}: ${jsString(v)}`).join(', ')}},`);
      if (hasBody(request)) lines.push(`    data=${jsString(request.body)},`);
      lines.push(')', 'print(response.status_code, response.text)');
      return lines.join('\n');
    }
    case 'java': {
      const publisher = hasBody(request) ? `HttpRequest.BodyPublishers.ofString(${javaText(request.body)})` : 'HttpRequest.BodyPublishers.noBody()';
      return [
        'HttpRequest request = HttpRequest.newBuilder(URI.create(' + jsString(url) + '))',
        ...request.headers.map(([k, v]) => `    .header(${jsString(k)}, ${jsString(v)})`),
        `    .method(${jsString(request.method)}, ${publisher})`,
        '    .build();',
        'HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());',
        'System.out.println(response.statusCode() + " " + response.body());',
      ].join('\n');
    }
    case 'csharp': {
      const contentType = request.headers.find(([k]) => k.toLowerCase() === 'content-type')?.[1] ?? 'text/plain';
      const mediaType = contentType.split(';')[0].trim();
      const lines = [
        'using var client = new HttpClient();',
        `using var request = new HttpRequestMessage(new HttpMethod(${jsString(request.method)}), ${jsString(url)});`,
        ...request.headers.filter(([k]) => !isContentHeader(k)).map(([k, v]) => `request.Headers.TryAddWithoutValidation(${jsString(k)}, ${jsString(v)});`),
      ];
      if (hasBody(request)) lines.push(`request.Content = new StringContent(${csharpText(request.body)}, Encoding.UTF8, ${jsString(mediaType)});`);
      lines.push(
        'using var response = await client.SendAsync(request);',
        'Console.WriteLine($"{(int)response.StatusCode} {await response.Content.ReadAsStringAsync()}");',
      );
      return lines.join('\n');
    }
    case 'apex': {
      const lines = [
        'HttpRequest request = new HttpRequest();',
        `request.setEndpoint(${apexString(url)});`,
        `request.setMethod(${apexString(request.method)});`,
        ...request.headers.map(([k, v]) => `request.setHeader(${apexString(k)}, ${apexString(v)});`),
      ];
      if (hasBody(request)) lines.push(`request.setBody(${apexString(request.body)});`);
      lines.push(
        'HttpResponse response = new Http().send(request);',
        "System.debug(response.getStatusCode() + ' ' + response.getBody());",
      );
      return lines.join('\n');
    }
  }
}
