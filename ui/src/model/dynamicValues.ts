export type DynamicScope = 'response' | 'callout';

export interface DynamicValue {
  label: string;
  expression: string;
  hint?: string;
}

export interface DynamicGroup {
  id: string;
  name: string;
  items: DynamicValue[];
}

function requestComponents(r: string): DynamicValue[] {
  return [
    { label: 'Whole body', expression: `{{{${r}.body}}}`, hint: 'Raw request body, triple braces keep JSON as is' },
    { label: 'JSON body field', expression: `{{jsonPath ${r}.body '$.id'}}`, hint: 'Any JSONPath, e.g. $.items[0].name' },
    { label: 'XML body field', expression: `{{xPath ${r}.body '/order/id/text()'}}` },
    { label: 'SOAP body field', expression: `{{soapXPath ${r}.body '/GetOrder/id/text()'}}` },
    { label: 'Form field', expression: `{{formData ${r}.body 'form'}}{{form.email}}`, hint: 'application/x-www-form-urlencoded' },
    { label: 'Path', expression: `{{${r}.path}}` },
    { label: 'Path segment', expression: `{{${r}.pathSegments.[1]}}`, hint: 'Zero-based, the base path counts' },
    { label: 'Path variable', expression: `{{${r}.path.id}}`, hint: 'With a /orders/{id} path template' },
    { label: 'Query parameter', expression: `{{${r}.query.page}}` },
    { label: 'Header', expression: `{{${r}.headers.X-Request-Id}}` },
    { label: 'Cookie', expression: `{{${r}.cookies.session}}` },
    { label: 'Method', expression: `{{${r}.method}}` },
    { label: 'URL with query', expression: `{{${r}.url}}` },
    { label: 'Base URL', expression: `{{${r}.baseUrl}}`, hint: 'Scheme, host and port the caller used' },
    { label: 'Request id', expression: `{{${r}.id}}` },
    { label: 'Client IP', expression: `{{${r}.clientIp}}` },
  ];
}

const RESPONSE_COMPONENTS: DynamicValue[] = [
  { label: 'Mock response body', expression: '{{{originalResponse.body}}}', hint: 'The body this stub just answered with' },
  { label: 'Mock response JSON field', expression: "{{jsonPath originalResponse.body '$.id'}}" },
  { label: 'Mock response status', expression: '{{originalResponse.status}}' },
  { label: 'Mock response header', expression: '{{originalResponse.headers.Content-Type}}' },
];

function controlFlow(r: string): DynamicValue[] {
  return [
    {
      label: 'If / else',
      expression: `{{#if (eq (jsonPath ${r}.body '$.type') 'gold')}}10{{else}}0{{/if}}`,
      hint: 'Pick a value by a condition',
    },
    { label: 'Unless', expression: `{{#unless ${r}.query.debug}}hidden{{/unless}}` },
    {
      label: 'Loop over a JSON array',
      expression: `[{{#each (jsonPath ${r}.body '$.items') as |item|}}{"id": "{{item.id}}"}{{#unless @last}},{{/unless}}{{/each}}]`,
    },
    { label: 'Repeat N times', expression: '[{{#each (range 1 3) as |n|}}{"n": {{n}} }{{#unless @last}},{{/unless}}{{/each}}]' },
    { label: 'Value or default', expression: `{{val ${r}.query.page default='1'}}` },
  ];
}

function dataManipulation(r: string): DynamicValue[] {
  return [
    { label: 'Parse JSON and read fields', expression: `{{#with (parseJson ${r}.body)}}{{id}}{{/with}}` },
    { label: 'To JSON', expression: `{{toJson (jsonPath ${r}.body '$.customer')}}` },
    { label: 'Pretty-print JSON', expression: `{{formatJson ${r}.body}}` },
    { label: 'Merge JSON', expression: `{{jsonMerge ${r}.body '{"status": "CREATED"}'}}` },
    { label: 'Remove a JSON field', expression: `{{jsonRemove ${r}.body '$.password'}}` },
    { label: 'Add to a JSON array', expression: `{{jsonArrayAdd '[1, 2]' '3'}}` },
    { label: 'Regex extract', expression: `{{regexExtract ${r}.path '[0-9]+'}}` },
    { label: 'Size of a list or text', expression: `{{size (jsonPath ${r}.body '$.items')}}` },
    { label: 'Base64 encode', expression: `{{base64 ${r}.body padding=false}}` },
    { label: 'Base64 decode', expression: "{{base64 'aGVsbG8=' decode=true}}" },
    { label: 'URL encode', expression: `{{urlEncode ${r}.query.q}}` },
    { label: 'Format XML', expression: `{{formatXml ${r}.body}}` },
  ];
}

function stateful(r: string): DynamicValue[] {
  return [
    {
      label: 'Read saved state',
      expression: `{{state context=(jsonPath ${r}.body '$.id') property='status' default='NEW'}}`,
      hint: 'Saved by a recordState listener',
    },
    { label: 'State exists?', expression: `{{#if (state context=${r}.path.id property='status')}}yes{{else}}no{{/if}}` },
  ];
}

function comparisons(r: string): DynamicValue[] {
  return [
    { label: 'Equals', expression: `{{#if (eq ${r}.query.status 'paid')}}…{{/if}}` },
    { label: 'Not equals', expression: `{{#if (neq ${r}.method 'GET')}}…{{/if}}` },
    { label: 'Greater than', expression: `{{#if (gt (jsonPath ${r}.body '$.amount') 1000)}}REVIEW{{else}}OK{{/if}}` },
    { label: 'Less than', expression: `{{#if (lt (jsonPath ${r}.body '$.age') 18)}}minor{{/if}}` },
    { label: 'And / or / not', expression: `{{#if (and (eq ${r}.method 'POST') (not ${r}.query.dryRun))}}…{{/if}}` },
    { label: 'Contains', expression: `{{#if (contains ${r}.body 'urgent')}}…{{/if}}` },
    { label: 'Matches regex', expression: `{{#if (matches ${r}.path '.*/v2/.*')}}…{{/if}}` },
  ];
}

function strings(r: string): DynamicValue[] {
  return [
    { label: 'Upper case', expression: `{{upper ${r}.query.code}}` },
    { label: 'Lower case', expression: `{{lower ${r}.headers.X-Region}}` },
    { label: 'Capitalize', expression: `{{capitalize (jsonPath ${r}.body '$.name')}}` },
    { label: 'Trim', expression: `{{trim (jsonPath ${r}.body '$.name')}}` },
    { label: 'Replace', expression: `{{replace ${r}.path '/v1/' '/v2/'}}` },
    { label: 'Substring', expression: `{{substring (jsonPath ${r}.body '$.card') 0 4}}` },
    { label: 'Abbreviate', expression: `{{abbreviate (jsonPath ${r}.body '$.text') 20}}` },
    { label: 'Default if empty', expression: `{{defaultIfEmpty ${r}.query.lang 'en'}}` },
  ];
}

const ARITHMETIC: DynamicValue[] = [
  { label: 'Add / subtract / multiply', expression: "{{math 10 '*' 3}}", hint: "Operators: + - * / %" },
  { label: 'Random integer', expression: '{{randomInt lower=1 upper=100}}' },
  { label: 'Random decimal', expression: '{{randomDecimal lower=1 upper=100}}' },
  { label: 'Number format', expression: "{{numberFormat 1234.5 '#,##0.00'}}" },
];

const DATES: DynamicValue[] = [
  { label: 'Now (ISO 8601)', expression: '{{now}}' },
  { label: 'Now, formatted', expression: "{{now format='yyyy-MM-dd HH:mm:ss'}}" },
  { label: 'In 3 days', expression: "{{now offset='3 days' format='yyyy-MM-dd'}}" },
  { label: 'Epoch milliseconds', expression: "{{now format='epoch'}}" },
  { label: 'Unix seconds', expression: "{{now format='unix'}}" },
  { label: 'Parse and reformat a date', expression: "{{date (parseDate '2026-11-03') format='dd MMM yyyy'}}" },
  { label: 'Start of the month', expression: "{{date (truncateDate (now) 'first day of month') format='yyyy-MM-dd'}}" },
];

const ARRAYS: DynamicValue[] = [
  { label: 'Pick one at random', expression: "{{pickRandom 'NEW' 'PAID' 'SHIPPED'}}" },
  { label: 'Make an array', expression: "{{toJson (array 'a' 'b' 'c')}}" },
  { label: 'Add to an array', expression: "{{toJson (arrayAdd (array 1 2) 3)}}" },
  { label: 'Remove from an array', expression: "{{toJson (arrayRemove (array 1 2 3) position='end')}}" },
  { label: 'Join with commas', expression: "{{arrayJoin ',' (array 'a' 'b' 'c')}}" },
  { label: 'Range of numbers', expression: '{{toJson (range 1 5)}}' },
];

const RANDOM: DynamicValue[] = [
  { label: 'UUID', expression: "{{randomValue type='UUID'}}" },
  { label: 'Alphanumeric code', expression: "{{randomValue length=8 type='ALPHANUMERIC' uppercase=true}}" },
  { label: 'Digits', expression: "{{randomValue length=6 type='NUMERIC'}}" },
  { label: 'Hex token', expression: "{{randomValue length=32 type='HEXADECIMAL'}}" },
  { label: 'Hostname', expression: '{{hostname}}' },
];

const FAKER: DynamicValue[] = [
  { label: 'Full name', expression: "{{fake 'Name.fullName'}}" },
  { label: 'First name', expression: "{{fake 'Name.firstName'}}" },
  { label: 'E-mail', expression: "{{fake 'Internet.emailAddress'}}" },
  { label: 'Phone', expression: "{{fake 'PhoneNumber.phoneNumber'}}" },
  { label: 'Username', expression: "{{fake 'Internet.username'}}" },
  { label: 'Street address', expression: "{{fake 'Address.streetAddress'}}" },
  { label: 'City', expression: "{{fake 'Address.city'}}" },
  { label: 'Country', expression: "{{fake 'Address.country'}}" },
  { label: 'Postcode', expression: "{{fake 'Address.zipCode'}}" },
  { label: 'Company', expression: "{{fake 'Company.name'}}" },
  { label: 'Product', expression: "{{fake 'Commerce.productName'}}" },
  { label: 'Price', expression: "{{fake 'Commerce.price'}}" },
  { label: 'IBAN', expression: "{{fake 'Finance.iban'}}" },
  { label: 'Credit card number', expression: "{{fake 'Finance.creditCard'}}" },
  { label: 'Number 1–100', expression: "{{fake 'Number.numberBetween' 1 100}}" },
  { label: 'Sentence', expression: "{{fake 'Lorem.sentence'}}" },
  { label: 'Name in German', expression: "{{fake 'Name.fullName' locale='de'}}" },
];

export function dynamicValues(scope: DynamicScope): DynamicGroup[] {
  const r = scope === 'callout' ? 'originalRequest' : 'request';
  const groups: DynamicGroup[] = [
    { id: 'request', name: 'Request components', items: requestComponents(r) },
    ...(scope === 'callout' ? [{ id: 'response', name: 'Response components', items: RESPONSE_COMPONENTS }] : []),
    { id: 'control', name: 'Control flow', items: controlFlow(r) },
    { id: 'data', name: 'Data manipulation', items: dataManipulation(r) },
    { id: 'state', name: 'Stateful helpers', items: stateful(r) },
    { id: 'compare', name: 'Comparison operators', items: comparisons(r) },
    { id: 'string', name: 'String operators', items: strings(r) },
    { id: 'math', name: 'Arithmetic operators', items: ARITHMETIC },
    { id: 'date', name: 'Date operators', items: DATES },
    { id: 'array', name: 'Array helpers', items: ARRAYS },
    { id: 'random', name: 'Random values', items: RANDOM },
    { id: 'faker', name: 'Faker', items: FAKER },
  ];
  return groups;
}

export function searchDynamicValues(groups: DynamicGroup[], query: string): DynamicGroup[] {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return groups;
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => {
        const text = `${g.name} ${i.label} ${i.expression} ${i.hint ?? ''}`.toLowerCase();
        return words.every((w) => text.includes(w));
      }),
    }))
    .filter((g) => g.items.length > 0);
}
