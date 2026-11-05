export interface ProxyView {
  enabled: boolean;
  allowedNetworks: string[];
  project: { id: string; name: string; basePath: string } | null;
  caReady: boolean;
}

export const CA_PATH = '/__admin/mockhub/api/proxy/ca.pem';

export function proxyAddress(location: { protocol: string; hostname: string; port: string }): string {
  const port = location.port || (location.protocol === 'https:' ? '443' : '80');
  return `http://${location.hostname}:${port}`;
}

export function setupCommands(address: string): { label: string; command: string }[] {
  return [
    { label: 'curl', command: `curl -x ${address} --cacert mockhub-proxy-ca.pem https://api.example.com/users/42` },
    { label: 'Environment', command: `export HTTP_PROXY=${address} HTTPS_PROXY=${address}` },
    { label: 'Java', command: `-Dhttp.proxyHost=${hostOf(address)} -Dhttp.proxyPort=${portOf(address)} -Dhttps.proxyHost=${hostOf(address)} -Dhttps.proxyPort=${portOf(address)}` },
  ];
}

function hostOf(address: string): string {
  return new URL(address).hostname;
}

function portOf(address: string): string {
  return new URL(address).port || '80';
}
