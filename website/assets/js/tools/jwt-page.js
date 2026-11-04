import { $, debounce, show, wireCopyButtons } from './common.js';
import { decode, generateRsaKeys, sign, status, times, verify } from './jwt.js';

const input = $('[data-jwt-input]');
const secret = $('[data-jwt-secret]');
const jwks = $('[data-jwt-jwks]');
const now = () => Math.floor(Date.now() / 1000);
const pretty = (value) => JSON.stringify(value, null, 2);
const STATUS = { valid: ['Not expired', 'ok'], expired: ['Expired', 'bad'], 'not-yet-valid': ['Not valid yet', 'warn'] };

async function checkSignature(header) {
  const result = $('[data-jwt-signature]');
  const rs = header.alg === 'RS256';
  $('[data-jwt-secret-label]').hidden = rs;
  secret.hidden = rs;
  $('[data-jwt-jwks-label]').hidden = !rs;
  jwks.hidden = !rs;
  try {
    let keys;
    if (rs && jwks.value.trim()) keys = JSON.parse(jwks.value);
    const ok = await verify(input.value, { secret: secret.value, jwks: keys });
    result.textContent = ok ? 'Signature verified' : 'Signature does not match';
    result.className = `tool-result ${ok ? 'ok' : 'bad'}`;
  } catch (e) {
    result.textContent = e instanceof SyntaxError ? 'The JWKS is not valid JSON.' : e.message;
    result.className = 'tool-result';
  }
}

function render() {
  const error = $('[data-jwt-error]');
  const badge = $('[data-jwt-status]');
  try {
    const { header, payload } = decode(input.value);
    show(error, '');
    $('[data-jwt-header]').textContent = pretty(header);
    $('[data-jwt-payload]').textContent = pretty(payload);
    $('[data-jwt-times]').innerHTML = '';
    for (const t of times(payload, now())) {
      const dt = document.createElement('dt');
      dt.textContent = `${t.label} (${t.claim})`;
      const dd = document.createElement('dd');
      dd.textContent = `${t.at.toLocaleString()} · ${t.relative}`;
      $('[data-jwt-times]').append(dt, dd);
    }
    const [label, tone] = STATUS[status(payload, now())];
    badge.textContent = label;
    badge.className = `tool-badge ${tone}`;
    checkSignature(header);
  } catch (e) {
    show(error, e.message);
    badge.textContent = '';
    badge.className = 'tool-badge';
    $('[data-jwt-header]').textContent = '';
    $('[data-jwt-payload]').textContent = '';
    $('[data-jwt-times]').innerHTML = '';
    $('[data-jwt-signature]').textContent = '';
  }
}

const alg = $('[data-create-alg]');
const createSecret = $('[data-create-secret]');
const claims = $('[data-create-claims]');
let rsa;

async function create() {
  const error = $('[data-create-error]');
  const rs = alg.value === 'RS256';
  $('[data-create-secret-label]').hidden = rs;
  createSecret.hidden = rs;
  $('[data-create-jwks-box]').hidden = !rs;
  try {
    const payload = JSON.parse(claims.value);
    if (payload === null || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('Claims must be a JSON object.');
    payload.iat ??= now();
    payload.exp ??= payload.iat + 3600;
    if (rs) rsa ??= await generateRsaKeys();
    const token = await sign(payload, rs ? { alg: 'RS256', privateKey: rsa.privateKey, kid: rsa.kid } : { alg: 'HS256', secret: createSecret.value });
    $('[data-create-token]').textContent = token;
    if (rs) $('[data-create-jwks]').textContent = pretty(rsa.jwks);
    show(error, '');
  } catch (e) {
    show(error, e instanceof SyntaxError ? 'The claims are not valid JSON.' : e.message);
  }
}

$('[data-create-decode]').addEventListener('click', () => {
  input.value = $('[data-create-token]').textContent;
  if (alg.value === 'RS256') jwks.value = $('[data-create-jwks]').textContent;
  else secret.value = createSecret.value;
  $('#jwt-tab-decode').click();
  render();
});

claims.value = pretty({ sub: 'ada', name: 'Ada Lovelace', scope: 'read write', roles: ['admin'] });
sign({ sub: 'ada', name: 'Ada Lovelace', scope: 'read write', iat: now(), exp: now() + 3600 }, { alg: 'HS256', secret: 'change-me' }).then((token) => {
  input.value = token;
  render();
});
input.addEventListener('input', debounce(render));
secret.addEventListener('input', debounce(render));
jwks.addEventListener('input', debounce(render));
[alg, createSecret, claims].forEach((el) => el.addEventListener('input', debounce(create)));
alg.addEventListener('change', create);
create();
wireCopyButtons();
