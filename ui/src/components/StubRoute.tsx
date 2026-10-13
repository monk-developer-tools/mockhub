import type { Stub } from '../api/types';
import { describeUrl, isPatternUrl } from '../model/stubForm';

type Obj = Record<string, unknown>;

export function MethodJack({ method }: { method: string }) {
  return <span className={`jack ${method}`}>{method}</span>;
}

export function StatusPlug({ stub }: { stub: Stub }) {
  const response = (stub.response ?? {}) as Obj;
  if (response.fault) return <span className="status-plug fault" title={String(response.fault)}>fault</span>;
  if (response.proxyBaseUrl) return <span className="status-plug proxy" title={String(response.proxyBaseUrl)}>proxy</span>;
  const status = Number(response.status ?? 200);
  return <span className={`status-plug s${String(status)[0]}`}>{status}</span>;
}

export function StubRoute({ stub, basePath }: { stub: Stub; basePath?: string }) {
  const request = (stub.request ?? {}) as Obj;
  const method = String(request.method ?? 'ANY');
  const url = describeUrl(stub);
  return (
    <span className="route">
      <MethodJack method={method} />
      <span className={`path${isPatternUrl(stub) ? ' pattern' : ''}`} title={(basePath ?? '') + url}>
        {url}
      </span>
      <span className="wire" aria-hidden="true" />
      <StatusPlug stub={stub} />
    </span>
  );
}
