import { render, screen } from '@testing-library/react';
import { ChatText } from './ChatText';

describe('ChatText', () => {
  it('renders paragraphs, lists, inline code and code blocks', () => {
    // When
    const { container } = render(<ChatText text={'Staged **3** stubs:\n- `GET /a`\n- `POST /a`\n\n1. first\n2. second\n```\n{"a":1}\n```'} />);

    // Then
    expect(screen.getByText('3').tagName).toBe('STRONG');
    expect(container.querySelectorAll('ul li')).toHaveLength(2);
    expect(container.querySelectorAll('ol li')).toHaveLength(2);
    expect(screen.getByText('GET /a').tagName).toBe('CODE');
    expect(container.querySelector('pre')).toHaveTextContent('{"a":1}');
  });
});
