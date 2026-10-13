import type { SampleRequest } from './sampleRequest';

export type SnippetLanguage = 'curl' | 'httpie' | 'fetch' | 'python' | 'java';

export const SNIPPET_LANGUAGES: { id: SnippetLanguage; label: string }[] = [
  { id: 'curl', label: 'cURL' },
  { id: 'httpie', label: 'HTTPie' },
  { id: 'fetch', label: 'JavaScript' },
  { id: 'python', label: 'Python' },
  { id: 'java', label: 'Java' },
];

const hasBody = (r: SampleRequest) => r.body !== '' && r.method !== 'GET' && r.method !== 'HEAD';
const shellQuote = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`;
const jsString = (s: string) => JSON.stringify(s);
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
  }
}
