export const COMMON_SECRET_HEADERS = ['authorization', 'proxy-authorization', 'cookie', 'set-cookie', 'x-api-key', 'x-auth-token'];

const HEADER = /^[!#$%&'*+.^_`|~0-9a-z-]{1,100}$/;

export function parseHeaderNames(text: string): { names: string[]; invalid: string[] } {
  const names: string[] = [];
  const invalid: string[] = [];
  for (const raw of text.split(/[\n,]/)) {
    const name = raw.trim().toLowerCase();
    if (!name) continue;
    if (!HEADER.test(name)) invalid.push(raw.trim());
    else if (!names.includes(name)) names.push(name);
  }
  return { names, invalid };
}

export function withCommonSecrets(text: string): string {
  const { names } = parseHeaderNames(text);
  return [...names, ...COMMON_SECRET_HEADERS.filter((h) => !names.includes(h))].join('\n');
}
