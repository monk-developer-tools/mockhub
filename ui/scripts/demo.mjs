const base = (process.argv[2] ?? 'http://localhost:8080').replace(/\/$/, '');
const api = `${base}/__admin/mockhub/api`;
const password = process.env.DEMO_PASSWORD ?? 'demo-password-1';

async function call(token, method, path, body) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
  return response.status === 204 ? undefined : response.json();
}

async function hit(method, path, body) {
  await fetch(`${base}${path}`, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
}

const people = [
  { username: 'jordan', displayName: 'Jordan Blake', email: 'jordan@acme.example', team: 'Checkout', role: 'editor' },
  { username: 'maria', displayName: 'Maria Lopez', email: 'maria@acme.example', team: 'Checkout', role: 'manager' },
  { username: 'li', displayName: 'Li Wei', email: 'li@acme.example', team: 'Platform', role: 'editor' },
  { username: 'sam', displayName: 'Sam Patel', email: 'sam@acme.example', team: 'Mobile app', role: 'editor' },
  { username: 'priya', displayName: 'Priya Raman', email: 'priya@acme.example', team: 'QA', role: 'viewer' },
];

const teams = [
  { name: 'Checkout', description: 'Cart, payment and order pages' },
  { name: 'Mobile app', description: 'iOS and Android apps' },
  { name: 'Platform', description: 'Shared services and examples' },
  { name: 'QA', description: 'End-to-end and performance testing' },
];

const examplesByTeam = {
  'stateful-cart': 'Checkout',
  webhooks: 'Checkout',
  'fake-data': 'Mobile app',
  chaos: 'QA',
};

const admin = await call(null, 'POST', '/users', { username: 'alex', displayName: 'Alex Morgan', email: 'alex@acme.example', password, serverRole: 'admin' });
const token = admin.token;
const [org] = await call(token, 'GET', '/orgs');
await call(token, 'PUT', `/orgs/${org.id}`, { name: 'Acme', description: 'Online shop and its back-office services' });

const teamIds = {};
for (const team of teams) teamIds[team.name] = (await call(token, 'POST', `/orgs/${org.id}/teams`, team)).id;

const userIds = {};
for (const person of people) {
  const user = await call(token, 'POST', '/users', { username: person.username, displayName: person.displayName, email: person.email, password, serverRole: 'none' });
  userIds[person.username] = user.id;
  await call(token, 'PUT', `/orgs/${org.id}/teams/${teamIds[person.team]}/members/${user.id}`, { role: person.role });
}

await call(token, 'POST', '/users', { username: 'taylor', displayName: 'Taylor Kim', email: 'taylor@acme.example', password, serverRole: 'none' });

const examples = await call(token, 'GET', '/examples');
const projects = {};
for (const example of examples) {
  const project = await call(token, 'POST', `/examples/${example.id}/install?orgId=${org.id}`);
  const teamId = teamIds[examplesByTeam[example.id] ?? 'Platform'];
  projects[example.id] = await call(token, 'PUT', `/projects/${project.id}`, { name: project.name, basePath: project.basePath, color: project.color, description: project.description, teamId });
}

const customers = await call(token, 'POST', '/projects', {
  name: 'Customers API',
  basePath: '/customers',
  description: 'Customer records for checkout, support and the mobile app.',
  color: '#7c3aed',
  orgId: org.id,
  teamId: teamIds.Checkout,
});
await call(token, 'POST', `/projects/${customers.id}/crud`, {
  resource: 'customers',
  idField: 'id',
  idType: 'number',
  seed: [
    { name: 'Ada Lovelace', email: 'ada@example.com', tier: 'gold' },
    { name: 'Grace Hopper', email: 'grace@example.com', tier: 'silver' },
  ],
});

const as = async (username) => (await call(null, 'POST', '/auth/login', { username, password })).token;
const maria = await as('maria');
const li = await as('li');
const cartStubs = await call(maria, 'GET', `/projects/${projects['stateful-cart']?.id}/stubs`).catch(() => []);
if (cartStubs[0]) await call(maria, 'PUT', `/projects/${projects['stateful-cart'].id}/stubs/${cartStubs[0].id}`, { ...cartStubs[0], name: `${cartStubs[0].name ?? 'Cart'} (reviewed)` });
await call(maria, 'PUT', `/projects/${customers.id}`, { name: customers.name, basePath: customers.basePath, color: customers.color, teamId: customers.teamId, description: 'Customer records for checkout, support and the mobile app. Ask Maria before changing the schema.' });
const petStubs = await call(li, 'GET', `/projects/${projects['rest-basics'].id}/stubs`);
const getPet = petStubs.find((stub) => stub.name === 'Get pet by id');
if (getPet) await call(li, 'PUT', `/projects/${projects['rest-basics'].id}/stubs/${getPet.id}`, { ...getPet, name: 'Get a pet by id' });

const pet = projects['rest-basics'];
if (pet) {
  for (const path of ['/pets', '/pets/7', '/pets/999', '/pets?status=sold', '/owners', '/pets/7', '/pets']) await hit('GET', `${pet.basePath}${path}`);
}
const hooks = projects.webhooks;
if (hooks) {
  await hit('POST', `${hooks.basePath}/payments`, { amount: 40, reference: 'ord_1001', callbackUrl: `${base}${hooks.basePath}/partner/callbacks` });
  await hit('POST', `${hooks.basePath}/cards/check`, { card: '4242' });
  await hit('POST', `${hooks.basePath}/refunds`, { reference: 'ord_1001', amount: 10 });
}

console.log(JSON.stringify({ admin: 'alex', password, org: org.id, projects: Object.fromEntries(Object.entries(projects).map(([k, v]) => [k, v.id])), customers: customers.id }, null, 2));
