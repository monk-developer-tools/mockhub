import { DownloadSimple, File, Trash, UploadSimple } from '@phosphor-icons/react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { BodyFile } from '../../api/types';
import { useToast } from '../../components/Toasts';
import { formatSize, MAX_FILE_BYTES, safeFileName } from '../../model/files';

export function useProjectFiles(projectId: string) {
  const [files, setFiles] = useState<BodyFile[] | null>(null);
  const toast = useToast();
  const reload = useCallback(async () => {
    try {
      setFiles(await api.files(projectId));
    } catch (e) {
      toast.fail(e);
    }
  }, [projectId, toast.fail]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { files, reload };
}

export function UploadButton({ projectId, onUploaded, label = 'Upload file', small }: {
  projectId: string;
  onUploaded: (file: BodyFile) => void;
  label?: string;
  small?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const upload = async (file?: globalThis.File) => {
    if (!file) return;
    if (file.size > MAX_FILE_BYTES) {
      toast.fail(new Error('Files can be at most 10 MB'));
      return;
    }
    setBusy(true);
    try {
      const saved = await api.uploadFile(projectId, safeFileName(file.name), file);
      toast.notify(`${saved.name} uploaded`);
      onUploaded(saved);
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
      if (input.current) input.current.value = '';
    }
  };

  return (
    <>
      <button type="button" className={`btn${small ? ' small' : ''}`} disabled={busy} onClick={() => input.current?.click()}>
        <UploadSimple size={small ? 13 : 14} /> {busy ? 'Uploading…' : label}
      </button>
      <input ref={input} type="file" hidden aria-label={label} onChange={(e) => upload(e.target.files?.[0])} />
    </>
  );
}

export function ResponseFiles({ projectId, canEdit }: { projectId: string; canEdit: boolean }) {
  const { files, reload } = useProjectFiles(projectId);
  const toast = useToast();

  const remove = async (f: BodyFile) => {
    try {
      await api.deleteFile(projectId, f.name);
      toast.notify(`${f.name} deleted`);
      reload();
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="panel">
      <div className="panel-head">
        <h2><File size={16} weight="duotone" /> Response files</h2>
        {canEdit && <UploadButton projectId={projectId} small onUploaded={() => reload()} />}
      </div>
      <p className="panel-body muted small" style={{ paddingBottom: 0 }}>
        Upload PDFs, images, large JSON or any other file up to 10 MB, then pick it as a stub's body under Response → File.
      </p>
      {files === null ? (
        <div className="panel-body muted small">Loading…</div>
      ) : files.length === 0 ? (
        <div className="panel-body muted small">No files yet.</div>
      ) : (
        <ul className="file-list">
          {files.map((f) => (
            <li key={f.name}>
              <span className="file-name">
                <strong className="mono">{f.name}</strong>
                <span className="muted small">
                  {formatSize(f.size)}, {f.contentType}, {f.usedBy ? `used by ${f.usedBy} stub${f.usedBy === 1 ? '' : 's'}` : 'not used yet'}
                </span>
              </span>
              <span className="btn-row">
                <button className="btn ghost icon small" aria-label={`Download ${f.name}`} onClick={() => api.downloadFile(projectId, f.name).catch(toast.fail)}>
                  <DownloadSimple size={14} />
                </button>
                {canEdit && (
                  <button className="btn ghost icon small" aria-label={`Delete ${f.name}`} title={f.usedBy ? 'Stubs still use this file' : 'Delete'} onClick={() => remove(f)}>
                    <Trash size={14} />
                  </button>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
