export interface CertificateInfo {
  subject: string;
  issuer: string;
  notBefore: string;
  notAfter: string;
  names: string[];
  chainLength?: number;
}

export interface TlsView {
  httpsEnabled: boolean;
  httpsPort: number | null;
  server: CertificateInfo | null;
  clientAuthorities: CertificateInfo[];
}

const DAY = 24 * 60 * 60 * 1000;

export function commonName(dn: string): string {
  const match = /(?:^|,)\s*CN=([^,]+)/.exec(dn);
  return match ? match[1].trim() : dn;
}

export function expiry(notAfter: string, now: Date = new Date()): { text: string; tone: 'ok' | 'warn' | 'danger' } {
  const days = Math.floor((new Date(notAfter).getTime() - now.getTime()) / DAY);
  if (days < 0) return { text: `expired ${-days} day${days === -1 ? '' : 's'} ago`, tone: 'danger' };
  if (days === 0) return { text: 'expires today', tone: 'danger' };
  const text = `expires in ${days} day${days === 1 ? '' : 's'}`;
  return { text, tone: days <= 30 ? 'warn' : 'ok' };
}

export function looksLikePem(text: string): boolean {
  return /-----BEGIN [A-Z0-9 ]+-----/.test(text);
}
