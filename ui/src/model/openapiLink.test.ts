import { describe, expect, it } from 'vitest';
import { badgeHtml, badgeMarkdown, isHttpUrl, mockLink, specFromSearch } from './openapiLink';

describe('Mock these APIs link', () => {
  it('reads the OpenAPI URL from the query string', () => {
    // When / Then
    expect(specFromSearch('?openapi=https%3A%2F%2Fapi.example.com%2Fopenapi.yaml')).toBe('https://api.example.com/openapi.yaml');
    expect(specFromSearch('?other=1')).toBe('');
  });

  it('accepts only http and https URLs', () => {
    // When / Then
    expect(isHttpUrl('https://api.example.com/spec.json')).toBe(true);
    expect(isHttpUrl('ftp://api.example.com/spec.json')).toBe(false);
    expect(isHttpUrl('openapi.yaml')).toBe(false);
  });

  it('builds the link, the Markdown badge and the HTML badge', () => {
    // Given
    const origin = 'https://mocks.example.com';
    const spec = 'https://api.example.com/openapi.yaml?v=2&x=1';

    // When
    const link = mockLink(origin, spec);

    // Then
    expect(link).toBe('https://mocks.example.com/__admin/mockhub/ui/mock-these-apis?openapi=https%3A%2F%2Fapi.example.com%2Fopenapi.yaml%3Fv%3D2%26x%3D1');
    expect(badgeMarkdown(origin, spec)).toBe(`[![Mock these APIs](https://mocks.example.com/__admin/mockhub/ui/mock-these-apis.svg)](${link})`);
    expect(badgeHtml(origin, spec)).toBe(`<a href="${link}"><img src="https://mocks.example.com/__admin/mockhub/ui/mock-these-apis.svg" alt="Mock these APIs" height="28"></a>`);
  });
});
