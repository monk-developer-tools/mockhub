import { execFileSync, spawn } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { connect, sleep } from './cdp.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const jar = join(root, 'build/libs/mockhub.jar');
const out = resolve(process.argv[2] ?? join(root, 'website/assets/img/shots'));
const password = 'demo-password-1';

const contract = `openapi: 3.0.3
info: { title: Petstore, version: "1.4" }
components:
  schemas:
    Pet:
      type: object
      required: [id, name, status]
      properties:
        id: { type: integer }
        name: { type: string }
        type: { type: string }
        status: { type: string, enum: [available, pending, sold] }
paths:
  /pets:
    get:
      parameters:
        - { name: status, in: query, schema: { type: string } }
      responses:
        '200':
          description: The pets
          content:
            application/json:
              schema: { type: array, items: { $ref: '#/components/schemas/Pet' } }
    post:
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Pet' }
  /pets/{id}:
    get:
      parameters:
        - { name: id, in: path, required: true, schema: { type: integer } }
      responses:
        '200':
          description: One pet
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Pet' }
    delete:
      parameters:
        - { name: id, in: path, required: true, schema: { type: integer } }
      responses:
        '204': { description: Deleted }
`;

const freePort = () => new Promise((r) => {
  const server = createServer();
  server.listen(0, () => {
    const { port } = server.address();
    server.close(() => r(port));
  });
});

async function waitFor(url) {
  for (let i = 0; i < 120; i++) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {
      await sleep(500);
    }
  }
  throw new Error(`${url} did not answer`);
}

const port = await freePort();
const httpsPort = await freePort();
const base = `http://localhost:${port}`;
const api = `${base}/__admin/mockhub/api`;
const data = mkdtempSync(join(tmpdir(), 'mockhub-feature-shots-'));
const children = [];
let stopped = false;
const stop = () => {
  if (stopped) return;
  stopped = true;
  children.forEach((c) => c.kill());
  try {
    rmSync(data, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
  } catch {
    console.error(`Left ${data} behind`);
  }
};
process.on('exit', stop);

children.push(spawn('java', ['-jar', jar], { env: { ...process.env, PORT: String(port), MOCKHUB_DATA_DIR: data, MOCKHUB_PROXY: 'true', MOCKHUB_HTTPS_PORT: String(httpsPort) }, stdio: 'ignore' }));
await waitFor(`${base}/__admin/health`);
const seeded = JSON.parse(execFileSync('node', [join(root, 'ui/scripts/demo.mjs'), base], { encoding: 'utf8' }));
const pet = seeded.projects['rest-basics'];

async function call(token, method, path, body) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
  return response.status === 204 ? undefined : response.json();
}

const token = (await call(null, 'POST', '/auth/login', { username: 'alex', password })).token;
const project = await call(token, 'GET', `/projects/${pet}`);
await call(token, 'PUT', `/projects/${pet}/contract`, { content: contract, source: 'upload', fileName: 'petstore.yaml' });
const traffic = [
  ['GET', '/pets/7/photos'], ['GET', '/pets/999'], ['DELETE', '/pets/3'], ['DELETE', '/pets/3', { Authorization: 'Bearer demo' }],
  ['GET', '/pets/12'], ['GET', '/pets?status=sold'],
];
for (const [method, path, headers] of traffic) await fetch(`${base}${project.basePath}${path}`, { method, headers });
await call(token, 'POST', `/projects/${pet}/drift/check`);
await call(token, 'PUT', `/proxy`, { projectId: pet });

const secret = (await call(token, 'POST', '/auth/tokens', { name: 'Laptop tunnel', role: 'editor', mode: 'direct', projectIds: [], expiresInDays: 30 })).secret;
for (const name of ['laptop', 'payments-dev']) {
  children.push(spawn('java', ['-jar', jar, 'tunnel', '--server', base, '--token', secret, '--name', name, '--to', `http://localhost:${name === 'laptop' ? 3000 : 4010}`], { stdio: 'ignore' }));
}
for (let i = 0; i < 40 && (await call(token, 'GET', '/tunnels')).length < 2; i++) await sleep(500);

const org = seeded.org;
const csv = 'sku,name,price,stock\nsku-1,Blue mug,12.5,3\nsku-2,Green teapot,34,12\nsku-3,Oak tray,22,0\nsku-4,Linen napkins,9.9,48\nsku-5,Stone bowl,18,7\n';
await call(token, 'POST', `/orgs/${org}/datasets`, { key: 'products', name: 'Products', description: 'Shop catalogue used by the cart and search mocks', identity: ['sku'], indexes: ['name'] });
const upload = await fetch(`${api}/orgs/${org}/datasets/products/upload?format=csv&mode=replace`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'text/plain' }, body: csv });
if (!upload.ok) throw new Error(`dataset upload: ${upload.status} ${await upload.text()}`);
await call(token, 'POST', `/orgs/${org}/connections`, { key: 'payments-sandbox', name: 'Payments sandbox', type: 'oauth2', tokenUrl: 'https://auth.payments.example/oauth2/token', clientId: 'mockhub-callouts', scope: 'payments.write', clientAuth: 'basic', headerName: 'Authorization', headerPrefix: 'Bearer ', secret: 'demo-secret' });
await call(token, 'POST', `/orgs/${org}/connections`, { key: 'partner-api-key', name: 'Partner API key', type: 'header', headerName: 'X-Api-Key', headerPrefix: '', secret: 'demo-key' });
await call(token, 'PUT', `/projects/${pet}`, { name: project.name, basePath: project.basePath, color: project.color, description: project.description, teamId: project.teamId, domains: ['petstore.acme.test', 'pets.staging.acme.test'] });
const echo = seeded.projects.echo;
for (const [method, path] of [['GET', '/orders/42?expand=items'], ['POST', '/payments'], ['DELETE', '/carts/7']]) {
  await fetch(`${base}/echo${path}`, { method, headers: method === 'POST' ? { 'Content-Type': 'application/json' } : {}, body: method === 'POST' ? '{"amount":40}' : undefined });
}
const stubOf = async (projectId, pick) => (await call(token, 'GET', `/projects/${projectId}/stubs`)).find(pick);
const echoStub = await stubOf(echo, (stub) => stub.name === 'Echo · any request');
const graphqlStub = await stubOf(seeded.projects['graphql-library'], (stub) => !String(stub.name).includes('·'));
const grpcStub = await stubOf(seeded.projects['grpc-inventory'], (stub) => stub.name === 'GetItem · any sku');

const ui = `${base}/__admin/mockhub/ui`;
const scrollTo = (text) => `const h = [...document.querySelectorAll('h2, h3')].find((e) => e.textContent.trim().startsWith(${JSON.stringify(text)})); h.style.scrollMarginTop = '76px'; h.scrollIntoView({ block: 'start' });`;
const openAccount = (tab) => `document.querySelector('[aria-label="Your account"]').click(); await new Promise((r) => setTimeout(r, 600)); [...document.querySelectorAll('[role=tab]')].find((t) => t.textContent === ${JSON.stringify(tab)}).click();`;
const all = [
  { name: 'contract', path: `/projects/${pet}/contract` },
  { name: 'tunnels', path: '/', then: openAccount('Tunnels') },
  { name: 'forward-proxy', path: '/server', then: scrollTo('Forward proxy') },
  { name: 'mtls', path: '/server', then: scrollTo('HTTPS and client certificates') },
  { name: 'access-tokens', path: '/', then: openAccount('Access tokens') },
  { name: 'datasets', path: '/org/datasets' },
  { name: 'connections', path: '/org/connections' },
  { name: 'custom-domains', path: `/projects/${pet}/settings`, then: scrollTo('Custom domains') },
  { name: 'echo', path: `/projects/${echo}/stubs/${echoStub?.id}`, then: scrollTo('Response') },
  { name: 'graphql', path: `/projects/${seeded.projects['graphql-library']}/stubs/${graphqlStub?.id}`, then: scrollTo('GraphQL console') },
  { name: 'grpc', path: `/projects/${seeded.projects['grpc-inventory']}/stubs/${grpcStub?.id}` },
];
const only = (process.env.SHOTS ?? '').split(',').filter(Boolean);
const shots = only.length ? all.filter((shot) => only.includes(shot.name)) : all;

const { send, close } = await connect();
await send('Page.enable');
for (const theme of ['light', 'dark']) {
  for (const scale of [1, 2]) {
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: scale, mobile: false });
    await send('Page.navigate', { url: `${ui}/` });
    await sleep(1500);
    await send('Runtime.evaluate', { expression: `sessionStorage.setItem('mockhub.auth', 'Bearer ${token}'); localStorage.setItem('mockhub.theme', '${theme}'); localStorage.setItem('mockhub.sidebar', 'expanded')` });
    for (const shot of shots) {
      await send('Page.navigate', { url: `${ui}${shot.path}` });
      await sleep(2200);
      if (shot.then) {
        await send('Runtime.evaluate', { expression: `(async () => { ${shot.then} })()`, awaitPromise: true });
        await sleep(1500);
      }
      const png = join(data, `${shot.name}.png`);
      writeFileSync(png, Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));
      const file = join(out, `${shot.name}-${theme}${scale === 2 ? '@2x' : ''}.webp`);
      execFileSync('cwebp', ['-quiet', '-q', '82', png, '-o', file]);
      console.log(file);
    }
  }
}
close();
stop();
process.exit(0);
