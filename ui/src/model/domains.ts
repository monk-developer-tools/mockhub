const LABEL = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/;

export const MAX_DOMAINS = 10;

export function parseDomainLines(text: string): string[] {
  const seen = new Set<string>();
  for (const raw of text.split(/[\s,]+/)) {
    const host = raw.trim().toLowerCase().replace(/\.$/, '');
    if (host) seen.add(host);
  }
  return [...seen];
}

export function domainError(host: string): string | null {
  if (/:\/\/|\/|:|\*/.test(host)) return `${host}: enter only the host name, without scheme, port, path or wildcard`;
  if (host.length > 253 || /^[0-9.]+$/.test(host) || (!host.includes('.') && host !== 'localhost')) return `${host} isn't a host name`;
  if (!host.split('.').every((label) => LABEL.test(label))) return `${host} isn't a host name`;
  return null;
}

export function validateDomains(hosts: string[]): string[] {
  const errors = hosts.map(domainError).filter((e): e is string => e !== null);
  if (hosts.length > MAX_DOMAINS) errors.push(`A project can have at most ${MAX_DOMAINS} domains`);
  return errors;
}

export function domainUrl(host: string, mockUrl: string): string {
  try {
    return `${new URL(mockUrl).protocol}//${host}`;
  } catch {
    return `https://${host}`;
  }
}
