import { describe, expect, it } from 'vitest';
import { domainUrl, parseDomainLines, validateDomains } from './domains';

describe('custom domains', () => {
  it('reads one host per line, lowercased, without a trailing dot or repeats', () => {
    // Given
    const text = ' Payments.Mocks.Example.com.\n\npayments.mocks.example.com, api.test ';

    // When
    const hosts = parseDomainLines(text);

    // Then
    expect(hosts).toEqual(['payments.mocks.example.com', 'api.test']);
  });

  it('accepts host names and explains what is wrong with the rest', () => {
    // When
    const errors = validateDomains(['payments.example.com', 'localhost', 'https://x.example.com', 'x.example.com:8443', '*.example.com', '10.0.0.1', 'intranet', '-bad.example.com']);

    // Then
    expect(errors).toEqual([
      'https://x.example.com: enter only the host name, without scheme, port, path or wildcard',
      'x.example.com:8443: enter only the host name, without scheme, port, path or wildcard',
      '*.example.com: enter only the host name, without scheme, port, path or wildcard',
      "10.0.0.1 isn't a host name",
      "intranet isn't a host name",
      "-bad.example.com isn't a host name",
    ]);
  });

  it('limits a project to ten domains', () => {
    // Given
    const hosts = Array.from({ length: 11 }, (_, i) => `h${i}.example.com`);

    // When / Then
    expect(validateDomains(hosts)).toEqual(['A project can have at most 10 domains']);
  });

  it('builds the URL with the scheme of the mock URL', () => {
    // When / Then
    expect(domainUrl('payments.example.com', 'https://mocks.example.com/payments')).toBe('https://payments.example.com');
    expect(domainUrl('payments.test', 'http://localhost:8080/payments')).toBe('http://payments.test');
  });
});
