import type { LatencyMode, LatencyRule, RatePeriod, RuntimeSettings } from '../api/types';

export type CorsMode = 'server' | 'custom' | 'fail';

export interface RuntimeForm {
  defaultOn: boolean;
  defaultStatus: string;
  defaultHeaders: string;
  defaultBody: string;
  defaultTemplated: boolean;
  rateOn: boolean;
  rateRequests: string;
  ratePer: RatePeriod;
  latencyMode: LatencyMode;
  fixedMs: string;
  minMs: string;
  maxMs: string;
  medianMs: string;
  sigma: string;
  loadOn: boolean;
  loadAfterRps: string;
  loadMsPerRequest: string;
  corsMode: CorsMode;
  corsOrigins: string;
  corsMethods: string;
  corsHeaders: string;
  corsCredentials: boolean;
  ipAllowList: string;
  requireClientCert: boolean;
}

export const EMPTY_RUNTIME: RuntimeSettings = { defaultResponse: null, rateLimit: null, latency: null, cors: null, ipAllowList: [], requireClientCert: false };

const DEFAULT_BODY = '{\n  "error": "No mock matches {{request.method}} {{request.path}}"\n}';

const text = (n: number | undefined, fallback: string) => (n === undefined || n === null || n === 0 ? fallback : String(n));

export function toForm(settings: RuntimeSettings): RuntimeForm {
  const d = settings.defaultResponse;
  const r = settings.rateLimit;
  const l = settings.latency;
  const c = settings.cors;
  return {
    defaultOn: Boolean(d),
    defaultStatus: String(d?.status ?? 404),
    defaultHeaders: d ? Object.entries(d.headers).map(([k, v]) => `${k}: ${v}`).join('\n') : 'Content-Type: application/json',
    defaultBody: d ? d.body : DEFAULT_BODY,
    defaultTemplated: d ? d.templated : true,
    rateOn: Boolean(r),
    rateRequests: String(r?.requests ?? 60),
    ratePer: r?.per ?? 'minute',
    latencyMode: l?.mode ?? 'none',
    fixedMs: text(l?.fixedMs, '300'),
    minMs: text(l?.minMs, '100'),
    maxMs: text(l?.maxMs, '800'),
    medianMs: text(l?.medianMs, '250'),
    sigma: text(l?.sigma, '0.25'),
    loadOn: Boolean(l?.load),
    loadAfterRps: String(l?.load?.afterRps ?? 10),
    loadMsPerRequest: String(l?.load?.msPerRequest ?? 20),
    corsMode: !c ? 'server' : c.simulateFailure ? 'fail' : 'custom',
    corsOrigins: c?.origins.join('\n') ?? '',
    corsMethods: c?.methods.join(', ') ?? 'GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS',
    corsHeaders: c?.headers.join(', ') ?? '',
    corsCredentials: c?.credentials ?? false,
    ipAllowList: settings.ipAllowList.join('\n'),
    requireClientCert: settings.requireClientCert === true,
  };
}

const lines = (value: string) => value.split('\n').map((s) => s.trim()).filter(Boolean);
const items = (value: string) => value.split(/[\n,]/).map((s) => s.trim()).filter(Boolean);
const num = (value: string) => Number(value.trim());

export function parseHeaders(value: string): Record<string, string> {
  const out: Record<string, string> = {};
  lines(value).forEach((line) => {
    const at = line.indexOf(':');
    const name = (at < 0 ? line : line.slice(0, at)).trim();
    if (name) out[name] = at < 0 ? '' : line.slice(at + 1).trim();
  });
  return out;
}

function latency(form: RuntimeForm): LatencyRule | null {
  const load = form.loadOn ? { afterRps: num(form.loadAfterRps), msPerRequest: num(form.loadMsPerRequest) } : null;
  if (form.latencyMode === 'none' && !load) return null;
  const rule: LatencyRule = { mode: form.latencyMode, load };
  if (form.latencyMode === 'fixed') rule.fixedMs = num(form.fixedMs);
  if (form.latencyMode === 'uniform') Object.assign(rule, { minMs: num(form.minMs), maxMs: num(form.maxMs) });
  if (form.latencyMode === 'lognormal') Object.assign(rule, { medianMs: num(form.medianMs), sigma: num(form.sigma) });
  return rule;
}

export function toSettings(form: RuntimeForm): RuntimeSettings {
  return {
    defaultResponse: form.defaultOn
      ? { status: num(form.defaultStatus), headers: parseHeaders(form.defaultHeaders), body: form.defaultBody, templated: form.defaultTemplated }
      : null,
    rateLimit: form.rateOn ? { requests: num(form.rateRequests), per: form.ratePer } : null,
    latency: latency(form),
    cors: form.corsMode === 'server'
      ? null
      : {
          origins: lines(form.corsOrigins),
          methods: items(form.corsMethods).map((m) => m.toUpperCase()),
          headers: items(form.corsHeaders),
          credentials: form.corsCredentials,
          simulateFailure: form.corsMode === 'fail',
        },
    ipAllowList: lines(form.ipAllowList),
    requireClientCert: form.requireClientCert,
  };
}

export function runtimeSummary(settings: RuntimeSettings): string[] {
  const out: string[] = [];
  if (settings.defaultResponse) out.push(`Unmatched calls get ${settings.defaultResponse.status}`);
  if (settings.rateLimit) out.push(`${settings.rateLimit.requests} requests per ${settings.rateLimit.per}`);
  if (settings.latency) out.push(settings.latency.mode === 'none' ? 'Slower under load' : `${settings.latency.mode} delay`);
  if (settings.cors) out.push(settings.cors.simulateFailure ? 'CORS fails on purpose' : `CORS for ${settings.cors.origins.length} origin${settings.cors.origins.length === 1 ? '' : 's'}`);
  if (settings.requireClientCert) out.push('Client certificate required');
  if (settings.ipAllowList.length) out.push(`${settings.ipAllowList.length} allowed IP range${settings.ipAllowList.length === 1 ? '' : 's'}`);
  return out;
}
