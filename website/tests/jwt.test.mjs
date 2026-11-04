import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createHmac } from 'node:crypto';
import { JwtError, base64UrlEncode, decode, generateRsaKeys, sign, status, times, verify } from '../assets/js/tools/jwt.js';

const hs256 = (payload, secret) => {
  const signed = `${base64UrlEncode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))}.${base64UrlEncode(JSON.stringify(payload))}`;
  return `${signed}.${createHmac('sha256', secret).update(signed).digest('base64url')}`;
};

test('decodes the header and payload and strips a Bearer prefix', () => {
  // Given
  const token = hs256({ sub: 'ada', scope: 'read' }, 's3cret');

  // When
  const decoded = decode(`Bearer ${token}`);

  // Then
  assert.equal(decoded.header.alg, 'HS256');
  assert.deepEqual(decoded.payload, { sub: 'ada', scope: 'read' });
});

test('explains what is wrong with a token that is not a JWT', () => {
  // When / Then
  assert.throws(() => decode('abc.def'), new JwtError('A JWT has three parts separated by dots; this has 2.'));
  assert.throws(() => decode('abc.def.ghi'), new JwtError('The header is not base64url-encoded JSON.'));
});

test('describes time claims relative to now and spots expired tokens', () => {
  // Given
  const now = 1_700_000_000;
  const payload = { iat: now - 7200, exp: now - 60 };

  // When
  const described = times(payload, now);

  // Then
  assert.deepEqual(described.map((t) => [t.claim, t.relative]), [['exp', '1 minute ago'], ['iat', '2 hours ago']]);
  assert.equal(status(payload, now), 'expired');
  assert.equal(status({ nbf: now + 10 }, now), 'not-yet-valid');
  assert.equal(status({ exp: now + 10 }, now), 'valid');
  assert.equal(times({ iat: now - 2 }, now)[0].relative, 'just now');
});

test('signs HS256 tokens that another library verifies, and checks the secret', async () => {
  // Given
  const payload = { sub: 'ada' };

  // When
  const token = await sign(payload, { alg: 'HS256', secret: 's3cret' });

  // Then
  assert.equal(token, hs256(payload, 's3cret'));
  assert.equal(await verify(token, { secret: 's3cret' }), true);
  assert.equal(await verify(token, { secret: 'wrong' }), false);
});

test('signs RS256 tokens with a generated key and verifies them against its JWKS', async () => {
  // Given
  const keys = await generateRsaKeys();

  // When
  const token = await sign({ sub: 'ada' }, { alg: 'RS256', privateKey: keys.privateKey, kid: keys.kid });
  const tampered = token.replace(/\.[^.]+\./, `.${base64UrlEncode(JSON.stringify({ sub: 'eve' }))}.`);

  // Then
  assert.equal(decode(token).header.kid, keys.kid);
  assert.equal(await verify(token, { jwks: keys.jwks }), true);
  assert.equal(await verify(tampered, { jwks: keys.jwks }), false);
  await assert.rejects(verify(token, {}), /Paste the JWKS/);
});
