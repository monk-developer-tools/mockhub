export class CurlError extends Error {}

export function tokenize(command) {
  const text = command.replace(/\\\r?\n/g, ' ').replace(/\^\r?\n/g, ' ').trim();
  const tokens = [];
  let current = '';
  let started = false;
  let quote = null;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quote === "'") {
      if (c === "'") quote = null;
      else current += c;
    } else if (quote === '"') {
      if (c === '"') quote = null;
      else if (c === '\\' && i + 1 < text.length && '"\\$`'.includes(text[i + 1])) current += text[++i];
      else current += c;
    } else if (c === "'" || c === '"') {
      quote = c;
      started = true;
    } else if (c === '$' && text[i + 1] === "'") {
      quote = "'";
      started = true;
      i++;
    } else if (c === '\\' && i + 1 < text.length) {
      current += text[++i];
      started = true;
    } else if (/\s/.test(c)) {
      if (started) tokens.push(current);
      current = '';
      started = false;
    } else {
      current += c;
      started = true;
    }
  }
  if (quote) throw new CurlError('A quote is not closed.');
  if (started) tokens.push(current);
  return tokens;
}

const DATA = new Set(['-d', '--data', '--data-raw', '--data-binary', '--data-ascii', '--data-urlencode']);
const IGNORED_WITH_VALUE = new Set(['-o', '--output', '-m', '--max-time', '--connect-timeout', '-w', '--write-out', '-A', '--user-agent', '-e', '--referer', '--cacert', '--cert', '--key', '-x', '--proxy', '--retry', '-F', '--form', '-b', '--cookie', '-c', '--cookie-jar', '--resolve']);

export function parse(command) {
  const tokens = tokenize(command);
  if (!tokens.length || tokens[0] !== 'curl') throw new CurlError('Paste a command that starts with curl.');
  const request = { method: null, url: null, headers: [], data: [], get: false, json: false, user: null };
  for (let i = 1; i < tokens.length; i++) {
    const token = tokens[i];
    const value = () => {
      if (i + 1 >= tokens.length) throw new CurlError(`${token} needs a value.`);
      return tokens[++i];
    };
    const [flag, inline] = token.startsWith('--') && token.includes('=') ? [token.slice(0, token.indexOf('=')), token.slice(token.indexOf('=') + 1)] : [token, null];
    const take = () => (inline !== null ? inline : value());
    if (flag === '-X' || flag === '--request') request.method = take().toUpperCase();
    else if (token.startsWith('-X') && token.length > 2) request.method = token.slice(2).toUpperCase();
    else if (flag === '-H' || flag === '--header') request.headers.push(take());
    else if (DATA.has(flag)) request.data.push(take());
    else if (flag === '--json') {
      request.data.push(take());
      request.json = true;
    } else if (flag === '-u' || flag === '--user') request.user = take();
    else if (flag === '-G' || flag === '--get') request.get = true;
    else if (flag === '-I' || flag === '--head') request.method = 'HEAD';
    else if (flag === '--url') request.url = take();
    else if (IGNORED_WITH_VALUE.has(flag)) {
      if (inline === null) value();
    } else if (token.startsWith('-')) continue;
    else if (!request.url) request.url = token;
  }
  if (!request.url) throw new CurlError('The command has no URL.');
  return request;
}

const NOISY = new Set(['user-agent', 'accept-encoding', 'content-length', 'host', 'connection', 'cache-control', 'pragma', 'origin', 'referer', 'cookie', 'sec-fetch-mode', 'sec-fetch-site', 'sec-fetch-dest', 'sec-ch-ua', 'sec-ch-ua-mobile', 'sec-ch-ua-platform', 'accept-language', 'priority']);

const splitHeader = (line) => {
  const at = line.indexOf(':');
  return at < 0 ? [line.trim(), ''] : [line.slice(0, at).trim(), line.slice(at + 1).trim()];
};

function looksJson(text) {
  try {
    const value = JSON.parse(text);
    return value !== null && typeof value === 'object';
  } catch (e) {
    return false;
  }
}

export function describe(command) {
  const request = parse(command);
  const withScheme = /^[a-z]+:\/\//i.test(request.url) ? request.url : `http://${request.url}`;
  let url;
  try {
    url = new URL(withScheme);
  } catch (e) {
    throw new CurlError(`"${request.url}" is not a URL.`);
  }
  const body = request.data.join('&');
  if (request.get && body) for (const [k, v] of new URLSearchParams(body)) url.searchParams.append(k, v);
  const method = request.method ?? (body && !request.get ? 'POST' : 'GET');
  const headers = request.headers.map(splitHeader);
  if (request.json) {
    if (!headers.some(([k]) => k.toLowerCase() === 'content-type')) headers.push(['Content-Type', 'application/json']);
    if (!headers.some(([k]) => k.toLowerCase() === 'accept')) headers.push(['Accept', 'application/json']);
  }
  if (request.user) headers.push(['Authorization', `Basic ${btoa(request.user)}`]);
  return {
    method,
    path: url.pathname,
    query: [...url.searchParams.entries()],
    headers: headers.map(([name, value]) => ({ name, value, suggested: !NOISY.has(name.toLowerCase()) && name.toLowerCase() !== 'accept' })),
    body: request.get ? '' : body,
  };
}

export function toStub(info, { headers = [], status = 200, responseBody = '', matchBody = true } = {}) {
  const request = { method: info.method, urlPath: info.path };
  if (info.query.length) {
    request.queryParameters = {};
    for (const [k, v] of info.query) request.queryParameters[k] = { equalTo: v };
  }
  const chosen = info.headers.filter((h) => headers.includes(h.name));
  if (chosen.length) {
    request.headers = {};
    for (const h of chosen) request.headers[h.name] = { equalTo: h.value };
  }
  if (matchBody && info.body) {
    request.bodyPatterns = [looksJson(info.body) ? { equalToJson: info.body, ignoreExtraElements: true } : { equalTo: info.body }];
  }
  const response = { status: Number(status) };
  if (responseBody.trim()) {
    if (looksJson(responseBody)) {
      response.jsonBody = JSON.parse(responseBody);
    } else {
      response.body = responseBody;
    }
    response.headers = { 'Content-Type': looksJson(responseBody) ? 'application/json' : 'text/plain' };
  }
  return { name: `${info.method} ${info.path}`, request, response };
}
