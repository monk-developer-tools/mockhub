import type { GeneratorField } from '../api/types';

export const GENERATOR_TYPES: { group: string; items: { label: string; expression: string; field: string }[] }[] = [
  {
    group: 'Ids',
    items: [
      { label: 'Sequence 1, 2, 3…', expression: 'sequence', field: 'id' },
      { label: 'UUID', expression: 'uuid', field: 'id' },
    ],
  },
  {
    group: 'People',
    items: [
      { label: 'Full name', expression: 'Name.fullName', field: 'name' },
      { label: 'First name', expression: 'Name.firstName', field: 'firstName' },
      { label: 'Last name', expression: 'Name.lastName', field: 'lastName' },
      { label: 'E-mail', expression: 'Internet.emailAddress', field: 'email' },
      { label: 'Phone', expression: 'PhoneNumber.phoneNumber', field: 'phone' },
      { label: 'Username', expression: 'Internet.username', field: 'username' },
      { label: 'Job title', expression: 'Job.title', field: 'jobTitle' },
    ],
  },
  {
    group: 'Places',
    items: [
      { label: 'Street address', expression: 'Address.streetAddress', field: 'address.street' },
      { label: 'City', expression: 'Address.city', field: 'address.city' },
      { label: 'Postcode', expression: 'Address.zipCode', field: 'address.postcode' },
      { label: 'Country', expression: 'Address.country', field: 'address.country' },
    ],
  },
  {
    group: 'Business',
    items: [
      { label: 'Company', expression: 'Company.name', field: 'company' },
      { label: 'Product', expression: 'Commerce.productName', field: 'product' },
      { label: 'Price', expression: 'Commerce.price', field: 'price' },
      { label: 'Department', expression: 'Commerce.department', field: 'department' },
      { label: 'IBAN', expression: 'Finance.iban', field: 'iban' },
      { label: 'Currency code', expression: 'Money.currencyCode', field: 'currency' },
    ],
  },
  {
    group: 'Other',
    items: [
      { label: 'Number 1–100', expression: "Number.numberBetween '1','100'", field: 'quantity' },
      { label: 'Yes / no', expression: 'Bool.bool', field: 'active' },
      { label: 'Word', expression: 'Lorem.word', field: 'tag' },
      { label: 'Sentence', expression: 'Lorem.sentence', field: 'description' },
      { label: 'Colour', expression: 'Color.name', field: 'colour' },
      { label: 'Web address', expression: 'Internet.url', field: 'website' },
    ],
  },
];

export const ALL_TYPES = GENERATOR_TYPES.flatMap((g) => g.items);

export const STARTER_FIELDS: GeneratorField[] = [
  { name: 'id', expression: 'sequence' },
  { name: 'name', expression: 'Name.fullName' },
  { name: 'email', expression: 'Internet.emailAddress' },
  { name: 'address.city', expression: 'Address.city' },
];

export function isKnownType(expression: string): boolean {
  return ALL_TYPES.some((t) => t.expression === expression);
}

export function fieldNameFor(expression: string, taken: string[]): string {
  const base = ALL_TYPES.find((t) => t.expression === expression)?.field ?? 'field';
  if (!taken.includes(base)) return base;
  for (let i = 2; ; i++) if (!taken.includes(`${base}${i}`)) return `${base}${i}`;
}

export function wrapRecords(records: unknown[], wrapKey: string): string {
  const key = wrapKey.trim();
  return JSON.stringify(key ? { [key]: records, total: records.length } : records, null, 2);
}
