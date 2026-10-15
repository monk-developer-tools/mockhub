import '@testing-library/jest-dom/vitest';

Element.prototype.scrollIntoView = function scrollIntoView() {
  return Promise.resolve() as unknown as void;
};
