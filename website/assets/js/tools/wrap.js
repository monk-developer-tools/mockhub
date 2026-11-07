export function forwardTarget(text) {
  const raw = String(text ?? '').trim();
  if (!raw) throw new Error('Enter the API to watch, like https://api.example.com');
  let url;
  try {
    url = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw) ? raw : `https://${raw}`);
  } catch (e) {
    throw new Error('That is not a URL. Use one like https://api.example.com');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') throw new Error('Only http and https APIs can be watched.');
  if (url.search || url.hash) throw new Error('Leave out the query and #fragment; they come from each request.');
  const path = url.pathname.replace(/\/+$/, '');
  return { target: url.origin, pathPrefix: path };
}

export function localTarget(text) {
  const raw = String(text ?? '').trim() || 'http://localhost:3000';
  if (/^\d{1,5}$/.test(raw)) return `http://localhost:${raw}`;
  let url;
  try {
    url = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw) ? raw : `http://${raw}`);
  } catch (e) {
    throw new Error('Enter a port like 3000 or an address like http://localhost:3000');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') throw new Error('The local service must speak http or https.');
  return url.origin;
}

export function forwardRouting({ target, pathPrefix }) {
  return { mode: 'forward', target, pathPrefix, insecure: false, requestHeaders: {}, responseHeaders: {} };
}

export function tunnelRouting() {
  return { mode: 'tunnel', target: '', pathPrefix: '', insecure: false, requestHeaders: {}, responseHeaders: {} };
}

export function forwardExample(binUrl, { target, pathPrefix }, path = '/users?page=2') {
  return { from: `${binUrl}${path}`, to: `${target}${pathPrefix}${path}` };
}
