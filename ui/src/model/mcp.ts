export type McpClientId = 'claude-code' | 'cursor' | 'codex' | 'claude-desktop' | 'curl';

export const MCP_CLIENTS: { id: McpClientId; label: string; file?: string }[] = [
  { id: 'claude-code', label: 'Claude Code' },
  { id: 'cursor', label: 'Cursor', file: '.cursor/mcp.json' },
  { id: 'codex', label: 'Codex', file: '~/.codex/config.toml' },
  { id: 'claude-desktop', label: 'Claude Desktop', file: 'claude_desktop_config.json' },
  { id: 'curl', label: 'API (curl)' },
];

export function mcpUrl(origin: string): string {
  return `${origin.replace(/\/$/, '')}/__admin/mockhub/mcp`;
}

export function mcpSnippet(client: McpClientId, origin: string, token: string): string {
  const url = mcpUrl(origin);
  switch (client) {
    case 'claude-code':
      return `claude mcp add --transport http mockhub ${url} \\\n  --header "Authorization: Bearer ${token}"`;
    case 'cursor':
      return JSON.stringify({ mcpServers: { mockhub: { url, headers: { Authorization: `Bearer ${token}` } } } }, null, 2);
    case 'codex':
      return `[mcp_servers.mockhub]\nurl = "${url}"\nbearer_token_env_var = "MOCKHUB_TOKEN"\n\n# then, in your shell:\n# export MOCKHUB_TOKEN=${token}`;
    case 'claude-desktop':
      return JSON.stringify(
        { mcpServers: { mockhub: { command: 'npx', args: ['-y', 'mcp-remote', url, '--header', `Authorization: Bearer ${token}`] } } },
        null,
        2,
      );
    case 'curl':
      return `curl -H "Authorization: Bearer ${token}" \\\n  ${origin.replace(/\/$/, '')}/__admin/mockhub/api/projects`;
  }
}

export function expiryLabel(expiresAt: string | null, now: number): string {
  if (!expiresAt) return 'Never';
  const ms = Date.parse(expiresAt) - now;
  if (ms <= 0) return 'Expired';
  const days = Math.ceil(ms / 86_400_000);
  return days === 1 ? 'in 1 day' : `in ${days} days`;
}

export function lastUsedLabel(lastUsedAt: string | null, now: number): string {
  if (!lastUsedAt) return 'Never used';
  const minutes = Math.floor((now - Date.parse(lastUsedAt)) / 60_000);
  if (minutes < 2) return 'Used just now';
  if (minutes < 60) return `Used ${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Used ${hours} h ago`;
  return `Used ${Math.floor(hours / 24)} days ago`;
}
