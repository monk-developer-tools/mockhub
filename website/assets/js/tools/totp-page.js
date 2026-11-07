import { $, show, wireCopyButtons } from './common.js';
import { apiSnippets, codeAt, otpauthLink, randomSecret, readInput, settings, TotpError, windowAt } from './totp.js';

const JSQR = '/assets/js/vendor/jsQR-1.4.0.js';
let current = null;
let timer = null;
let jsQrLoading = null;

const placeholder = (digits) => '-'.repeat(digits);

function chosen() {
  return { digits: $('[data-totp-digits]').value, period: $('[data-totp-period]').value, algorithm: $('[data-totp-algorithm]').value };
}

function applyParsed(parsed) {
  if ('digits' in parsed) {
    const values = settings(parsed);
    $('[data-totp-digits]').value = String(values.digits);
    $('[data-totp-period]').value = String(values.period);
    $('[data-totp-algorithm]').value = values.algorithm;
  }
  const who = [parsed.issuer, parsed.account].filter(Boolean).join(' · ');
  $('[data-totp-label]').textContent = who ? `For ${who}` : '';
  $('[data-totp-label]').hidden = !who;
}

function read(fromInput) {
  try {
    const parsed = readInput($('[data-totp-input]').value);
    if (fromInput) applyParsed(parsed);
    current = { parsed, values: settings(parsed, chosen()) };
    show($('[data-totp-error]'), '');
  } catch (e) {
    if (!(e instanceof TotpError)) throw e;
    current = null;
    show($('[data-totp-error]'), e.message);
  }
  renderStatic();
  tick();
}

function renderStatic() {
  const digits = Number($('[data-totp-digits]').value) || 6;
  if (!current) {
    ['[data-totp-code]', '[data-totp-previous]', '[data-totp-next]'].forEach((sel) => {
      $(sel).textContent = placeholder(digits);
    });
    $('[data-totp-link]').textContent = '';
    document.querySelectorAll('[data-totp-snippet]').forEach((pre) => {
      pre.textContent = '';
    });
    $('[data-totp-remaining]').textContent = '';
    $('[data-totp-fill]').style.width = '0%';
    return;
  }
  const secret = current.parsed.secret.replace(/[\s-]/g, '').replace(/=+$/, '').toUpperCase();
  $('[data-totp-link]').textContent = otpauthLink(secret, { issuer: current.parsed.issuer, account: current.parsed.account, ...current.values });
  const snippets = apiSnippets(location.origin, secret, current.values);
  document.querySelectorAll('[data-totp-snippet]').forEach((pre) => {
    pre.textContent = snippets[pre.dataset.totpSnippet];
  });
}

async function tick() {
  clearTimeout(timer);
  if (current) {
    const { parsed, values } = current;
    const now = Date.now() / 1000;
    const window = windowAt(values.period, Math.floor(now));
    const [code, previous, next] = await Promise.all([
      codeAt(parsed.secret, values, now),
      codeAt(parsed.secret, values, window.from - values.period),
      codeAt(parsed.secret, values, window.until),
    ]);
    if (current?.parsed === parsed) {
      $('[data-totp-code]').textContent = code;
      $('[data-totp-previous]').textContent = previous;
      $('[data-totp-next]').textContent = next;
      const left = window.until - now;
      $('[data-totp-remaining]').textContent = `Changes in ${Math.ceil(left)} s`;
      $('[data-totp-fill]').style.width = `${Math.max(0, Math.min(100, (left / values.period) * 100))}%`;
      $('[data-totp-bar]').setAttribute('aria-valuemax', String(values.period));
      $('[data-totp-bar]').setAttribute('aria-valuenow', String(Math.ceil(left)));
      $('[data-totp-bar]').classList.toggle('ending', left <= 5);
    }
  }
  timer = setTimeout(tick, 1000 - (Date.now() % 1000) + 20);
}

function loadJsQr() {
  if (window.jsQR) return Promise.resolve(window.jsQR);
  jsQrLoading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = JSQR;
    script.onload = () => resolve(window.jsQR);
    script.onerror = () => {
      jsQrLoading = null;
      reject(new Error('The QR reader could not be loaded.'));
    };
    document.head.append(script);
  });
  return jsQrLoading;
}

async function nativeQr(bitmap) {
  if (!('BarcodeDetector' in window)) return null;
  try {
    const formats = await window.BarcodeDetector.getSupportedFormats();
    if (!formats.includes('qr_code')) return null;
    const found = await new window.BarcodeDetector({ formats: ['qr_code'] }).detect(bitmap);
    return found[0]?.rawValue ?? null;
  } catch (e) {
    return null;
  }
}

async function libraryQr(bitmap) {
  const jsQR = await loadJsQr();
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const context = canvas.getContext('2d', { willReadFrequently: true });
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
  return jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'attemptBoth' })?.data ?? null;
}

async function readQr(file) {
  if (!file || !file.type.startsWith('image/')) return;
  show($('[data-totp-error]'), '');
  $('[data-totp-label]').hidden = false;
  $('[data-totp-label]').textContent = 'Reading the QR code…';
  let text = null;
  try {
    const bitmap = await createImageBitmap(file);
    text = (await nativeQr(bitmap)) ?? (await libraryQr(bitmap));
  } catch (e) {
    show($('[data-totp-error]'), e.message || 'The image could not be read.');
    $('[data-totp-label]').hidden = true;
    return;
  }
  if (!text) {
    show($('[data-totp-error]'), 'No QR code found in that image. Try a sharper screenshot of just the code.');
    $('[data-totp-label]').hidden = true;
    return;
  }
  $('[data-totp-input]').value = text;
  read(true);
}

$('[data-totp-input]').addEventListener('input', () => read(true));
['[data-totp-digits]', '[data-totp-period]', '[data-totp-algorithm]'].forEach((sel) => $(sel).addEventListener('change', () => read(false)));
$('[data-totp-period]').addEventListener('input', () => read(false));
$('[data-totp-qr]').addEventListener('change', (e) => readQr(e.target.files?.[0]));
document.addEventListener('paste', (e) => {
  const file = [...(e.clipboardData?.files ?? [])].find((f) => f.type.startsWith('image/'));
  if (!file) return;
  e.preventDefault();
  readQr(file);
});
$('[data-totp-new]').addEventListener('click', () => {
  $('[data-totp-input]').value = randomSecret();
  $('[data-totp-digits]').value = '6';
  $('[data-totp-period]').value = '30';
  $('[data-totp-algorithm]').value = 'SHA1';
  read(true);
});
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) tick();
});

read(true);
wireCopyButtons();
