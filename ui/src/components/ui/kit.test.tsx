import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../../test/render';
import { Breadcrumbs } from './Breadcrumbs';
import { EmptyState } from './EmptyState';
import { Menu } from './Menu';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('shows the title, meta, description and actions', () => {
    // When
    renderWithShell(
      <PageHeader title="Server" meta={<span>v1</span>} description="Settings for every project." actions={<button>Save</button>} />,
    );

    // Then
    expect(screen.getByRole('heading', { level: 1, name: 'Server' })).toBeInTheDocument();
    expect(screen.getByText('v1')).toBeInTheDocument();
    expect(screen.getByText('Settings for every project.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
  });

  it('leaves out empty parts', () => {
    // When
    const { container } = renderWithShell(<PageHeader title="Users" actions={false} />);

    // Then
    expect(container.querySelector('.page-actions')).toBeNull();
    expect(container.querySelector('.page-description')).toBeNull();
  });
});

describe('Breadcrumbs', () => {
  it('links every crumb but the current page', () => {
    // When
    renderWithShell(<Breadcrumbs crumbs={[{ label: 'Default', to: '/' }, { label: 'Payments', to: '/projects/p1' }, { label: 'Stubs' }]} />);

    // Then
    expect(screen.getByRole('link', { name: 'Default' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Payments' })).toHaveAttribute('href', '/projects/p1');
    expect(screen.getByText('Stubs')).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('link', { name: 'Stubs' })).not.toBeInTheDocument();
  });
});

describe('Menu', () => {
  it('runs an item and closes', async () => {
    // Given
    const onSelect = vi.fn();
    renderWithShell(<Menu label="Help" trigger="?" items={[{ label: 'Keyboard shortcuts', onSelect }, { label: 'Docs', href: 'https://example.com/docs' }]} />);

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Help' }));
    expect(screen.getByRole('menuitem', { name: 'Docs' })).toHaveAttribute('href', 'https://example.com/docs');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Keyboard shortcuts' }));

    // Then
    expect(onSelect).toHaveBeenCalled();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('closes with Escape', async () => {
    // Given
    renderWithShell(<Menu label="Help" trigger="?" items={[{ label: 'Docs', href: '/docs' }]} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help' }));

    // When
    await userEvent.keyboard('{Escape}');

    // Then
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Help' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('button', { name: 'Help' })).toHaveFocus();
  });

  it('moves between items with the arrow keys', async () => {
    // Given
    renderWithShell(<Menu label="Help" trigger="?" items={[{ label: 'First', onSelect: vi.fn() }, { label: 'Second', onSelect: vi.fn() }]} />);
    await userEvent.click(screen.getByRole('button', { name: 'Help' }));
    expect(screen.getByRole('menuitem', { name: 'First' })).toHaveFocus();

    // When
    await userEvent.keyboard('{ArrowDown}');

    // Then
    expect(screen.getByRole('menuitem', { name: 'Second' })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'First' })).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(screen.getByRole('menuitem', { name: 'Second' })).toHaveFocus();
  });
});

describe('EmptyState', () => {
  it('offers the action and a docs link', () => {
    // When
    renderWithShell(<EmptyState title="No callouts yet" actions={<button>Add one</button>} docs="https://example.com/docs#webhooks">Calls show up here.</EmptyState>);

    // Then
    expect(screen.getByRole('heading', { name: 'No callouts yet' })).toBeInTheDocument();
    expect(screen.getByText('Calls show up here.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add one' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Read the docs' })).toHaveAttribute('href', 'https://example.com/docs#webhooks');
  });
});
