const KEY = 'mockhub.sidebar';

export function readCollapsed(): boolean {
  try {
    return localStorage.getItem(KEY) === 'collapsed';
  } catch {
    return false;
  }
}

export function writeCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(KEY, collapsed ? 'collapsed' : 'expanded');
  } catch {
    return;
  }
}
