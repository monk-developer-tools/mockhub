import { Plus, Trash } from '@phosphor-icons/react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { FilePicker } from '../../components/FilePicker';
import { Confirm } from '../../components/Modal';
import { useToast } from '../../components/Toasts';
import { formatSize } from '../../model/files';
import { starterStub, type GrpcCatalog, type GrpcService } from '../../model/grpc';
import { CopyButton } from '../../components/CopyButton';

export const PROTOC_COMMAND = 'protoc --include_imports --descriptor_set_out=library.dsc library.proto';
const ACCEPT = '.proto,.dsc,.desc,.pb,.protoset,.bin';
const SAFE_NAME = /[^A-Za-z0-9._-]/g;

export function descriptorName(fileName: string): string {
  const cleaned = fileName.replace(SAFE_NAME, '-').replace(/^[^A-Za-z0-9]+/, '');
  return /\.(proto|dsc|desc|pb|protoset|bin)$/.test(cleaned) ? cleaned : `${cleaned || 'services'}.dsc`;
}

export function useGrpcCatalog(projectId: string, enabled = true) {
  const [catalog, setCatalog] = useState<GrpcCatalog | null>(null);
  const reload = useCallback(async () => {
    if (!enabled) return;
    try {
      setCatalog(await api.grpc(projectId));
    } catch {
      setCatalog({ descriptors: [], services: [] });
    }
  }, [projectId, enabled]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { catalog, reload, setCatalog };
}

export function GrpcServices({ projectId, canEdit, onStubsCreated }: { projectId: string; canEdit: boolean; onStubsCreated: () => void }) {
  const { catalog, setCatalog, reload } = useGrpcCatalog(projectId);
  const toast = useToast();
  const [removing, setRemoving] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const upload = async (file: File) => {
    setBusy(true);
    try {
      setCatalog(await api.uploadGrpcDescriptor(projectId, descriptorName(file.name), file));
      toast.notify(`${file.name} uploaded`);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (name: string) => {
    setRemoving(null);
    try {
      await api.deleteGrpcDescriptor(projectId, name);
      toast.notify(`${name} removed`);
      reload();
    } catch (e) {
      toast.fail(e);
    }
  };

  const createStubs = async (service: GrpcService) => {
    setBusy(true);
    try {
      for (const method of service.methods) await api.createStub(projectId, starterStub(service, method));
      toast.notify(`Created ${service.methods.length} stub${service.methods.length === 1 ? '' : 's'} for ${service.name}`);
      onStubsCreated();
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="panel">
      <div className="panel-head">
        <h2>gRPC services</h2>
      </div>
      <div className="panel-body stack">
        <p className="muted small">
          Upload a .proto file and MockHub answers its services over HTTP/2 on the mock port. A .proto file can import only the google/protobuf types; for several files that import each other, upload a descriptor set built with:
        </p>
        <div className="code-line">
          <code className="mono small">{PROTOC_COMMAND}</code>
          <CopyButton text={PROTOC_COMMAND} />
        </div>
        {canEdit && <FilePicker accept={ACCEPT} label={busy ? 'Uploading…' : 'Drop a .proto file or a descriptor set here, or click to choose'} onFile={upload} />}
        {catalog === null ? (
          <p className="muted small">Loading…</p>
        ) : catalog.descriptors.length === 0 ? (
          <p className="muted small">No descriptors yet.</p>
        ) : (
          <ul className="grpc-list">
            {catalog.descriptors.map((d) => (
              <li key={d.name}>
                <div className="grpc-row">
                  <span className="mono">{d.name}</span>
                  <span className="muted small">{formatSize(d.size)} · {d.files.length} file{d.files.length === 1 ? '' : 's'}</span>
                  {canEdit && (
                    <button className="btn ghost icon danger" title={`Remove ${d.name}`} aria-label={`Remove ${d.name}`} onClick={() => setRemoving(d.name)}>
                      <Trash size={14} />
                    </button>
                  )}
                </div>
                {catalog.services.filter((s) => s.descriptor === d.name).map((s) => (
                  <div key={s.name} className="grpc-service">
                    <div className="grpc-row">
                      <span className="mono">{s.name}</span>
                      {canEdit && (
                        <button className="btn small" disabled={busy} onClick={() => createStubs(s)}>
                          <Plus size={13} /> Create stubs
                        </button>
                      )}
                    </div>
                    <ul className="grpc-methods">
                      {s.methods.map((m) => (
                        <li key={m.name}>
                          <span className="mono">{m.name}</span>
                          <span className="muted small mono">({m.requestType.split('.').pop()}) → {m.responseType.split('.').pop()}</span>
                          {m.kind !== 'unary' && <span className="tag">{m.kind}</span>}
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </li>
            ))}
          </ul>
        )}
      </div>
      {removing && (
        <Confirm
          title="Remove descriptor"
          message={`MockHub stops answering the services in ${removing}. Their stubs stay in the project.`}
          confirmLabel="Remove"
          onConfirm={() => remove(removing)}
          onCancel={() => setRemoving(null)}
        />
      )}
    </div>
  );
}
