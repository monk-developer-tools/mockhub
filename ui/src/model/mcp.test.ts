import { expiryLabel, lastUsedLabel, mcpSnippet, mcpUrl } from './mcp';

const NOW = Date.parse('2026-11-03T10:00:00Z');

describe('mcpSnippet', () => {
  it('builds the setup for each client with the MCP URL and the token', () => {
    // When
    const claude = mcpSnippet('claude-code', 'https://mocks.acme.dev/', 'mh_abc');
    const cursor = JSON.parse(mcpSnippet('cursor', 'https://mocks.acme.dev', 'mh_abc'));
    const codex = mcpSnippet('codex', 'https://mocks.acme.dev', 'mh_abc');

    // Then
    expect(mcpUrl('https://mocks.acme.dev/')).toBe('https://mocks.acme.dev/__admin/mockhub/mcp');
    expect(claude).toBe('claude mcp add --transport http mockhub https://mocks.acme.dev/__admin/mockhub/mcp \\\n  --header "Authorization: Bearer mh_abc"');
    expect(cursor).toEqual({ mcpServers: { mockhub: { url: 'https://mocks.acme.dev/__admin/mockhub/mcp', headers: { Authorization: 'Bearer mh_abc' } } } });
    expect(codex).toContain('bearer_token_env_var = "MOCKHUB_TOKEN"');
    expect(codex).toContain('export MOCKHUB_TOKEN=mh_abc');
  });
});

describe('token labels', () => {
  it('describes expiry and last use in plain words', () => {
    // When
    const expiry = [null, '2026-11-04T09:00:00Z', '2026-12-03T10:00:00Z', '2026-11-01T00:00:00Z'].map((e) => expiryLabel(e, NOW));
    const used = [null, '2026-11-03T09:59:30Z', '2026-11-03T09:15:00Z', '2026-11-03T04:00:00Z', '2026-10-30T10:00:00Z'].map((u) => lastUsedLabel(u, NOW));

    // Then
    expect(expiry).toEqual(['Never', 'in 1 day', 'in 30 days', 'Expired']);
    expect(used).toEqual(['Never used', 'Used just now', 'Used 45 min ago', 'Used 6 h ago', 'Used 4 days ago']);
  });
});
