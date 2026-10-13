import { connectSnippet, envName } from './OverviewTab';

describe('connect snippets', () => {
  it('derives an environment variable name from the base path', () => {
    expect(envName('payment-service')).toBe('PAYMENT_SERVICE_URL');
  });

  it('renders a snippet per target', () => {
    const url = 'https://mocks.acme.com/payment-service';
    expect(connectSnippet('env', 'payment-service', url)).toBe(`PAYMENT_SERVICE_URL=${url}`);
    expect(connectSnippet('spring', 'payment-service', url)).toContain(`base-url: ${url}`);
    expect(connectSnippet('compose', 'payment-service', url)).toContain(`PAYMENT_SERVICE_URL: ${url}`);
    expect(connectSnippet('curl', 'payment-service', url)).toBe(`curl -i ${url}/`);
  });
});
