import type { CrudRequest, Stub } from '../api/types';

export type StartKind = 'openapi' | 'wsdl' | 'graphql' | 'grpc' | 'crud' | 'json' | 'webhook' | 'proxy' | 'echo' | 'postman' | 'ai';

export const START_KINDS: StartKind[] = ['openapi', 'wsdl', 'graphql', 'grpc', 'crud', 'json', 'webhook', 'proxy', 'echo', 'postman', 'ai'];

export interface StartCard {
  id: string;
  title: string;
  blurb: string;
  to?: string;
  href?: string;
  kind?: StartKind;
  needsAi?: boolean;
  createsProject?: boolean;
}

export interface StartGroup {
  id: string;
  title: string;
  lede: string;
  cards: StartCard[];
}

export const START_GROUPS: StartGroup[] = [
  {
    id: 'mock-servers',
    title: 'Mock servers',
    lede: 'Start from what you have. Each one makes a working mock you can call straight away.',
    cards: [
      { id: 'openapi', kind: 'openapi', to: '/new/openapi', title: 'OpenAPI or Swagger', blurb: 'Upload, link or paste a spec. One stub per operation, with example answers.' },
      { id: 'wsdl', kind: 'wsdl', to: '/new/wsdl', title: 'SOAP from WSDL', blurb: 'A stub per operation, the ?wsdl endpoint and a fault for anything else.' },
      { id: 'graphql', kind: 'graphql', to: '/new/graphql', title: 'GraphQL', blurb: 'One /graphql endpoint that answers every query and mutation in your schema.' },
      { id: 'grpc', kind: 'grpc', to: '/new/grpc', title: 'gRPC', blurb: 'Give it a .proto file. Every method answers with a message of the right type.' },
      { id: 'crud', kind: 'crud', to: '/new/crud', title: 'CRUD REST API', blurb: 'A REST resource that saves records: list, filter, get, create, update, delete.' },
      { id: 'json', kind: 'json', to: '/new/json', title: 'JSON or XML answer', blurb: 'Paste the response you want and choose the path. One stub, ready to call.' },
      { id: 'postman', kind: 'postman', to: '/new/postman', title: 'Postman collection', blurb: 'Every saved example response becomes a stub.' },
      { id: 'examples', to: '/examples', title: 'Ready-made mocks', blurb: 'Examples of every feature, and GitHub, Stripe, Twilio, UPS, Amadeus and DHL sandboxes.' },
      { id: 'empty', title: 'Empty project', blurb: 'Start from nothing and add stubs yourself.', createsProject: true },
    ],
  },
  {
    id: 'tools',
    title: 'Tools',
    lede: 'Catch, watch and generate traffic and data.',
    cards: [
      { id: 'csv', kind: 'crud', to: '/new/crud?from=csv', title: 'CSV to REST API', blurb: 'Upload CSV or JSON records and get a REST API that saves changes.' },
      { id: 'webhook', kind: 'webhook', to: '/new/webhook', title: 'Webhook testing', blurb: 'An endpoint that catches webhooks, or a stub that calls your service back.' },
      { id: 'proxy', kind: 'proxy', to: '/new/proxy', title: 'HTTP traffic monitoring', blurb: 'Put MockHub in front of a service: every call goes through and is recorded.' },
      { id: 'echo', kind: 'echo', to: '/new/echo', title: 'HTTP echo', blurb: 'An endpoint that answers with the request it received.' },
      { id: 'tunnel', kind: 'proxy', to: '/new/proxy?target=tunnel', title: 'Local tunnel', blurb: 'Reach a service on your laptop through MockHub, and record its traffic.' },
      { id: 'test-data', href: '/tools/fake-data/', title: 'Generate test data', blurb: 'Realistic names, addresses and more as JSON, CSV or SQL.' },
      { id: 'totp', href: '/tools/totp/', title: '2FA code generator', blurb: 'TOTP codes from a secret, for tests of login screens.' },
    ],
  },
  {
    id: 'virtualization',
    title: 'Service virtualization',
    lede: 'Ways teams use mocks, and where each one starts.',
    cards: [
      { id: 'ai', kind: 'ai', to: '/new/ai', title: 'AI for API virtualization', blurb: 'Describe an API and the assistant drafts the stubs for you to review.', needsAi: true },
      { id: 'chaos', to: '/examples', title: 'Performance and chaos testing', blurb: 'Add latency, failures and rate limits. Start from the Resilience testing example.' },
      { id: 'teams', to: '/org', title: 'Test environments for teams', blurb: 'Projects per team and environment, with roles, snapshots and an audit log.' },
      { id: 'contract', href: '/docs/contract-drift/', title: 'Contract checks', blurb: 'Compare real traffic with an OpenAPI document and see where it drifts.' },
      { id: 'ci', href: '/docs/api/', title: 'API tests in CI', blurb: 'Load stubs over the API with an access token and check unmatched calls.' },
    ],
  },
];

export const IMPORT_FORMAT: Partial<Record<StartKind, string>> = { openapi: 'openapi', wsdl: 'wsdl', graphql: 'graphql', postman: 'postman' };

export const isStartKind = (value: string | undefined): value is StartKind => START_KINDS.includes(value as StartKind);

export const cardFor = (kind: StartKind): StartCard =>
  START_GROUPS.flatMap((g) => g.cards).find((c) => c.kind === kind && c.id === kind) ?? { id: kind, title: kind, blurb: '' };

const firstMatch = (text: string, pattern: RegExp) => pattern.exec(text)?.[1]?.trim() ?? '';

const fromFileName = (fileName?: string) =>
  (fileName ?? '').replace(/\.[^.]+$/, '').replace(/[._-]+/g, ' ').trim().replace(/\b\w/g, (c) => c.toUpperCase());

export function nameFromSource(kind: StartKind, text: string, fileName?: string): string {
  const source = String(text ?? '');
  let name = '';
  if (kind === 'openapi') {
    try {
      name = JSON.parse(source)?.info?.title ?? '';
    } catch {
      name = firstMatch(source, /^info:\s*\n(?:[ \t]+.*\n)*?[ \t]+title:\s*['"]?([^'"\n]+)['"]?/m);
    }
  } else if (kind === 'wsdl') {
    name = firstMatch(source, /<(?:\w+:)?(?:service|definitions)\b[^>]*\bname="([^"]+)"/);
  } else if (kind === 'grpc') {
    name = firstMatch(source, /^\s*service\s+(\w+)/m) || firstMatch(source, /^\s*package\s+([\w.]+)\s*;/m);
  } else if (kind === 'postman') {
    try {
      name = JSON.parse(source)?.info?.name ?? '';
    } catch {
      name = '';
    }
  }
  return (name || fromFileName(fileName) || '').slice(0, 80);
}

export interface PayloadStub {
  method: string;
  path: string;
  status: number;
  contentType: string;
  body: string;
}

export function normalPath(path: string): string {
  const trimmed = String(path ?? '').trim();
  if (!trimmed) throw new Error('Choose the path the mock answers on, like /orders.');
  if (/\s/.test(trimmed)) throw new Error('A path can\'t contain spaces.');
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

export function detectContentType(body: string): string {
  const text = body.trim();
  if (text.startsWith('{') || text.startsWith('[')) return 'application/json';
  if (text.startsWith('<')) return /<(\w+:)?Envelope\b/.test(text) ? 'text/xml' : 'application/xml';
  return 'text/plain';
}

export function payloadStub(input: PayloadStub): Stub {
  const path = normalPath(input.path);
  if (!Number.isInteger(input.status) || input.status < 100 || input.status > 599) throw new Error('The status must be between 100 and 599.');
  if (input.contentType === 'application/json' && input.body.trim()) {
    try {
      JSON.parse(input.body);
    } catch {
      throw new Error('The answer isn\'t valid JSON. Fix it, or choose another content type.');
    }
  }
  return {
    name: `${input.method} ${path}`,
    request: { method: input.method, urlPath: path },
    response: { status: input.status, headers: { 'Content-Type': input.contentType }, body: input.body },
  };
}

export function receiverStub(rawPath: string): Stub {
  const path = normalPath(rawPath).replace(/\/+$/, '') || '/';
  const pattern = `${path === '/' ? '' : path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(/.*)?`;
  return {
    name: `Webhook receiver ${path}`,
    request: { method: 'ANY', urlPathPattern: pattern },
    response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { received: true } },
    metadata: { mockhub: { folder: 'Webhooks', description: 'Answers 200 at once. Every webhook sent here shows up in the Requests tab with its headers and body.' } },
  };
}

export interface Callback {
  path: string;
  status: number;
  callbackUrl: string;
  delaySeconds: number;
}

export function callbackStub(input: Callback): Stub {
  const path = normalPath(input.path);
  const url = String(input.callbackUrl ?? '').trim();
  if (!/^https?:\/\/\S+$/.test(url) && !/^tunnel:\/\/\S+$/.test(url)) throw new Error('The callback URL must start with http://, https:// or tunnel://.');
  const delay = Number(input.delaySeconds);
  if (!Number.isFinite(delay) || delay < 0 || delay > 300) throw new Error('Wait between 0 and 300 seconds before calling back.');
  return {
    name: `POST ${path}, then call back`,
    request: { method: 'POST', urlPath: path },
    response: {
      status: input.status,
      headers: { 'Content-Type': 'application/json' },
      body: '{ "id": "{{randomValue type=\'UUID\'}}", "status": "PENDING" }',
      transformers: ['response-template'],
    },
    serveEventListeners: [{
      name: 'mockhub-callout',
      parameters: {
        method: 'POST',
        url,
        headers: { 'Content-Type': 'application/json' },
        body: '{ "event": "completed", "request": {{originalRequest.body}} }',
        delay: { type: 'fixed', milliseconds: Math.round(delay * 1000) },
      },
    }],
    metadata: { mockhub: { folder: 'Webhooks', description: `Answers ${input.status} at once, then POSTs the request body back to ${url} after ${delay} s. Every callback shows up in the Callouts tab.` } },
  };
}

export function proxyStub(rawTarget: string): Stub {
  const target = String(rawTarget ?? '').trim().replace(/\/+$/, '');
  if (!/^https?:\/\/[^\s/]+/.test(target) && !/^tunnel:\/\/[A-Za-z0-9][A-Za-z0-9-]{0,62}$/.test(target)) {
    throw new Error('Give the address of the service, like https://api.example.com, or tunnel://my-laptop for a local service.');
  }
  return {
    name: `Forward everything to ${target}`,
    priority: 10,
    request: { method: 'ANY' },
    response: { proxyBaseUrl: target },
    metadata: { mockhub: { description: 'Every call goes to the real service and is recorded in Requests. Add stubs with a higher priority to replace answers one at a time.' } },
  };
}

export function echoStub(rawPath: string): Stub {
  const path = normalPath(rawPath).replace(/\/+$/, '') || '/';
  return {
    name: `Echo ${path}`,
    request: { method: 'ANY', urlPathPattern: `${path === '/' ? '' : path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(/.*)?` },
    response: { status: 200, transformers: ['mockhub-echo'] },
    metadata: { mockhub: { description: 'Answers with JSON describing the request: method, path, query, headers and body.' } },
  };
}

export const tunnelCommand = (serverUrl: string, name: string) =>
  `MOCKHUB_TOKEN=mh_… java -jar mockhub.jar tunnel --server ${serverUrl} --name ${name} --to http://localhost:3000`;

export const RESOURCE = /^[a-z][a-z0-9-]{0,62}$/;

function csvRows(text: string): Record<string, string>[] {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  if (lines.length < 2) return [];
  const split = (line: string) => {
    const cells: string[] = [];
    let cell = '';
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (quoted && c === '"' && line[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        quoted = !quoted;
      } else if (c === ',' && !quoted) {
        cells.push(cell);
        cell = '';
      } else {
        cell += c;
      }
    }
    cells.push(cell);
    return cells.map((v) => v.trim());
  };
  const header = split(lines[0]);
  return lines.slice(1).map((line) => {
    const cells = split(line);
    return Object.fromEntries(header.map((h, i) => [h, cells[i] ?? '']));
  });
}

export function crudRequest(resource: string, recordsText: string): CrudRequest {
  const name = String(resource ?? '').trim();
  if (!RESOURCE.test(name)) throw new Error('Name the resource with lowercase letters, digits and dashes, like users or order-items.');
  const text = String(recordsText ?? '').trim();
  let seed: Record<string, unknown>[] = [];
  if (text) {
    if (text.startsWith('[') || text.startsWith('{')) {
      let parsed: unknown;
      try {
        parsed = JSON.parse(text);
      } catch {
        throw new Error('The records aren\'t valid JSON. Use a JSON array of objects, or CSV with a header row.');
      }
      const list = Array.isArray(parsed) ? parsed : [parsed];
      if (!list.every((r) => r && typeof r === 'object' && !Array.isArray(r))) throw new Error('Each record must be a JSON object.');
      seed = list as Record<string, unknown>[];
    } else {
      seed = csvRows(text);
      if (!seed.length) throw new Error('CSV needs a header row and at least one record.');
    }
  }
  if (seed.length > 1000) throw new Error('Start with up to 1,000 records.');
  const ids = seed.map((r) => r.id).filter((v) => v !== undefined && v !== null && v !== '');
  const idType = ids.length && ids.some((v) => !/^\d+$/.test(String(v))) ? 'uuid' : 'number';
  return seed.length ? { resource: name, idField: 'id', idType, seed } : { resource: name, idField: 'id', idType };
}

export function aiPrompt(description: string): string {
  const text = String(description ?? '').trim();
  if (text.length < 10) throw new Error('Describe the API in a sentence or two, like "An orders API with list, get and create".');
  return `Create the stubs for this API, with realistic example answers and an error case for each operation: ${text}`;
}

export const SAMPLES: Record<StartKind, { fileName: string; text: string }> = {
  openapi: {
    fileName: 'orders.yaml',
    text: `openapi: 3.0.3
info:
  title: Orders API
  version: 1.0.0
paths:
  /orders:
    get:
      responses:
        '200':
          description: All orders
          content:
            application/json:
              schema:
                type: array
                items: { $ref: '#/components/schemas/Order' }
  /orders/{id}:
    get:
      parameters:
        - { name: id, in: path, required: true, schema: { type: string } }
      responses:
        '200':
          description: One order
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }
        '404':
          description: Not found
components:
  schemas:
    Order:
      type: object
      properties:
        id: { type: string, example: ord_42 }
        customerEmail: { type: string, format: email }
        total: { type: number, example: 99.5 }
        status: { type: string, enum: [PAID, SHIPPED, CANCELLED] }
`,
  },
  wsdl: {
    fileName: 'calculator.wsdl',
    text: `<?xml version="1.0" encoding="UTF-8"?>
<definitions name="Calculator" targetNamespace="http://example.com/calc" xmlns="http://schemas.xmlsoap.org/wsdl/"
  xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:tns="http://example.com/calc" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <types>
    <xsd:schema targetNamespace="http://example.com/calc" elementFormDefault="qualified">
      <xsd:element name="Add"><xsd:complexType><xsd:sequence>
        <xsd:element name="a" type="xsd:int"/><xsd:element name="b" type="xsd:int"/>
      </xsd:sequence></xsd:complexType></xsd:element>
      <xsd:element name="AddResponse"><xsd:complexType><xsd:sequence>
        <xsd:element name="result" type="xsd:int"/>
      </xsd:sequence></xsd:complexType></xsd:element>
    </xsd:schema>
  </types>
  <message name="AddInput"><part name="parameters" element="tns:Add"/></message>
  <message name="AddOutput"><part name="parameters" element="tns:AddResponse"/></message>
  <portType name="CalculatorPort"><operation name="Add"><input message="tns:AddInput"/><output message="tns:AddOutput"/></operation></portType>
  <binding name="CalculatorBinding" type="tns:CalculatorPort">
    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
    <operation name="Add"><soap:operation soapAction="http://example.com/calc/Add"/>
      <input><soap:body use="literal"/></input><output><soap:body use="literal"/></output></operation>
  </binding>
  <service name="Calculator"><port name="CalculatorPort" binding="tns:CalculatorBinding">
    <soap:address location="http://localhost/ws/calculator"/></port></service>
</definitions>
`,
  },
  graphql: {
    fileName: 'shop.graphql',
    text: `type Query {
  products(first: Int): [Product!]!
  product(id: ID!): Product
}

type Mutation {
  addToCart(productId: ID!, quantity: Int!): Cart!
}

type Product {
  id: ID!
  name: String!
  price: Float!
}

type Cart {
  id: ID!
  items: Int!
}
`,
  },
  grpc: {
    fileName: 'greeter.proto',
    text: `syntax = "proto3";

package helloworld;

message HelloRequest { string name = 1; }
message HelloReply { string message = 1; }

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
`,
  },
  crud: { fileName: 'users.csv', text: 'id,name,email\n1,Ada Lovelace,ada@example.com\n2,Alan Turing,alan@example.com\n' },
  json: { fileName: 'answer.json', text: '{\n  "id": 42,\n  "name": "Ada Lovelace",\n  "plan": "pro"\n}\n' },
  webhook: { fileName: '', text: '' },
  proxy: { fileName: '', text: '' },
  echo: { fileName: '', text: '' },
  postman: { fileName: 'collection.json', text: '' },
  ai: { fileName: '', text: 'An orders API: list orders with a status filter, get one by id (404 if missing), and create one that answers 201.' },
};
