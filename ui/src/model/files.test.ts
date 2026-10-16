import { formatSize, safeFileName } from './files';

describe('file helpers', () => {
  it('makes upload names safe for the server', () => {
    // Then
    expect(safeFileName('Invoice 42 (final).pdf')).toBe('Invoice-42-final-.pdf');
    expect(safeFileName('../../etc/passwd')).toBe('etc-passwd');
    expect(safeFileName('***')).toBe('file');
  });

  it('formats sizes for people', () => {
    // Then
    expect(formatSize(512)).toBe('512 B');
    expect(formatSize(2048)).toBe('2.0 KB');
    expect(formatSize(3 * 1024 * 1024)).toBe('3.0 MB');
  });
});
