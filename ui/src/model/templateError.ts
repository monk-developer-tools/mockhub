const ENTITIES: Record<string, string> = { '&apos;': "'", '&quot;': '"', '&lt;': '<', '&gt;': '>', '&amp;': '&', '&#39;': "'" };

const decode = (text: string) => text.replace(/&(apos|quot|lt|gt|amp|#39);/g, (m) => ENTITIES[m] ?? m);

export function templateError(status: number, body: string): string | null {
  if (status !== 500 || !/HandlebarsException/.test(body)) return null;
  const title = /<title>([\s\S]*?)<\/title>/i.exec(body)?.[1] ?? body;
  const text = decode(title).replace(/^[\s\S]*?HandlebarsException:\s*/, '').replace(/^inline@[0-9a-f]+:/, 'line ');
  const lines = text.split('\n').map((l) => l.replace(/\s+$/, '')).filter((l) => l.trim());
  return lines.join('\n').trim() || 'The template could not be rendered.';
}

export function describeRequest(method: string, path: string, headers: string, body: string): string {
  const head = headers.trim() ? `\n${headers.trim()}` : '';
  return `${method} ${path}${head}${body.trim() && method !== 'GET' && method !== 'HEAD' ? `\n\n${body}` : ''}`;
}
