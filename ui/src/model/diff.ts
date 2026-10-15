export type DiffLine = { kind: 'same' | 'add' | 'remove'; text: string };

export function diffLines(before: string, after: string): DiffLine[] {
  const a = before ? before.split('\n') : [];
  const b = after ? after.split('\n') : [];
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      out.push({ kind: 'same', text: a[i] });
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ kind: 'remove', text: a[i++] });
    } else {
      out.push({ kind: 'add', text: b[j++] });
    }
  }
  while (i < a.length) out.push({ kind: 'remove', text: a[i++] });
  while (j < b.length) out.push({ kind: 'add', text: b[j++] });
  return out;
}

export function collapse(lines: DiffLine[], context = 3): (DiffLine | { kind: 'gap'; count: number })[] {
  const keep = lines.map((l, i) => l.kind !== 'same' || lines.slice(Math.max(0, i - context), i + context + 1).some((n) => n.kind !== 'same'));
  const out: (DiffLine | { kind: 'gap'; count: number })[] = [];
  let gap = 0;
  lines.forEach((line, i) => {
    if (keep[i]) {
      if (gap) out.push({ kind: 'gap', count: gap });
      gap = 0;
      out.push(line);
    } else {
      gap++;
    }
  });
  if (gap) out.push({ kind: 'gap', count: gap });
  return out;
}
