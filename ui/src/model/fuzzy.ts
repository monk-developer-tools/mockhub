export function fuzzyScore(query: string, text: string): number {
  const q = query.trim().toLowerCase();
  if (!q) return 1;
  const t = text.toLowerCase();
  const direct = t.indexOf(q);
  if (direct >= 0) return 1000 - direct - (t.length - q.length) * 0.01;
  let score = 0;
  let position = 0;
  let streak = 0;
  for (const ch of q) {
    if (ch === ' ') continue;
    const found = t.indexOf(ch, position);
    if (found < 0) return 0;
    streak = found === position ? streak + 1 : 0;
    score += 10 + streak * 5 - Math.min(found - position, 10);
    position = found + 1;
  }
  return score;
}

export function fuzzyFilter<T>(items: T[], query: string, text: (item: T) => string, limit = 50): T[] {
  return items
    .map((item) => ({ item, score: fuzzyScore(query, text(item)) }))
    .filter((r) => r.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((r) => r.item);
}
