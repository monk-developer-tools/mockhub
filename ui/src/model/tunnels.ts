export interface TunnelView {
  name: string;
  owner: string;
  url: string;
  online: boolean;
  connectedAt: string;
  lastSeenAt: string;
  requests: number;
  failed: number;
  waiting: number;
  bytesOut: number;
  bytesIn: number;
}

export const TUNNEL_NAME = /^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$/;

export function isTunnelUrl(url: string): boolean {
  return /^tunnel:\/\/[a-z0-9]/.test(url.trim());
}

export function tunnelCommand(server: string, name: string, target: string): string {
  return `java -jar mockhub.jar tunnel --server ${server} --token $MOCKHUB_TOKEN --name ${name || 'laptop'} --to ${target || 'http://localhost:3000'}`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
