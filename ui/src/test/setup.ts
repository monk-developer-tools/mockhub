import '@testing-library/jest-dom/vitest';

Element.prototype.scrollIntoView = function scrollIntoView() {
  return Promise.resolve() as unknown as void;
};

const emptyRects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
const emptyRect = () => ({ x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, toJSON: () => ({}) }) as DOMRect;
Range.prototype.getClientRects = emptyRects;
Range.prototype.getBoundingClientRect = emptyRect;
