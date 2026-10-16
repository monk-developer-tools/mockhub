import { everything, NOTHING, orgState, pickedProjectIds, toggleOrg, toggleProject, toSelection, type OrgNode } from './selection';

const tree: OrgNode[] = [
  { id: 'payments', projectIds: ['pbb', 'cards'] },
  { id: 'retail', projectIds: ['basket'] },
];
const [payments, retail] = tree;

describe('selection', () => {
  it('ticks a whole organisation and sends it as one id', () => {
    // When
    const picked = toggleOrg(NOTHING, payments);

    // Then
    expect(orgState(picked, payments)).toBe('all');
    expect(pickedProjectIds(picked, tree)).toEqual(['pbb', 'cards']);
    expect(toSelection(picked, tree)).toEqual({ organizations: ['payments'], projects: [] });
  });

  it('unticking one project of a whole organisation keeps the rest', () => {
    // Given
    const whole = toggleOrg(NOTHING, payments);

    // When
    const picked = toggleProject(whole, payments, 'pbb');

    // Then
    expect(orgState(picked, payments)).toBe('some');
    expect(toSelection(picked, tree)).toEqual({ organizations: [], projects: ['cards'] });
  });

  it('mixes single projects across organisations', () => {
    // When
    const picked = toggleProject(toggleProject(NOTHING, payments, 'pbb'), retail, 'basket');

    // Then
    expect(orgState(picked, payments)).toBe('some');
    expect(orgState(picked, retail)).toBe('all');
    expect(toSelection(picked, tree)).toEqual({ organizations: [], projects: ['pbb', 'basket'] });
    expect(orgState(toggleOrg(picked, retail), retail)).toBe('none');
  });

  it('asks for everything when every organisation is ticked', () => {
    // Then
    expect(toSelection(everything(tree), tree)).toEqual({ everything: true, organizations: [], projects: [] });
  });
});
