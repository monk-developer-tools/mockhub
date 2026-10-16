import { DownloadSimple } from '@phosphor-icons/react';
import { useState } from 'react';
import { api } from '../../api/client';
import { FilePicker } from '../../components/FilePicker';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

const EXPORTS = [
  { format: 'postman', name: 'Postman collection', detail: 'Collection v2.1 with a baseUrl variable, folders and a status test per request. Import it in Postman.' },
  { format: 'bruno', name: 'Bruno collection', detail: 'A zip of .bru files with a MockHub environment. Unzip it and choose “Open collection” in Bruno.' },
  { format: 'wiremock', name: 'WireMock mappings', detail: 'Plain WireMock JSON. Import it into another project or any WireMock server.' },
];

const IMPORTS = [
  { format: 'openapi', name: 'OpenAPI / Swagger', accept: '.json,.yaml,.yml', detail: 'One stub per operation, using examples from the spec or generated sample data.' },
  { format: 'wsdl', name: 'WSDL (SOAP)', accept: '.wsdl,.xml', detail: 'One stub per operation with sample envelopes, plus ?wsdl and a fault for unknown operations.' },
  { format: 'wiremock', name: 'WireMock mappings', accept: '.json', detail: 'A mappings file, an array of stubs or a single stub.' },
];

export function ImportExportTab() {
  const { project, reloadProject } = useProject();
  const { canEdit, refreshProjects } = useApp();
  const toast = useToast();
  const [format, setFormat] = useState(IMPORTS[0].format);
  const [path, setPath] = useState('');
  const [stripPrefix, setStripPrefix] = useState('');
  const [result, setResult] = useState<string>('');
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
            <FilePicker accept={current.accept} label={`Drop a ${current.name} file here or click to choose`} onText={runImport} />
            {result && <div className="callout">{result}</div>}
          </div>
        </div>
      )}
    </div>
  );
}
