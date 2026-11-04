const encoder = new TextEncoder();
const decoder = new TextDecoder();

export function base64UrlEncode(input) {
  const bytes = typeof input === 'string' ? encoder.encode(input) : new Uint8Array(input);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function base64UrlBytes(text) {
  const padded = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(text.length / 4) * 4, '=');
  const binary = atob(padded);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

const base64UrlText = (text) => decoder.decode(base64UrlBytes(text));

export class JwtError extends Error {}

export function decode(raw) {
  const token = String(raw).trim().replace(/^Bearer\s+/i, '');
  const parts = token.split('.');
  if (parts.length !== 3) throw new JwtError(`A JWT has three parts separated by dots; this has ${parts.length}.`);
  const part = (text, name) => {
    try {
      return JSON.parse(base64UrlText(text));
    } catch (e) {
      throw new JwtError(`The ${name} is not base64url-encoded JSON.`);
    }
  };
  return { header: part(parts[0], 'header'), payload: part(parts[1], 'payload'), signature: parts[2], signed: `${parts[0]}.${parts[1]}` };
}

const TIME_CLAIMS = { exp: 'Expires', iat: 'Issued', nbf: 'Not before' };

function relative(seconds) {
  const abs = Math.abs(seconds);
  if (abs < 5) return 'just now';
  const [value, unit] = abs < 60 ? [abs, 'second'] : abs < 3600 ? [Math.round(abs / 60), 'minute'] : abs < 86400 ? [Math.round(abs / 3600), 'hour'] : [Math.round(abs / 86400), 'day'];
  const text = `${value} ${unit}${value === 1 ? '' : 's'}`;
  return seconds >= 0 ? `in ${text}` : `${text} ago`;
}

export function times(payload, nowSeconds) {
  return Object.entries(TIME_CLAIMS)
    .filter(([claim]) => typeof payload[claim] === 'number')
    .map(([claim, label]) => ({ claim, label, at: new Date(payload[claim] * 1000), relative: relative(payload[claim] - nowSeconds) }));
}

export function status(payload, nowSeconds) {
  if (typeof payload.exp === 'number' && payload.exp <= nowSeconds) return 'expired';
  if (typeof payload.nbf === 'number' && payload.nbf > nowSeconds) return 'not-yet-valid';
  return 'valid';
}

const HMAC = { name: 'HMAC', hash: 'SHA-256' };
const RSA = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };

const hmacKey = (secret, usage) => crypto.subtle.importKey('raw', encoder.encode(secret), HMAC, false, [usage]);

export async function generateRsaKeys() {
  const pair = await crypto.subtle.generateKey({ ...RSA, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) }, true, ['sign', 'verify']);
  const jwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(`${jwk.n}.${jwk.e}`));
  const kid = base64UrlEncode(digest).slice(0, 16);
  return { privateKey: pair.privateKey, kid, jwks: { keys: [{ kty: 'RSA', use: 'sig', alg: 'RS256', kid, n: jwk.n, e: jwk.e }] } };
}

export async function sign(payload, options) {
  const header = options.alg === 'RS256' ? { alg: 'RS256', typ: 'JWT', kid: options.kid } : { alg: 'HS256', typ: 'JWT' };
  const signed = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}`;
  const signature = options.alg === 'RS256'
    ? await crypto.subtle.sign(RSA, options.privateKey, encoder.encode(signed))
    : await crypto.subtle.sign(HMAC, await hmacKey(options.secret ?? '', 'sign'), encoder.encode(signed));
  return `${signed}.${base64UrlEncode(signature)}`;
}

function pickJwk(keys, header) {
  const list = Array.isArray(keys?.keys) ? keys.keys : [keys];
  const rsa = list.filter((k) => k && k.kty === 'RSA');
  if (!rsa.length) throw new JwtError('The JWKS has no RSA key.');
  return rsa.find((k) => k.kid === header.kid) ?? (rsa.length === 1 ? rsa[0] : null) ?? (() => {
    throw new JwtError(`No key in the JWKS has kid "${header.kid}".`);
  })();
}

export async function verify(raw, { secret, jwks }) {
  const { header, signature, signed } = decode(raw);
  const data = encoder.encode(signed);
  const bytes = base64UrlBytes(signature);
  if (header.alg === 'HS256') {
    if (!secret) throw new JwtError('Enter the shared secret to check an HS256 token.');
    return crypto.subtle.verify(HMAC, await hmacKey(secret, 'verify'), bytes, data);
  }
  if (header.alg === 'RS256') {
    if (!jwks) throw new JwtError('Paste the JWKS (or one public JWK) to check an RS256 token.');
    const jwk = pickJwk(jwks, header);
    const key = await crypto.subtle.importKey('jwk', { kty: 'RSA', n: jwk.n, e: jwk.e, alg: 'RS256', ext: true }, RSA, false, ['verify']);
    return crypto.subtle.verify(RSA, key, bytes, data);
  }
  throw new JwtError(`This tool checks HS256 and RS256 tokens; this one uses ${header.alg}.`);
}
