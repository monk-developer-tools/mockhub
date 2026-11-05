export const LINK_PATH = '/__admin/mockhub/ui/mock-these-apis';
export const BADGE_PATH = '/__admin/mockhub/ui/mock-these-apis.svg';

export function specFromSearch(search: string): string {
  return new URLSearchParams(search).get('openapi')?.trim() ?? '';
}

export function isHttpUrl(text: string): boolean {
  try {
    const url = new URL(text.trim());
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

export function mockLink(origin: string, specUrl: string): string {
  return `${origin}${LINK_PATH}?openapi=${encodeURIComponent(specUrl.trim())}`;
}

export function badgeMarkdown(origin: string, specUrl: string): string {
  return `[![Mock these APIs](${origin}${BADGE_PATH})](${mockLink(origin, specUrl)})`;
}

export function badgeHtml(origin: string, specUrl: string): string {
  const href = mockLink(origin, specUrl).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
  return `<a href="${href}"><img src="${origin}${BADGE_PATH}" alt="Mock these APIs" height="28"></a>`;
}
