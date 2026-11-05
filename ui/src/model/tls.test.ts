import { describe, expect, it } from 'vitest';
import { commonName, expiry, looksLikePem } from './tls';

describe('tls', () => {
  it('shows the common name of a distinguished name', () => {
    // Then
    expect(commonName('O=Acme,CN=billing-service')).toBe('billing-service');
    expect(commonName('CN=localhost')).toBe('localhost');
    expect(commonName('O=Acme')).toBe('O=Acme');
  });

  it('describes how long a certificate has left', () => {
    // Given
    const now = new Date('2026-11-05T12:00:00Z');

    // Then
    expect(expiry('2027-11-05T12:00:00Z', now)).toEqual({ text: 'expires in 365 days', tone: 'ok' });
    expect(expiry('2026-11-20T12:00:00Z', now)).toEqual({ text: 'expires in 15 days', tone: 'warn' });
    expect(expiry('2026-11-05T18:00:00Z', now)).toEqual({ text: 'expires today', tone: 'danger' });
    expect(expiry('2026-11-01T12:00:00Z', now)).toEqual({ text: 'expired 4 days ago', tone: 'danger' });
  });

  it('recognises PEM text', () => {
    // Then
    expect(looksLikePem('-----BEGIN CERTIFICATE-----\nMII')).toBe(true);
    expect(looksLikePem('hello')).toBe(false);
  });
});
