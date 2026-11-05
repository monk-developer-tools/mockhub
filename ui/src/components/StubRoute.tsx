import type { Stub } from '../api/types';
import { crudOf } from '../model/crud';
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
  const crud = crudOf(stub);
  if (crud) {
    return (
      <span className="route">
        <MethodJack method="CRUD" />
        <span className="path" title={`${basePath ?? ''}/${crud.resource}`}>/{crud.resource}</span>
        <span className="wire" aria-hidden="true" />
        <span className="status-plug data">data</span>
      </span>
    );
  }
  const request = (stub.request ?? {}) as Obj;
  const url = describeUrl(stub);
  if ((((stub.metadata ?? {}) as Obj).mockhub as Obj | undefined)?.type === 'grpc') {
    const headers = (((stub.response ?? {}) as Obj).headers ?? {}) as Obj;
    const status = String(headers['grpc-status-name'] ?? 'OK');
    return (
      <span className="route">
        <MethodJack method="gRPC" />
        <span className="path" title={url}>{url}</span>
        <span className="wire" aria-hidden="true" />
        <span className={`status-plug ${status === 'OK' ? 's2' : 's4'}`}>{status}</span>
      </span>
    );
  }
  const method = String(request.method ?? 'ANY');
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
