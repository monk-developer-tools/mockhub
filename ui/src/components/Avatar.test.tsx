import { render } from '@testing-library/react';
import { Avatar, initials } from './Avatar';

describe('Avatar', () => {
  it('takes initials from names, usernames and e-mails', () => {
    // Then
    expect(initials('Maria Lopez')).toBe('ML');
    expect(initials('Li Wei Chen')).toBe('LC');
    expect(initials('sam')).toBe('SA');
    expect(initials('ana.tester@contoso.com')).toBe('AC');
    expect(initials('  ')).toBe('?');
  });

  it('gives the same person the same colours every time', () => {
    // When
    const first = render(<Avatar name="Maria Lopez" seed="u1" />).container.innerHTML;
    const again = render(<Avatar name="Maria Lopez" seed="u1" />).container.innerHTML;
    const other = render(<Avatar name="Maria Lopez" seed="u2" />).container.innerHTML;

    // Then
    expect(first).toBe(again);
    expect(first).toContain('ML');
    expect(other).not.toBe(first);
  });
});
