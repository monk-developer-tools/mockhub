import { describe, expect, it } from 'vitest';
import { proxyAddress, setupCommands } from './proxy';

describe('forward proxy setup', () => {
  it('uses the host and port the UI was opened on', () => {
    // When / Then
    expect(proxyAddress({ protocol: 'http:', hostname: 'mocks.local', port: '8080' })).toBe('http://mocks.local:8080');
    expect(proxyAddress({ protocol: 'http:', hostname: 'mocks.local', port: '' })).toBe('http://mocks.local:80');
  });

  it('gives curl, environment and Java settings', () => {
    // When
    const commands = setupCommands('http://mocks.local:8080');

    // Then
    expect(commands.map((c) => c.command)).toEqual([
      'curl -x http://mocks.local:8080 --cacert mockhub-proxy-ca.pem https://api.example.com/users/42',
      'export HTTP_PROXY=http://mocks.local:8080 HTTPS_PROXY=http://mocks.local:8080',
      '-Dhttp.proxyHost=mocks.local -Dhttp.proxyPort=8080 -Dhttps.proxyHost=mocks.local -Dhttps.proxyPort=8080',
    ]);
  });
});
