import { TUNNEL_NAME, formatBytes, isTunnelUrl, tunnelCommand } from './tunnels';

describe('tunnels', () => {
  it('recognises tunnel URLs', () => {
    // Given / When / Then
    expect(isTunnelUrl('tunnel://laptop/api')).toBe(true);
    expect(isTunnelUrl(' tunnel://laptop')).toBe(true);
    expect(isTunnelUrl('tunnel://')).toBe(false);
    expect(isTunnelUrl('https://laptop')).toBe(false);
  });

  it('accepts the same names as the server', () => {
    // Given / When / Then
    expect(TUNNEL_NAME.test('alice-api')).toBe(true);
    expect(TUNNEL_NAME.test('Laptop')).toBe(false);
    expect(TUNNEL_NAME.test('-edge')).toBe(false);
  });

  it('builds the command that opens a tunnel', () => {
    // Given
    const server = 'https://mock.example.com';

    // When
    const command = tunnelCommand(server, 'desk', 'http://localhost:8081');

    // Then
    expect(command).toBe('java -jar mockhub.jar tunnel --server https://mock.example.com --token $MOCKHUB_TOKEN --name desk --to http://localhost:8081');
    expect(tunnelCommand(server, '', '')).toContain('--name laptop --to http://localhost:3000');
  });

  it('formats traffic sizes', () => {
    // Given / When / Then
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB');
  });
});
