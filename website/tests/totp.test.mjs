import assert from 'node:assert/strict';
import { test } from 'node:test';
import { apiSnippets, base32Decode, base32Encode, codeAt, otpauthLink, parseOtpAuth, randomSecret, readInput, settings, windowAt } from '../assets/js/tools/totp.js';

const ascii = (text) => new TextEncoder().encode(text);
const SHA1 = base32Encode(ascii('12345678901234567890'));
const SHA256 = base32Encode(ascii('12345678901234567890123456789012'));
const SHA512 = base32Encode(ascii('1234567890123456789012345678901234567890123456789012345678901234'));

test('matches the RFC 6238 test vectors for SHA1, SHA256 and SHA512', async () => {
  // Given
  const vectors = [
    [59, '94287082', '46119246', '90693936'],
    [1111111109, '07081804', '68084774', '25091201'],
    [1234567890, '89005924', '91819424', '93441116'],
    [20000000000, '65353130', '77737706', '47863826'],
  ];

  // When
  const codes = await Promise.all(vectors.map(async ([time]) => [
    await codeAt(SHA1, { digits: 8, period: 30, algorithm: 'SHA1' }, time),
    await codeAt(SHA256, { digits: 8, period: 30, algorithm: 'SHA256' }, time),
    await codeAt(SHA512, { digits: 8, period: 30, algorithm: 'SHA512' }, time),
  ]));

  // Then
  assert.deepEqual(codes, vectors.map(([, ...expected]) => expected));
  assert.equal(SHA1, 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ');
});

test('reads secrets with spaces, lowercase and padding, and refuses non-Base32', () => {
  // Given
  const spaced = 'gezd gnbv gy3t qojq gezd gnbv gy3t qojq===';

  // When
  const bytes = base32Decode(spaced);

  // Then
  assert.equal(new TextDecoder().decode(bytes), '12345678901234567890');
  assert.throws(() => base32Decode('JBSWY3DP1'), /must be Base32/);
  assert.throws(() => base32Decode('  '), /Paste a secret/);
  assert.equal(base32Encode(base32Decode('JBSWY3DPEHPK3PXP')), 'JBSWY3DPEHPK3PXP');
});

test('reads an otpauth link with issuer, account and settings', () => {
  // Given
  const link = 'otpauth://totp/ACME%20Co:jane%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60';

  // When
  const parsed = parseOtpAuth(link);
  const plain = readInput('  JBSWY3DPEHPK3PXP ');

  // Then
  assert.deepEqual(parsed, { secret: 'JBSWY3DPEHPK3PXP', issuer: 'ACME Co', account: 'jane@example.com', digits: 8, period: 60, algorithm: 'SHA256' });
  assert.deepEqual(settings(parsed), { digits: 8, period: 60, algorithm: 'SHA256' });
  assert.deepEqual(settings(plain), { digits: 6, period: 30, algorithm: 'SHA1' });
  assert.deepEqual(settings(parsed, { digits: '6' }), { digits: 6, period: 60, algorithm: 'SHA256' });
});

test('refuses links and settings it cannot use', () => {
  // Given
  const cases = ['otpauth://hotp/x?secret=JBSWY3DP&counter=1', 'otpauth://totp/x?issuer=a', 'https://example.com/?secret=JBSWY3DP', 'otpauth://totp/x?secret=JBSWY3DP&digits=5'];

  // When
  const messages = cases.map((link) => {
    try {
      readInput(link);
      return '';
    } catch (e) {
      return e.message;
    }
  });

  // Then
  assert.match(messages[0], /not counter-based HOTP/);
  assert.match(messages[1], /has no secret/);
  assert.match(messages[2], /must be Base32/);
  assert.match(messages[3], /digits must be a whole number from 6 to 8/);
  assert.throws(() => settings({}, { algorithm: 'MD5' }), /SHA1, SHA256 or SHA512/);
});

test('works out the time window, a link back and snippets for tests', () => {
  // Given
  const chosen = { digits: 8, period: 60, algorithm: 'SHA256' };

  // When
  const window = windowAt(30, 1111111111);
  const link = otpauthLink('JBSWY3DPEHPK3PXP', { issuer: 'Shop', account: 'ada', ...chosen });
  const snippets = apiSnippets('https://mockhub.example', 'JBSWY3DPEHPK3PXP', chosen);
  const simple = apiSnippets('https://mockhub.example', 'JBSWY3DPEHPK3PXP', { digits: 6, period: 30, algorithm: 'SHA1' });

  // Then
  assert.deepEqual(window, { step: 37037037, from: 1111111110, until: 1111111140, remaining: 29 });
  assert.deepEqual(parseOtpAuth(link), { secret: 'JBSWY3DPEHPK3PXP', issuer: 'Shop', account: 'ada', ...chosen });
  assert.equal(snippets.curl, "curl -s 'https://mockhub.example/__public/totp?secret=JBSWY3DPEHPK3PXP&digits=8&period=60&algorithm=SHA256&format=text'");
  assert.equal(snippets.python, 'import hashlib\nimport pyotp\n\nprint(pyotp.TOTP("JBSWY3DPEHPK3PXP", digits=8, interval=60, digest=hashlib.sha256).now())');
  assert.equal(simple.python, 'import pyotp\n\nprint(pyotp.TOTP("JBSWY3DPEHPK3PXP").now())');
  assert.match(randomSecret(), /^[A-Z2-7]{32}$/);
});
