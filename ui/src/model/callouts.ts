import type { CalloutEntry, Stub } from '../api/types';

export type CalloutOutcome = 'ok' | 'client-error' | 'server-error' | 'failed';

export const CATCH_ALL_NAME = 'Catch every webhook';

export function catchAllStub(): Stub {
  return {
    name: CATCH_ALL_NAME,
    priority: 10,
    request: { method: 'ANY' },
    response: {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: '{\n  "received": true,\n  "id": "{{randomValue type=\'UUID\'}}",\n  "at": "{{now}}"\n}',
      transformers: ['response-template'],
    },
    metadata: {
      mockhub: {
        folder: 'Webhooks',
        description: 'Accepts any request no other stub matched with 200, so third-party webhooks can be pointed here and inspected in the Requests tab.',
      },
    },
  };
}

export function hasCatchAll(stubs: Stub[]): boolean {
  return stubs.some((s) => s.name === CATCH_ALL_NAME);
}

export function outcomeOf(entry: CalloutEntry): CalloutOutcome {
  if (entry.error || !entry.response) return 'failed';
  if (entry.response.status >= 500) return 'server-error';
  if (entry.response.status >= 400) return 'client-error';
  return 'ok';
}

export function describeOutcome(entry: CalloutEntry): string {
  if (entry.error || !entry.response) return entry.error ?? 'No response';
  return String(entry.response.status);
}

export function calloutCounts(entries: CalloutEntry[]): { total: number; failed: number } {
  return { total: entries.length, failed: entries.filter((e) => outcomeOf(e) !== 'ok').length };
}

export function askAssistantAbout(entry: CalloutEntry): string {
  const target = entry.request ? `${entry.request.method} ${entry.request.url}` : 'its target';
  const stub = entry.stubName ? `"${entry.stubName}"` : 'a stub';
  const what = entry.error ? `failed with: ${entry.error}` : `got HTTP ${entry.response?.status}`;
  return `The ${entry.mode === 'sync' ? 'wait-for-callout' : 'webhook'} from ${stub} to ${target} ${what}. Look at the stub and the recent callouts, explain what went wrong and stage a fix.`;
}
