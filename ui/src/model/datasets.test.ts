import type { Dataset } from '../api/types';
import { fieldList, filtersFrom, formatOf, helperExamples, lookupExpression, sizeLabel, slugifyKey } from './datasets';

const customers: Dataset = {
  orgId: 'o1', key: 'customers', name: 'Customers', description: '', identity: ['id'], indexes: ['city'],
  columns: [{ name: 'id', type: 'number' }, { name: 'name', type: 'string' }, { name: 'city', type: 'string' }],
  recordCount: 3, bytes: 120, createdAt: '', updatedAt: '',
};

describe('dataset helpers', () => {
  it('works out the format from the file name or its first characters', () => {
    // Then
    expect(formatOf('people.CSV', '[')).toBe('csv');
    expect(formatOf('people.jsonl', '')).toBe('ndjson');
    expect(formatOf('people.json', '')).toBe('json');
    expect(formatOf('export.txt', '  [{"id":1}]')).toBe('json');
    expect(formatOf('export.txt', '{"id":1}\n')).toBe('ndjson');
    expect(formatOf('export.txt', 'id,name')).toBe('csv');
  });

  it('turns names into keys and field lists', () => {
    // Then
    expect(slugifyKey(' Customer Accounts! ')).toBe('customer-accounts');
    expect(fieldList(' id, email ,,id')).toEqual(['id', 'email']);
    expect(sizeLabel(512)).toBe('512 bytes');
    expect(sizeLabel(2048)).toBe('2 KB');
    expect(sizeLabel(3 * 1024 * 1024)).toBe('3.0 MB');
  });

  it('suggests helpers that look records up by identity and index', () => {
    // When
    const examples = helperExamples(customers).map((e) => e.expression);

    // Then
    expect(examples).toEqual([
      "{{{dataset-one 'customers' id=request.path.id}}}",
      "{{#with (dataset-one 'customers' id=request.path.id)}}{{name}}{{else}}Not found{{/with}}",
      "{{{dataset 'customers' city=request.query.city limit=20}}}",
    ]);
  });

  it('writes the expression for a lookup', () => {
    // Given
    const filters = filtersFrom([{ field: 'city', value: 'London' }, { field: ' ', value: 'x' }, { field: 'name', value: "O'Brien" }]);

    // Then
    expect(lookupExpression('customers', filters, true)).toBe(`{{{dataset-one 'customers' city='London' name="O'Brien"}}}`);
    expect(lookupExpression('customers', {}, false)).toBe("{{{dataset 'customers'}}}");
  });
});
