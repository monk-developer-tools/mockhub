import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(repo, 'src', 'main', 'resources', 'examples');

export const SANDBOXES = [
  {
    file: 'sandbox-github.json',
    project: 'https://github.com/github/rest-api-description',
    copyright: 'Copyright (c) 2020 GitHub',
    title: 'GitHub REST API sandbox',
    sources: [{
      url: 'https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.json',
      operations: ['users/get-authenticated', 'users/get-by-username', 'repos/list-for-authenticated-user', 'repos/get', 'issues/list-for-repo',
        'issues/create', 'issues/get', 'issues/update', 'issues/create-comment', 'pulls/list', 'pulls/get', 'repos/list-commits'],
    }],
  },
  {
    file: 'sandbox-stripe.json',
    project: 'https://github.com/stripe/openapi',
    copyright: 'Copyright (c) 2011- Stripe, Inc.',
    title: 'Stripe API sandbox',
    sources: [{
      url: 'https://raw.githubusercontent.com/stripe/openapi/master/openapi/spec3.json',
      operations: ['PostCustomers', 'GetCustomers', 'GetCustomersCustomer', 'PostCustomersCustomer', 'DeleteCustomersCustomer', 'PostPaymentIntents',
        'GetPaymentIntentsIntent', 'PostPaymentIntentsIntentConfirm', 'GetCharges', 'PostRefunds', 'GetBalance'],
    }],
  },
  {
    file: 'sandbox-twilio.json',
    project: 'https://github.com/twilio/twilio-oai',
    copyright: 'Copyright (C) 2023, Twilio, Inc.',
    title: 'Twilio Messaging and Voice API sandbox',
    sources: [{
      url: 'https://raw.githubusercontent.com/twilio/twilio-oai/main/spec/json/twilio_api_v2010.json',
      operations: ['CreateMessage', 'FetchMessage', 'ListMessage', 'CreateCall', 'FetchCall'],
    }],
  },
  {
    file: 'sandbox-ups.json',
    project: 'https://github.com/UPS-API/api-documentation',
    copyright: 'Copyright (c) 2023 UPS-API',
    title: 'UPS Tracking, Rating, Address Validation and Time in Transit API sandbox',
    sources: [
      { url: 'https://raw.githubusercontent.com/UPS-API/api-documentation/main/Tracking.yaml', operations: ['getSingleTrackResponseUsingGET'] },
      { url: 'https://raw.githubusercontent.com/UPS-API/api-documentation/main/Rating.yaml', operations: ['Rate'] },
      { url: 'https://raw.githubusercontent.com/UPS-API/api-documentation/main/AddressValidation.yaml', operations: ['AddressValidation'] },
      { url: 'https://raw.githubusercontent.com/UPS-API/api-documentation/main/TimeInTransit.yaml', operations: ['TimeInTransit'] },
    ],
  },
  {
    file: 'sandbox-amadeus.json',
    project: 'https://github.com/amadeus4dev/amadeus-open-api-specification',
    copyright: 'Copyright (c) 2017 Amadeus IT Group SA',
    title: 'Amadeus Self-Service flight booking API sandbox',
    sources: [
      { url: 'https://raw.githubusercontent.com/amadeus4dev/amadeus-open-api-specification/main/spec/json/AirportCitySearch_v1_swagger_specification.json', operations: ['getAirportCitySearch', 'getAirportCity'] },
      { url: 'https://raw.githubusercontent.com/amadeus4dev/amadeus-open-api-specification/main/spec/json/FlightOffersSearch_v2_swagger_specification.json', operations: ['getFlightOffers', 'searchFlightOffers'] },
      { url: 'https://raw.githubusercontent.com/amadeus4dev/amadeus-open-api-specification/main/spec/json/FlightOffersPrice_v1_swagger_specification.json', operations: ['quoteAirOffers'] },
      { url: 'https://raw.githubusercontent.com/amadeus4dev/amadeus-open-api-specification/main/spec/json/FlightCreateOrders_v1_swagger_specification.json', operations: ['createFligtOrders'] },
      { url: 'https://raw.githubusercontent.com/amadeus4dev/amadeus-open-api-specification/main/spec/json/FlightOrderManagement_v1_swagger_specification.json', operations: ['getFlightOrder', 'cancelFlightOrder'] },
    ],
  },
];

const METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options'];
const isSwagger2 = (spec) => String(spec.swagger ?? '').startsWith('2');
const LOCAL = /^#\/(components\/[^/]+|definitions|parameters|responses)\/[^/]+$/;

function refsIn(node, found) {
  if (Array.isArray(node)) node.forEach((n) => refsIn(n, found));
  else if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      if (key === '$ref' && typeof value === 'string' && LOCAL.test(value)) found.add(value);
      else refsIn(value, found);
    }
  }
  return found;
}

function renameRefs(node, renames) {
  if (Array.isArray(node)) return node.map((n) => renameRefs(n, renames));
  if (node && typeof node === 'object') {
    return Object.fromEntries(Object.entries(node).map(([key, value]) => [key,
      key === '$ref' && typeof value === 'string' && renames.has(value) ? renames.get(value) : renameRefs(value, renames)]));
  }
  return node;
}

const pointer = (spec, ref) => ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~')).reduce((n, p) => n?.[p], spec);
const section = (ref) => ref.slice(2, ref.lastIndexOf('/'));
const nameOf = (ref) => ref.slice(ref.lastIndexOf('/') + 1);

export function extractSource(spec, operations, label) {
  const paths = {};
  const missing = new Set(operations);
  const prefix = isSwagger2(spec) && spec.basePath && spec.basePath !== '/' ? spec.basePath.replace(/\/$/, '') : '';
  for (const [path, item] of Object.entries(spec.paths)) {
    for (const method of METHODS) {
      const op = item[method];
      if (!op || !operations.includes(op.operationId)) continue;
      missing.delete(op.operationId);
      const key = prefix + path;
      paths[key] ??= item.parameters ? { parameters: item.parameters } : {};
      const { security, ...kept } = op;
      paths[key][method] = kept;
    }
  }
  if (missing.size) throw new Error(`${label}: operations not in the spec: ${[...missing].join(', ')}`);
  const parts = new Map();
  const queue = [...refsIn(paths, new Set())];
  while (queue.length) {
    const ref = queue.pop();
    if (parts.has(ref)) continue;
    const value = pointer(spec, ref);
    if (value === undefined) throw new Error(`${label}: ${ref} is not in the spec`);
    parts.set(ref, value);
    refsIn(value, new Set()).forEach((r) => queue.push(r));
  }
  return { paths, parts };
}

export function merge(extracted, tags) {
  const parts = new Map();
  const paths = {};
  extracted.forEach((source, i) => {
    const renames = new Map();
    for (const [ref, value] of source.parts) {
      const existing = parts.get(ref);
      if (existing === undefined || JSON.stringify(existing) === JSON.stringify(value)) continue;
      let candidate = `#/${section(ref)}/${nameOf(ref)}${tags[i]}`;
      for (let n = 2; parts.has(candidate); n++) candidate = `#/${section(ref)}/${nameOf(ref)}${tags[i]}${n}`;
      renames.set(ref, candidate);
    }
    for (const [ref, value] of source.parts) parts.set(renames.get(ref) ?? ref, renameRefs(value, renames));
    for (const [path, item] of Object.entries(renameRefs(source.paths, renames))) {
      if (paths[path]) {
        for (const [method, op] of Object.entries(item)) {
          if (method !== 'parameters' && paths[path][method]) throw new Error(`${method.toUpperCase()} ${path} comes from two sources`);
          paths[path][method] = op;
        }
      } else {
        paths[path] = item;
      }
    }
  });
  return { paths, parts };
}

function nest(parts) {
  const out = {};
  for (const [ref, value] of [...parts.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    const keys = ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
    let node = out;
    keys.slice(0, -1).forEach((k) => { node = node[k] ??= {}; });
    node[keys[keys.length - 1]] = value;
  }
  return out;
}

export function extract(specs, sandbox) {
  const labels = sandbox.sources.map((s) => s.url.split('/').pop());
  const tags = labels.map((l) => l.replace(/\.(json|ya?ml)$/i, '').replace(/_swagger_specification$/i, '').replace(/[^A-Za-z0-9]/g, ''));
  const extracted = specs.map((spec, i) => extractSource(spec, sandbox.sources[i].operations, `${sandbox.file} (${labels[i]})`));
  const swagger = specs.map(isSwagger2);
  if (new Set(swagger).size > 1) throw new Error(`${sandbox.file}: mixes Swagger 2 and OpenAPI 3 sources`);
  const { paths, parts } = merge(extracted, tags);
  const count = sandbox.sources.reduce((n, s) => n + s.operations.length, 0);
  const info = {
    title: sandbox.title,
    version: specs[0].info?.version ?? '1',
    description: `An unofficial MockHub sandbox with ${count} operations taken from ${sandbox.project}. ${sandbox.copyright}. Used under the MIT License. Not affiliated with or endorsed by the API's owner.`,
    license: { name: 'MIT', url: `${sandbox.project}/blob/HEAD/LICENSE` },
  };
  const nested = nest(parts);
  if (swagger[0]) {
    const first = specs[0];
    return { swagger: '2.0', info, host: first.host, basePath: '/', schemes: first.schemes, consumes: first.consumes, produces: first.produces, paths, ...nested };
  }
  return { openapi: specs[0].openapi, info, servers: specs[0].servers, paths, components: nested.components ?? {} };
}

function parse(text, name) {
  if (/\.ya?ml$/i.test(name)) return createRequire(join(repo, 'ui', 'package.json'))('yaml').parse(text);
  return JSON.parse(text);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const local = process.argv[2];
  const only = process.argv[3];
  for (const sandbox of SANDBOXES.filter((s) => !only || s.file === only)) {
    const specs = [];
    for (const source of sandbox.sources) {
      const name = source.url.split('/').pop();
      const text = local ? readFileSync(join(local, name), 'utf8') : await (await fetch(source.url)).text();
      specs.push(parse(text, name));
    }
    const subset = extract(specs, sandbox);
    writeFileSync(join(out, sandbox.file), `${JSON.stringify(subset, null, 1)}\n`);
    const count = [...Object.values(subset.components ?? {}), subset.definitions ?? {}, subset.parameters ?? {}, subset.responses ?? {}]
      .reduce((n, c) => n + Object.keys(c).length, 0);
    console.log(`${sandbox.file}: ${Object.keys(subset.paths).length} paths, ${count} components, ${Buffer.byteLength(JSON.stringify(subset))} bytes`);
  }
}
