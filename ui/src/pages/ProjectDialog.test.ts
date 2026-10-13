import { slugify } from './ProjectDialog';

describe('slugify', () => {
  it('derives a base path from a project name', () => {
    expect(slugify('Payment Service (v2)!')).toBe('payment-service-v2');
    expect(slugify('  --Orders--  ')).toBe('orders');
  });
});
