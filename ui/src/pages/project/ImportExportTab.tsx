import { DownloadSimple } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import { FilePicker } from '../../components/FilePicker';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';
import { ResponseFiles } from './ResponseFiles';

const EXPORTS = [
  { format: 'postman', name: 'Postman collection', detail: 'Collection v2.1 with a baseUrl variable, folders and a status test per request. Import it in Postman.' },
  { format: 'bruno', name: 'Bruno collection', detail: 'A zip of .bru files with a MockHub environment. Unzip it and choose “Open collection” in Bruno.' },
  { format: 'wiremock', name: 'WireMock mappings', detail: 'Plain WireMock JSON. Import it into another project or any WireMock server.' },
];

const IMPORTS = [
  { format: 'openapi', name: 'OpenAPI / Swagger', accept: '.json,.yaml,.yml', detail: 'One stub per operation, using examples from the spec or generated sample data.' },
  { format: 'wsdl', name: 'WSDL (SOAP)', accept: '.wsdl,.xml', detail: 'One stub per operation with sample envelopes, plus ?wsdl and a fault for unknown operations.' },
  { format: 'wiremock', name: 'WireMock mappings', accept: '.json', detail: 'A mappings file, an array of stubs or a single stub.' },
  {
    format: 'wiremock-zip',
    name: 'WireMock folder',
    accept: '.zip',
    detail: 'Moving from WireMock? Zip the folder that holds mappings and __files (for example src/test/resources/wiremock). Every mapping is imported and every body file is copied into this project.',
  },
];

export function ImportExportTab() {
  const { project, reloadProject } = useProject();
  const { canEdit, refreshProjects } = useApp();
  const toast = useToast();
  const [format, setFormat] = useState(IMPORTS[0].format);
  const [path, setPath] = useState('');
  const [stripPrefix, setStripPrefix] = useState('');
  const [result, setResult] = useState<string>('');
  const [filesVersion, setFilesVersion] = useState(0);
  const current = IMPORTS.find((i) => i.format === format)!;

  const runImport = async (text: string, fileName: string) => {
    const options: Record<string, string> = {};
    if (format === 'wsdl' && path) options.path = path;
    if (format === 'openapi' && stripPrefix) options.stripPrefix = stripPrefix;
    try {
      const r = await api.importInto(project.id, format, text, options);
      setResult(`Imported ${r.imported} stub${r.imported === 1 ? '' : 's'} from ${fileName}.`);
      toast.notify(`Imported ${r.imported} stubs`);
      reloadProject();
      refreshProjects();
    } catch (e) {
      setResult('');
      toast.fail(e);
    }
  };

  const runZipImport = async (file: File) => {
    try {
      const r = await api.importZip(project.id, file);
      const missing = r.missingFiles.length ? ` ${r.missingFiles.length} body file${r.missingFiles.length === 1 ? ' was' : 's were'} not in the zip: ${r.missingFiles.join(', ')}.` : '';
      setResult(`Imported ${r.imported} stub${r.imported === 1 ? '' : 's'} and ${r.files} body file${r.files === 1 ? '' : 's'} from ${file.name}.${missing}`);
      toast.notify(`Imported ${r.imported} stubs`);
      setFilesVersion((v) => v + 1);
      reloadProject();
      refreshProjects();
    } catch (e) {
      setResult('');
      toast.fail(e);
    }
  };

  const download = async (f: string) => {
    try {
      await api.exportProject(project.id, f);
    } catch (e) {
      toast.fail(e);
    }
  };

  return (
    <div className="split">
      <div className="panel">
        <div className="panel-head">
          <h2>Export</h2>
        </div>
        {EXPORTS.map((e) => (
          <div className="section" key={e.format}>
            <h3>
              {e.name}
              <button className="btn small" onClick={() => download(e.format)}>
                <DownloadSimple size={14} /> Download
              </button>
            </h3>
            <p className="muted small">{e.detail}</p>
          </div>
        ))}
        <div className="section">
          <p className="muted small">
            Collections point <span className="mono">baseUrl</span> at <span className="mono">{project.mockUrl}</span>. Change that one
            variable to run the same requests against the real service.
          </p>
        </div>
      </div>

      <div className="stack">
        {canEdit && (
          <div className="panel">
            <div className="panel-head">
              <h2>Import into {project.name}</h2>
            </div>
            <div className="panel-body stack">
              <div className="segmented" role="group" aria-label="Import format">
                {IMPORTS.map((i) => (
                  <button key={i.format} aria-pressed={i.format === format} onClick={() => setFormat(i.format)}>
                    {i.name}
                  </button>
                ))}
              </div>
              <p className="muted small">{current.detail}</p>
              {format === 'wsdl' && (
                <label className="field">
                  <span>Endpoint path <span className="hint">optional, defaults to the path in soap:address</span></span>
                  <input type="text" className="mono" placeholder="/ws/customers" value={path} onChange={(e) => setPath(e.target.value)} />
                </label>
              )}
              {format === 'openapi' && (
                <label className="field">
                  <span>Strip path prefix <span className="hint">optional, e.g. /api/v1</span></span>
                  <input type="text" className="mono" value={stripPrefix} onChange={(e) => setStripPrefix(e.target.value)} />
                </label>
              )}
              {format === 'wiremock-zip' && !project.root && (
                <div className="callout small">
                  <span>
                    Your WireMock URLs will answer under <span className="mono">{project.basePath}</span>. To keep them exactly as they are, turn on
                    <strong> Answer at the root</strong> in this project's settings before you point your services here.
                  </span>
                </div>
              )}
              {format === 'wiremock-zip' ? (
                <FilePicker accept=".zip" label="Drop a .zip of your WireMock folder here or click to choose" onFile={runZipImport} />
              ) : (
                <FilePicker accept={current.accept} label={`Drop a ${current.name} file here or click to choose`} onText={runImport} />
              )}
              {result && <div className="callout">{result}</div>}
            </div>
          </div>
        )}
        <ResponseFiles key={filesVersion} projectId={project.id} canEdit={canEdit} />
      </div>
    </div>
  );
}
