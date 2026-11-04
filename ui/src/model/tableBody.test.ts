import { cellText, datasetBody, editCell, emptyRow, keyFrom, pathParam, tableOf, withRows } from './tableBody';

describe('table body', () => {
  it('reads arrays of objects and an object that wraps one', () => {
    // Then
    expect(tableOf('[{"id":1,"name":"Rex"},{"id":2,"tag":"cat"}]')).toEqual({
      path: null, rows: [{ id: 1, name: 'Rex' }, { id: 2, tag: 'cat' }], columns: ['id', 'name', 'tag'],
    });
    expect(tableOf('{"data":[{"id":1}],"total":1}')?.path).toBe('data');
    expect(tableOf('{"a":[{"id":1}],"b":[{"id":2}]}')).toBeNull();
    expect(tableOf('[1,2]')).toBeNull();
    expect(tableOf('[]')).toBeNull();
    expect(tableOf('{{fake}}')).toBeNull();
  });

  it('keeps each value the type its column has', () => {
    // Given
    const rows = [{ id: 1, name: 'Rex', vaccinated: true, tags: ['a'] }, { id: 2, name: 'Tom', vaccinated: false, tags: [] }];

    // When
    let edited = editCell(rows, 0, 'id', '10');
    edited = editCell(edited, 0, 'vaccinated', 'false');
    edited = editCell(edited, 1, 'tags', '["b","c"]');
    edited = editCell(edited, 1, 'name', '42');
    const added = editCell([...edited, emptyRow(['id', 'name'])], 2, 'id', '3');

    // Then
    expect(edited[0]).toEqual({ id: 10, name: 'Rex', vaccinated: false, tags: ['a'] });
    expect(edited[1]).toEqual({ id: 2, name: '42', vaccinated: false, tags: ['b', 'c'] });
    expect(added[2]).toEqual({ id: 3, name: null });
    expect(cellText(['a'])).toBe('["a"]');
    expect(cellText(null)).toBe('');
  });

  it('writes the rows back where they came from', () => {
    // Then
    expect(JSON.parse(withRows('{"data":[],"total":0}', 'data', [{ id: 1 }]))).toEqual({ data: [{ id: 1 }], total: 0 });
    expect(JSON.parse(withRows('[]', null, [{ id: 1 }]))).toEqual([{ id: 1 }]);
  });

  it('binds the body to a dataset by path parameter or as a list', () => {
    // Then
    expect(pathParam('urlPathTemplate', '/pets/{petId}')).toBe('petId');
    expect(pathParam('urlPathTemplate', '/pets')).toBeNull();
    expect(pathParam('urlPath', '/pets/{id}')).toBeNull();
    expect(datasetBody('[]', null, 'pets', 'petId', 'id')).toBe("{{{dataset-one 'pets' id=request.path.petId}}}");
    expect(datasetBody('{"data":[],"total":2}', 'data', 'pets', null, 'id')).toBe(`{
  "data": {{{dataset 'pets' limit=1000}}},
  "total": 2
}`);
    expect(keyFrom('/v1/pets/{id}')).toBe('pets');
    expect(keyFrom('/')).toBe('records');
  });
});
