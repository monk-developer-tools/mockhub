export interface TemplateExample {
  title: string;
  template: string;
  method: string;
  url: string;
  headers: string;
  body: string;
}

export const TEMPLATE_EXAMPLES: TemplateExample[] = [
  {
    title: 'Echo parts of the request',
    template: '{\n  "orderId": "{{request.path.[1]}}",\n  "customer": "{{request.query.customer}}",\n  "item": "{{jsonPath request.body \'$.item\'}}",\n  "trace": "{{request.headers.X-Trace-Id}}",\n  "receivedAt": "{{now}}"\n}',
    method: 'POST',
    url: '/orders/42?customer=ada',
    headers: 'Content-Type: application/json\nX-Trace-Id: abc-123',
    body: '{"item": "tea", "quantity": 2}',
  },
  {
    title: 'Fake person',
    template: '{\n  "id": "{{randomValue type=\'UUID\'}}",\n  "name": "{{fake \'Name.fullName\'}}",\n  "email": "{{fake \'Internet.emailAddress\'}}",\n  "city": "{{fake \'Address.city\'}}"\n}',
    method: 'GET',
    url: '/people/1',
    headers: '',
    body: '',
  },
  {
    title: 'A list from a number',
    template: '[\n{{#each (range 1 (jsonPath request.body \'$.count\')) as |i|}}\n  { "line": {{i}} }{{#unless @last}},{{/unless}}\n{{/each}}\n]',
    method: 'POST',
    url: '/lines',
    headers: 'Content-Type: application/json',
    body: '{"count": 3}',
  },
  {
    title: 'Dates',
    template: '{\n  "today": "{{now format=\'yyyy-MM-dd\'}}",\n  "inAWeek": "{{now offset=\'7 days\' format=\'yyyy-MM-dd\'}}",\n  "epoch": {{now format=\'epoch\'}}\n}',
    method: 'GET',
    url: '/dates',
    headers: '',
    body: '',
  },
];

export const headerLines = { example: 'Name: value, one per line' };

export function parseHeaders(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of text.split('\n')) {
    const at = line.indexOf(':');
    if (at <= 0) continue;
    const name = line.slice(0, at).trim();
    if (name) out[name] = line.slice(at + 1).trim();
  }
  return out;
}

export function sampleOf(method: string, url: string, headers: string, body: string) {
  return { method, url: url.trim() || '/', headers: parseHeaders(headers), body };
}

export function urlPathOf(url: string): string {
  const path = url.trim().split('?')[0].split('#')[0];
  return path.startsWith('/') ? path : `/${path}`;
}
