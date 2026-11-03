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

const SETTINGS_KEY = 'mockhub.sidebar.settings';

export function readSettingsOpen(): boolean {
  try {
    return localStorage.getItem(SETTINGS_KEY) === 'open';
  } catch {
    return false;
  }
}

export function writeSettingsOpen(open: boolean) {
  try {
    localStorage.setItem(SETTINGS_KEY, open ? 'open' : 'closed');
  } catch {
    return;
  }
}
