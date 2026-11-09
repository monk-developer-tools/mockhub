import type { HeaderPair, Stub } from '../api/types';
import { samplePathOf } from './ready';

export const INSPECT_KINDS = new Set(['webhook', 'proxy', 'echo']);

export interface InspectStep {
  id: 'catch' | 'look' | 'answer' | 'share';
  title: string;
  hint: string;
  done: boolean;
}

export interface InspectProgress {
  requests: number;
  looked: boolean;
  answered: boolean;
  shared: boolean;
}

export function inspectSteps(p: InspectProgress): InspectStep[] {
  return [
    { id: 'catch', title: 'Catch a request', hint: 'Point a webhook or your app at the URL, or select Send test.', done: p.requests > 0 },
    { id: 'look', title: 'Look inside a request', hint: 'Select a request to see its headers, body and the answer it got.', done: p.looked },
    { id: 'answer', title: 'Answer it your way', hint: 'Make a stub from a request to answer with your own status and body.', done: p.answered },
    { id: 'share', title: 'Share with your team', hint: 'Copy a link to this project or to one request.', done: p.shared },
  ];
}

export function queryOf(url: string): HeaderPair[] {
  const at = url.indexOf('?');
  if (at < 0) return [];
  return [...new URLSearchParams(url.slice(at + 1))].map(([name, value]) => ({ name, value }));
}

export function testTarget(stubs: Stub[], mockUrl: string): string {
  for (const stub of stubs) {
    const request = (stub.request ?? {}) as Record<string, unknown>;
    const method = String(request.method ?? 'ANY').toUpperCase();
    if (method !== 'ANY' && method !== 'POST') continue;
    const path = samplePathOf(request);
    if (path !== null) return `${mockUrl}${path === '/' ? '/test' : path}`;
  }
  return `${mockUrl}/test`;
}

export function testPayload(now: Date): string {
  return JSON.stringify({ event: 'mockhub.test', id: `evt_${now.getTime().toString(36)}`, sentAt: now.toISOString(), data: { message: 'Hello from MockHub' } }, null, 2);
}

export function storageKey(projectId: string, what: string): string {
  return `mockhub.inspect.${projectId}.${what}`;
}
