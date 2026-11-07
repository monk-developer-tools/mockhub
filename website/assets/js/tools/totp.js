const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const HASHES = { SHA1: 'SHA-1', SHA256: 'SHA-256', SHA512: 'SHA-512' };

export const DEFAULTS = { digits: 6, period: 30, algorithm: 'SHA1' };

export class TotpError extends Error {}

export function base32Decode(text) {
  const clean = String(text ?? '').replace(/[\s-]/g, '').replace(/=+$/, '').toUpperCase();
  if (!clean) throw new TotpError('Paste a secret, an otpauth:// link or a QR code.');
  const out = [];
  let buffer = 0;
  let bits = 0;
  for (const c of clean) {
    const value = ALPHABET.indexOf(c);
    if (value < 0) throw new TotpError('The secret must be Base32: letters A to Z and digits 2 to 7.');
    buffer = ((buffer << 5) | value) & 0xffff;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      out.push((buffer >> bits) & 0xff);
    }
  }
  return new Uint8Array(out);
}

export function base32Encode(bytes) {
  let out = '';
  let buffer = 0;
  let bits = 0;
  for (const b of bytes) {
    buffer = ((buffer << 8) | b) & 0xffff;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      out += ALPHABET[(buffer >> bits) & 31];
    }
  }
  if (bits > 0) out += ALPHABET[(buffer << (5 - bits)) & 31];
  return out;
}

function number(value, name, min, max) {
  if (value === undefined || value === null || value === '') return undefined;
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new TotpError(`${name} must be a whole number from ${min} to ${max}.`);
  return n;
}

function algorithm(value) {
  if (!value) return undefined;
  const name = String(value).toUpperCase().replace('-', '');
  if (!HASHES[name]) throw new TotpError('The algorithm must be SHA1, SHA256 or SHA512.');
  return name;
}

export function parseOtpAuth(text) {
  let url;
  try {
    url = new URL(String(text).trim());
  } catch (e) {
    throw new TotpError('That otpauth:// link is not a valid URL.');
  }
  if (url.protocol !== 'otpauth:') throw new TotpError('The link must start with otpauth://');
  const kind = url.hostname.toLowerCase();
  if (kind === 'hotp') throw new TotpError('Only time-based codes (otpauth://totp/…) are supported, not counter-based HOTP.');
  if (kind !== 'totp') throw new TotpError('The link must start with otpauth://totp/');
  const secret = url.searchParams.get('secret');
  if (!secret) throw new TotpError('The otpauth:// link has no secret.');
  const label = decodeURIComponent(url.pathname.replace(/^\//, ''));
  const colon = label.indexOf(':');
  return {
    secret,
    issuer: url.searchParams.get('issuer') ?? (colon < 0 ? '' : label.slice(0, colon).trim()),
    account: (colon < 0 ? label : label.slice(colon + 1)).trim(),
    digits: number(url.searchParams.get('digits'), 'digits', 6, 8),
    period: number(url.searchParams.get('period'), 'period', 10, 300),
    algorithm: algorithm(url.searchParams.get('algorithm')),
  };
}

export function readInput(text) {
  const value = String(text ?? '').trim();
  const parsed = /^otpauth:\/\//i.test(value) ? parseOtpAuth(value) : { secret: value, issuer: '', account: '' };
  base32Decode(parsed.secret);
  return parsed;
}

export function settings(parsed, chosen = {}) {
  return {
    digits: number(chosen.digits, 'digits', 6, 8) ?? parsed.digits ?? DEFAULTS.digits,
    period: number(chosen.period, 'period', 10, 300) ?? parsed.period ?? DEFAULTS.period,
    algorithm: algorithm(chosen.algorithm) ?? parsed.algorithm ?? DEFAULTS.algorithm,
  };
}

export function windowAt(period, nowSeconds) {
  const step = Math.floor(nowSeconds / period);
  const from = step * period;
  return { step, from, until: from + period, remaining: from + period - nowSeconds };
}

function counterBytes(step) {
  const bytes = new Uint8Array(8);
  let value = BigInt(step);
  for (let i = 7; i >= 0; i--) {
    bytes[i] = Number(value & 0xffn);
    value >>= 8n;
  }
  return bytes;
}

export async function codeAt(secret, { digits, period, algorithm: hash }, nowSeconds) {
  const key = await crypto.subtle.importKey('raw', base32Decode(secret), { name: 'HMAC', hash: HASHES[hash] }, false, ['sign']);
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', key, counterBytes(Math.floor(nowSeconds / period))));
  const offset = mac[mac.length - 1] & 0x0f;
  const binary = ((mac[offset] & 0x7f) << 24) | (mac[offset + 1] << 16) | (mac[offset + 2] << 8) | mac[offset + 3];
  return String(binary % 10 ** digits).padStart(digits, '0');
}

export function otpauthLink(secret, { issuer = '', account = '', digits, period, algorithm: hash }) {
  const label = issuer ? `${encodeURIComponent(issuer)}:${encodeURIComponent(account || 'test')}` : encodeURIComponent(account || 'test');
  const query = new URLSearchParams({ secret });
  if (issuer) query.set('issuer', issuer);
  if (hash !== DEFAULTS.algorithm) query.set('algorithm', hash);
  if (digits !== DEFAULTS.digits) query.set('digits', String(digits));
  if (period !== DEFAULTS.period) query.set('period', String(period));
  return `otpauth://totp/${label}?${query.toString()}`;
}

export function apiSnippets(origin, secret, { digits, period, algorithm: hash }) {
  const query = new URLSearchParams({ secret });
  if (digits !== DEFAULTS.digits) query.set('digits', String(digits));
  if (period !== DEFAULTS.period) query.set('period', String(period));
  if (hash !== DEFAULTS.algorithm) query.set('algorithm', hash);
  const options = [digits !== DEFAULTS.digits ? `digits=${digits}` : '', period !== DEFAULTS.period ? `interval=${period}` : '', hash !== DEFAULTS.algorithm ? `digest=hashlib.${hash.toLowerCase()}` : ''].filter(Boolean);
  return {
    curl: `curl -s '${origin}/__public/totp?${query.toString()}&format=text'`,
    json: `curl -s '${origin}/__public/totp?${query.toString()}'`,
    python: `${hash !== DEFAULTS.algorithm ? 'import hashlib\n' : ''}import pyotp\n\nprint(pyotp.TOTP(${JSON.stringify(secret)}${options.length ? `, ${options.join(', ')}` : ''}).now())`,
  };
}

export function randomSecret(bytes = 20) {
  return base32Encode(crypto.getRandomValues(new Uint8Array(bytes)));
}
