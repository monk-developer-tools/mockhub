import { filterFindings, groupFindings, isSpecFile, requestsLink, sourceLabel, specDiff, type DriftFinding } from './contract';

const finding = (over: Partial<DriftFinding>): DriftFinding => ({
  key: 'k', kind: 'UNDOCUMENTED_PATH', label: 'Path not in the contract', side: 'request', method: 'GET', path: '/refunds', detail: '',
  count: 1, firstSeen: '2026-11-05T10:00:00Z', lastSeen: '2026-11-05T10:00:00Z', expected: false,
  example: { id: 'e1', url: '/shop/refunds?x=1', status: 200 }, ...over,
});

describe('contract', () => {
  it('groups findings by kind with request problems first and the busiest first', () => {
    // Given
    const findings = [
      finding({ key: 'a', kind: 'RESPONSE_BODY_SCHEMA', label: 'Response body', side: 'response', count: 9 }),
      finding({ key: 'b', count: 2 }),
      finding({ key: 'c', count: 3, path: '/x' }),
      finding({ key: 'd', kind: 'UNDOCUMENTED_HEADER', label: 'Header', count: 7 }),
    ];

    // When
    const groups = groupFindings(findings);

    // Then
    expect(groups.map((g) => [g.kind, g.total, g.findings.length])).toEqual([
      ['UNDOCUMENTED_HEADER', 7, 1],
      ['UNDOCUMENTED_PATH', 5, 2],
      ['RESPONSE_BODY_SCHEMA', 9, 1],
    ]);
  });

  it('filters open and expected findings', () => {
    // Given
    const findings = [finding({ key: 'a' }), finding({ key: 'b', expected: true })];

    // When / Then
    expect(filterFindings(findings, 'open').map((f) => f.key)).toEqual(['a']);
    expect(filterFindings(findings, 'expected').map((f) => f.key)).toEqual(['b']);
    expect(filterFindings(findings, 'all')).toHaveLength(2);
  });

  it('links a finding to its traffic in the request log', () => {
    // Given / When
    const link = requestsLink('p1', '/shop', finding({}));

    // Then
    expect(link).toBe('/projects/p1/requests?method=GET&q=%2Fshop%2Frefunds');
    expect(requestsLink('p1', '/shop', finding({ example: { id: null, url: null, status: 0 } }))).toContain('q=%2Fshop%2Frefunds');
  });

  it('diffs only the changed middle of a large contract', () => {
    // Given
    const lines = Array.from({ length: 3000 }, (_, i) => `line ${i}`);
    const after = [...lines.slice(0, 1500), 'added', ...lines.slice(1500)];

    // When
    const diff = specDiff(lines.join('\n'), after.join('\n'));

    // Then
    expect(diff.filter((l) => l.kind === 'add').map((l) => l.text)).toEqual(['added']);
    expect(diff.filter((l) => l.kind === 'remove')).toHaveLength(0);
    expect(diff).toHaveLength(3001);
  });

  it('describes where a contract came from and which files it accepts', () => {
    // Given / When / Then
    expect(sourceLabel('ai-patch')).toBe('patched by the assistant');
    expect(isSpecFile('orders.YAML')).toBe(true);
    expect(isSpecFile('orders.txt')).toBe(false);
  });
});
