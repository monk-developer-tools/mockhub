import { emptyForm } from '../../model/stubForm';
import { samplePath } from './TryIt';

describe('samplePath', () => {
  it('fills path template variables and equal query parameters', () => {
    // Given
    const form = {
      ...emptyForm(),
      urlMatch: 'urlPathTemplate' as const,
      url: '/users/{id}/orders/{orderId}',
      query: [
        { key: 'page', operator: 'equalTo' as const, value: '2', caseInsensitive: false },
        { key: 'debug', operator: 'absent' as const, value: '', caseInsensitive: false },
      ],
    };

    // When / Then
    expect(samplePath(form)).toBe('/users/1/orders/1?page=2');
  });

  it('turns regexes into a concrete example', () => {
    expect(samplePath({ ...emptyForm(), urlMatch: 'urlPathPattern', url: '^/files/[0-9]+/.*$' })).toBe('/files/1/sample');
  });

  it('uses the base path for any-URL stubs', () => {
    expect(samplePath({ ...emptyForm(), urlMatch: 'any' })).toBe('/');
  });
});
