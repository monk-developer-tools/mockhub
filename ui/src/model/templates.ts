import type { Stub } from '../api/types';
import { soapEnvelope, soapFault } from './stubForm';

export interface StubTemplate {
  id: string;
  category: string;
  name: string;
  description: string;
  stub: Stub;
}

const json = { 'Content-Type': 'application/json' };
const soapXml = { 'Content-Type': 'text/xml; charset=utf-8' };

export const TEMPLATES: StubTemplate[] = [
  {
    id: 'json-get',
    category: 'Basics',
    name: 'GET returning JSON',
    description: 'Exact path, static JSON body.',
    stub: {
      name: 'Get items',
      request: { method: 'GET', urlPath: '/items' },
      response: { status: 200, headers: json, jsonBody: [{ id: 1, name: 'First item' }] },
    },
  },
  {
    id: 'post-created',
    category: 'Basics',
    name: 'POST returning 201',
    description: 'Create endpoint with a Location header.',
    stub: {
      name: 'Create item',
      request: { method: 'POST', urlPath: '/items' },
      response: { status: 201, headers: { ...json, Location: '/items/42' }, jsonBody: { id: 42 } },
    },
  },
  {
    id: 'path-template',
    category: 'Basics',
    name: 'Path variable',
    description: 'Matches /items/{id} and echoes the id back.',
    stub: {
      name: 'Get item by id',
      request: { method: 'GET', urlPathTemplate: '/items/{id}' },
      response: { status: 200, headers: json, jsonBody: { id: '{{request.path.id}}' }, transformers: ['response-template'] },
    },
  },
  {
    id: 'no-content',
    category: 'Basics',
    name: 'DELETE returning 204',
    description: 'No response body.',
    stub: { name: 'Delete item', request: { method: 'DELETE', urlPathTemplate: '/items/{id}' }, response: { status: 204 } },
  },
  {
    id: 'query-match',
    category: 'Matching',
    name: 'Query parameter',
    description: 'Only matches when ?status=active.',
    stub: {
      name: 'Active items',
      priority: 1,
      request: { method: 'GET', urlPath: '/items', queryParameters: { status: { equalTo: 'active' } } },
      response: { status: 200, headers: json, jsonBody: [] },
    },
  },
  {
    id: 'header-match',
    category: 'Matching',
    name: 'Header required',
    description: 'Requires a Bearer token; pair it with a low-priority 401 stub.',
    stub: {
      name: 'Authorised request',
      request: { method: 'GET', urlPath: '/me', headers: { Authorization: { matches: 'Bearer .+' } } },
      response: { status: 200, headers: json, jsonBody: { user: 'demo' } },
    },
  },
  {
    id: 'json-body-match',
    category: 'Matching',
    name: 'JSON body equals',
    description: 'Matches a JSON body, ignoring extra fields and array order.',
    stub: {
      name: 'Create order for customer 1',
      request: {
        method: 'POST',
        urlPath: '/orders',
        bodyPatterns: [{ equalToJson: '{ "customerId": 1 }', ignoreExtraElements: true, ignoreArrayOrder: true }],
      },
      response: { status: 201, headers: json, jsonBody: { orderId: 'ORD-1' } },
    },
  },
  {
    id: 'jsonpath-match',
    category: 'Matching',
    name: 'JSONPath condition',
    description: 'Matches when the body has amount > 1000.',
    stub: {
      name: 'Large payment needs review',
      request: { method: 'POST', urlPath: '/payments', bodyPatterns: [{ matchesJsonPath: '$[?(@.amount > 1000)]' }] },
      response: { status: 202, headers: json, jsonBody: { status: 'PENDING_REVIEW' } },
    },
  },
  {
    id: 'regex-url',
    category: 'Matching',
    name: 'URL regex',
    description: 'Matches any numeric id with a regular expression.',
    stub: {
      name: 'Numeric ids only',
      request: { method: 'GET', urlPathPattern: '/accounts/[0-9]+' },
      response: { status: 200, headers: json, jsonBody: { type: 'account' } },
    },
  },
  {
    id: 'echo',
    category: 'Dynamic responses',
    name: 'Echo the request',
    description: 'Returns method, path, a query param and the body.',
    stub: {
      name: 'Echo',
      request: { method: 'ANY', urlPathPattern: '/echo/.*' },
      response: {
        status: 200,
        headers: json,
        body: '{\n  "method": "{{request.method}}",\n  "path": "{{request.path}}",\n  "q": "{{request.query.q}}",\n  "body": {{#if request.body}}{{{request.body}}}{{else}}null{{/if}}\n}',
        transformers: ['response-template'],
      },
    },
  },
  {
    id: 'random',
    category: 'Dynamic responses',
    name: 'Random ids and dates',
    description: 'UUIDs, random numbers and timestamps that change per call.',
    stub: {
      name: 'Generated resource',
      request: { method: 'POST', urlPath: '/resources' },
      response: {
        status: 201,
        headers: json,
        body: '{\n  "id": "{{randomValue type=\'UUID\'}}",\n  "number": {{randomInt lower=1000 upper=9999}},\n  "createdAt": "{{now}}",\n  "expiresAt": "{{now offset=\'7 days\'}}"\n}',
        transformers: ['response-template'],
      },
    },
  },
  {
    id: 'jsonpath-response',
    category: 'Dynamic responses',
    name: 'Copy fields from the body',
    description: 'Reads values from the JSON request body.',
    stub: {
      name: 'Register user',
      request: { method: 'POST', urlPath: '/users' },
      response: {
        status: 201,
        headers: json,
        body: '{\n  "id": "{{randomValue type=\'UUID\'}}",\n  "email": "{{jsonPath request.body \'$.email\'}}",\n  "name": "{{jsonPath request.body \'$.name\'}}"\n}',
        transformers: ['response-template'],
      },
    },
  },
  {
    id: 'not-found',
    category: 'Errors & faults',
    name: '404 problem detail',
    description: 'RFC 7807 style error.',
    stub: {
      name: 'Not found',
      request: { method: 'GET', urlPath: '/items/0' },
      response: {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
        jsonBody: { type: 'about:blank', title: 'Not Found', status: 404, detail: 'Item 0 does not exist' },
      },
    },
  },
  {
    id: 'server-error',
    category: 'Errors & faults',
    name: '503 with Retry-After',
    description: 'Tell clients to back off.',
    stub: {
      name: 'Service unavailable',
      request: { method: 'ANY', urlPathPattern: '/.*' },
      priority: 10,
      response: { status: 503, headers: { 'Retry-After': '30' } },
    },
  },
  {
    id: 'slow',
    category: 'Errors & faults',
    name: 'Slow response',
    description: 'Random latency between 1 and 3 seconds.',
    stub: {
      name: 'Slow endpoint',
      request: { method: 'GET', urlPath: '/slow' },
      response: { status: 200, headers: json, jsonBody: { ok: true }, delayDistribution: { type: 'uniform', lower: 1000, upper: 3000 } },
    },
  },
  {
    id: 'reset',
    category: 'Errors & faults',
    name: 'Connection reset',
    description: 'Drops the connection to test client error handling.',
    stub: { name: 'Connection dropped', request: { method: 'GET', urlPath: '/unstable' }, response: { fault: 'CONNECTION_RESET_BY_PEER' } },
  },
  {
    id: 'scenario-start',
    category: 'Stateful',
    name: 'Scenario: first call',
    description: 'Answers in state "Started", then moves to "Done". Add a second stub for the "Done" state.',
    stub: {
      name: 'Job status — pending',
      scenarioName: 'Job',
      requiredScenarioState: 'Started',
      newScenarioState: 'Done',
      request: { method: 'GET', urlPath: '/jobs/1' },
      response: { status: 200, headers: json, jsonBody: { status: 'PENDING' } },
    },
  },
  {
    id: 'scenario-done',
    category: 'Stateful',
    name: 'Scenario: later calls',
    description: 'Answers once the scenario reached "Done".',
    stub: {
      name: 'Job status — done',
      scenarioName: 'Job',
      requiredScenarioState: 'Done',
      request: { method: 'GET', urlPath: '/jobs/1' },
      response: { status: 200, headers: json, jsonBody: { status: 'DONE' } },
    },
  },
  {
    id: 'soap-operation',
    category: 'SOAP',
    name: 'SOAP operation',
    description: 'Matches the operation element inside the SOAP body.',
    stub: {
      name: 'GetCustomer',
      request: {
        method: 'POST',
        urlPath: '/ws/customers',
        bodyPatterns: [{ matchesXPath: "//*[local-name()='Body']/*[local-name()='GetCustomer']" }],
      },
      response: {
        status: 200,
        headers: soapXml,
        body: soapEnvelope('1.1', '<ns:GetCustomerResponse xmlns:ns="http://example.com/customers">\n      <ns:name>Ada Lovelace</ns:name>\n    </ns:GetCustomerResponse>'),
      },
      metadata: { mockhub: { type: 'soap', soapVersion: '1.1', operation: 'GetCustomer', soapAction: 'urn:GetCustomer' } },
    },
  },
  {
    id: 'soap-value',
    category: 'SOAP',
    name: 'SOAP: match a field value',
    description: 'Different answer when customerId is 42.',
    stub: {
      name: 'GetCustomer 42',
      priority: 1,
      request: {
        method: 'POST',
        urlPath: '/ws/customers',
        bodyPatterns: [{ matchesXPath: { expression: "//*[local-name()='customerId']/text()", equalTo: '42' } }],
      },
      response: {
        status: 200,
        headers: soapXml,
        body: soapEnvelope('1.1', '<ns:GetCustomerResponse xmlns:ns="http://example.com/customers">\n      <ns:name>Customer 42</ns:name>\n    </ns:GetCustomerResponse>'),
      },
      metadata: { mockhub: { type: 'soap', soapVersion: '1.1', operation: 'GetCustomer' } },
    },
  },
  {
    id: 'soap-fault',
    category: 'SOAP',
    name: 'SOAP fault',
    description: 'A SOAP 1.1 server fault with HTTP 500.',
    stub: {
      name: 'GetCustomer fault',
      priority: 2,
      request: {
        method: 'POST',
        urlPath: '/ws/customers',
        bodyPatterns: [{ matchesXPath: { expression: "//*[local-name()='customerId']/text()", equalTo: '0' } }],
      },
      response: { status: 500, headers: soapXml, body: soapFault('1.1', 'Customer not found') },
      metadata: { mockhub: { type: 'soap', soapVersion: '1.1', operation: 'GetCustomer' } },
    },
  },
  {
    id: 'soap-echo',
    category: 'SOAP',
    name: 'SOAP: echo a request value',
    description: 'Copies a value from the request envelope with xPath.',
    stub: {
      name: 'Echo customer id',
      request: { method: 'POST', urlPath: '/ws/customers', bodyPatterns: [{ matchesXPath: "//*[local-name()='GetCustomer']" }] },
      response: {
        status: 200,
        headers: soapXml,
        body: soapEnvelope('1.1', '<ns:GetCustomerResponse xmlns:ns="http://example.com/customers">\n      <ns:id>{{xPath request.body \'//*[local-name()="customerId"]/text()\'}}</ns:id>\n    </ns:GetCustomerResponse>'),
        transformers: ['response-template'],
      },
      metadata: { mockhub: { type: 'soap', soapVersion: '1.1', operation: 'GetCustomer' } },
    },
  },
  {
    id: 'proxy-all',
    category: 'Proxy',
    name: 'Pass through to the real service',
    description: 'Low-priority catch-all that forwards anything not mocked.',
    stub: {
      name: 'Forward everything else',
      priority: 10,
      request: { method: 'ANY', urlPathPattern: '/.*' },
      response: { proxyBaseUrl: 'https://api.example.com' },
    },
  },
];

export const TEMPLATE_CATEGORIES = Array.from(new Set(TEMPLATES.map((t) => t.category)));
