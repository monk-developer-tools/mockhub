import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'examples');

export const SANDBOXES = [
  {
    file: 'sandbox-github.json',
    source: 'https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.json',
    project: 'https://github.com/github/rest-api-description',
    copyright: 'Copyright (c) 2020 GitHub',
    title: 'GitHub REST API sandbox',
    operations: ['users/get-authenticated', 'users/get-by-username', 'repos/list-for-authenticated-user', 'repos/get', 'issues/list-for-repo',
      'issues/create', 'issues/get', 'issues/update', 'issues/create-comment', 'pulls/list', 'pulls/get', 'repos/list-commits'],
  },
  {
    file: 'sandbox-stripe.json',
    source: 'https://raw.githubusercontent.com/stripe/openapi/master/openapi/spec3.json',
    project: 'https://github.com/stripe/openapi',
    copyright: 'Copyright (c) 2011- Stripe, Inc.',
    title: 'Stripe API sandbox',
    operations: ['PostCustomers', 'GetCustomers', 'GetCustomersCustomer', 'PostCustomersCustomer', 'DeleteCustomersCustomer', 'PostPaymentIntents',
      'GetPaymentIntentsIntent', 'PostPaymentIntentsIntentConfirm', 'GetCharges', 'PostRefunds', 'GetBalance'],
  },
  {
    file: 'sandbox-twilio.json',
    source: 'https://raw.githubusercontent.com/twilio/twilio-oai/main/spec/json/twilio_api_v2010.json',
    project: 'https://github.com/twilio/twilio-oai',
    copyright: 'Copyright (C) 2023, Twilio, Inc.',
    title: 'Twilio Messaging and Voice API sandbox',
    operations: ['CreateMessage', 'FetchMessage', 'ListMessage', 'CreateCall', 'FetchCall'],
  },
];

const METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options'];

function refsIn(node, found) {
  if (Array.isArray(node)) node.forEach((n) => refsIn(n, found));
  else if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      if (key === '$ref' && typeof value === 'string' && value.startsWith('#/components/')) found.add(value);
      else refsIn(value, found);
    }
  }
  return found;
}

const pointer = (spec, ref) => ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~')).reduce((n, p) => n?.[p], spec);

export function extract(spec, sandbox) {
  const paths = {};
  const missing = new Set(sandbox.operations);
  for (const [path, item] of Object.entries(spec.paths)) {
    for (const method of METHODS) {
      const op = item[method];
      if (!op || !sandbox.operations.includes(op.operationId)) continue;
      missing.delete(op.operationId);
      paths[path] ??= item.parameters ? { parameters: item.parameters } : {};
      const { security, ...kept } = op;
      paths[path][method] = kept;
    }
  }
  if (missing.size) throw new Error(`${sandbox.file}: operations not in the spec: ${[...missing].join(', ')}`);
  const components = {};
  const queue = [...refsIn(paths, new Set())];
  const seen = new Set();
  while (queue.length) {
    const ref = queue.pop();
    if (seen.has(ref)) continue;
    seen.add(ref);
    const [, , kind, name] = ref.split('/');
    const value = pointer(spec, ref);
    if (value === undefined) throw new Error(`${sandbox.file}: ${ref} is not in the spec`);
    (components[kind] ??= {})[name] = value;
    refsIn(value, new Set()).forEach((r) => queue.push(r));
  }
  return {
    openapi: spec.openapi,
    info: {
      title: sandbox.title,
      version: spec.info?.version ?? '1',
      description: `An unofficial MockHub sandbox with ${sandbox.operations.length} operations taken from ${sandbox.project}. ${sandbox.copyright}. Used under the MIT License. Not affiliated with or endorsed by the API's owner.`,
      license: { name: 'MIT', url: `${sandbox.project}/blob/HEAD/LICENSE` },
    },
    servers: spec.servers,
    paths,
    components,
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const local = process.argv[2];
  for (const sandbox of SANDBOXES) {
    const text = local ? readFileSync(join(local, sandbox.source.split('/').pop()), 'utf8') : await (await fetch(sandbox.source)).text();
    const subset = extract(JSON.parse(text), sandbox);
    writeFileSync(join(out, sandbox.file), `${JSON.stringify(subset, null, 1)}\n`);
    console.log(`${sandbox.file}: ${Object.keys(subset.paths).length} paths, ${Object.values(subset.components).reduce((n, c) => n + Object.keys(c).length, 0)} components, ${Buffer.byteLength(JSON.stringify(subset))} bytes`);
  }
}
