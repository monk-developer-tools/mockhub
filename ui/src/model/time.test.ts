import { timeAgo } from './time';

describe('timeAgo', () => {
  const now = Date.parse('2024-01-01T12:00:00Z');

  it('describes recent and older times', () => {
    expect(timeAgo(undefined, now)).toBe('never');
    expect(timeAgo('2024-01-01T11:59:40Z', now)).toBe('just now');
    expect(timeAgo('2024-01-01T11:50:00Z', now)).toBe('10 min ago');
    expect(timeAgo('2024-01-01T09:00:00Z', now)).toBe('3 h ago');
    expect(timeAgo('2023-12-29T12:00:00Z', now)).toBe('3 d ago');
  });
});
