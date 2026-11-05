import { Link } from 'react-router-dom';
import type { Project } from '../../api/types';
import { CodeEditor } from '../../components/CodeEditor';
import { CopyButton } from '../../components/CopyButton';
import { GRPC_STATUSES, grpcurlCommand, type GrpcMethod } from '../../model/grpc';
import type { StubForm } from '../../model/stubForm';
import { useGrpcCatalog } from './GrpcServices';

interface FieldProps {
  form: StubForm;
  update: (patch: Partial<StubForm>) => void;
  readOnly: boolean;
}

const pretty = (value: unknown) => JSON.stringify(value, null, 2);
const isBlank = (text: string) => ['', '{}'].includes(text.replace(/\s/g, ''));

export function GrpcRequestFields({ project, form, update, readOnly }: FieldProps & { project: Project }) {
  const { catalog } = useGrpcCatalog(project.id);
  const services = catalog?.services ?? [];
  const service = services.find((s) => s.name === form.grpcService);
  const known = Boolean(service) || (services.length > 0 && !form.grpcService);

  const pickMethod = (method: GrpcMethod | undefined, name: string) => {
    update({
      grpcMethod: name,
      name: form.name || name,
      responseBody: method && isBlank(form.responseBody) ? pretty(method.responseExample) : form.responseBody,
      sampleBody: method && !form.sampleBody.trim() ? pretty(method.requestExample) : form.sampleBody,
    });
  };

  return (
    <div className="section">
      <h3>gRPC method</h3>
      <div className="grid two">
        {known ? (
          <>
            <label className="field">
              <span>Service</span>
              <select value={form.grpcService} disabled={readOnly} onChange={(e) => update({ grpcService: e.target.value, grpcMethod: '' })}>
                <option value="">Choose a service…</option>
                {services.map((s) => <option key={s.name} value={s.name}>{s.name}</option>)}
              </select>
            </label>
            <label className="field">
              <span>Method</span>
              <select value={form.grpcMethod} disabled={readOnly || !service}
                onChange={(e) => pickMethod(service?.methods.find((m) => m.name === e.target.value), e.target.value)}>
                <option value="">Choose a method…</option>
                {(service?.methods ?? []).map((m) => (
                  <option key={m.name} value={m.name}>{m.name}{m.kind === 'unary' ? '' : ` (${m.kind})`}</option>
                ))}
              </select>
            </label>
          </>
        ) : (
          <>
            <label className="field">
              <span>Service <span className="hint">full name with the package</span></span>
              <input type="text" className="mono" value={form.grpcService} placeholder="library.v1.BookService" onChange={(e) => update({ grpcService: e.target.value })} />
            </label>
            <label className="field">
              <span>Method</span>
              <input type="text" className="mono" value={form.grpcMethod} placeholder="GetBook" onChange={(e) => update({ grpcMethod: e.target.value })} />
            </label>
          </>
        )}
      </div>
      {catalog && !service && (
        <p className="muted small">
          {services.length === 0 ? 'No descriptor uploaded yet. ' : form.grpcService ? `${form.grpcService} is not in this project's descriptors. ` : ''}
          Upload a descriptor set in <Link to={`/projects/${project.id}/import-export`}>Import &amp; export</Link> so MockHub can serve the service.
        </p>
      )}
      {service && form.grpcMethod && (
        <p className="muted small mono">
          {service.methods.find((m) => m.name === form.grpcMethod)?.requestType} → {service.methods.find((m) => m.name === form.grpcMethod)?.responseType}
        </p>
      )}
    </div>
  );
}

export function GrpcResponseFields({ form, update, readOnly }: FieldProps) {
  const ok = form.grpcStatus === 'OK';
  return (
    <div className="rows" style={{ gap: 14 }}>
      <div className="grid two">
        <label className="field">
          <span>Status</span>
          <select value={form.grpcStatus} onChange={(e) => update({ grpcStatus: e.target.value })}>
            {GRPC_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        {!ok && (
          <label className="field">
            <span>Message <span className="hint">optional, sent as the status description</span></span>
            <input type="text" value={form.grpcReason} placeholder="Book 404 not found" onChange={(e) => update({ grpcReason: e.target.value })} />
          </label>
        )}
      </div>
      {ok && (
        <div className="field">
          <span>Response message <span className="hint">protobuf JSON: proto field names or their lowerCamel form</span></span>
          <CodeEditor label="Response message" value={form.responseBody} language="json" readOnly={readOnly} minHeight="140px"
            onChange={(responseBody) => update({ responseBody })} />
        </div>
      )}
      {ok && (
        <label className="check">
          <input type="checkbox" checked={form.templating} onChange={(e) => update({ templating: e.target.checked })} />
          Response templating
          <span className="muted small">
            the request message is the body, so <span className="mono">{"{{jsonPath request.body '$.id'}}"}</span> echoes a field
          </span>
        </label>
      )}
    </div>
  );
}

export function GrpcCall({ project, form }: { project: Project; form: StubForm }) {
  if (!form.grpcService || !form.grpcMethod) return null;
  const command = grpcurlCommand(project.mockUrl, form.grpcService, form.grpcMethod, form.sampleBody);
  return (
    <div className="section">
      <h3>Call it</h3>
      <p className="muted small">
        gRPC needs HTTP/2, so call MockHub's port directly. Server reflection is on, so grpcurl finds the service without the .proto files.
      </p>
      <div className="code-line">
        <code className="mono small">{command}</code>
        <CopyButton text={command} />
      </div>
    </div>
  );
}
