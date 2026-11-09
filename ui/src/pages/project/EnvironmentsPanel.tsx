import { Plus, Trash, X } from '@phosphor-icons/react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { ProjectEnvironment } from '../../api/types';
import { useToast } from '../../components/Toasts';
import { atLeast, useApp } from '../../state/AppContext';
import { announceEnvironments, useEnvironments } from '../../state/useEnvironments';
import { useProject } from './ProjectPage';

interface Row {
  key: string;
  value: string;
}

interface Draft {
  name: string;
  rows: Row[];
}

const toDraft = (e: ProjectEnvironment): Draft => ({ name: e.name, rows: Object.entries(e.variables).map(([key, value]) => ({ key, value })) });

export function EnvironmentsPanel() {
  const { project } = useProject();
  const { roleInTeam } = useApp();
  const toast = useToast();
  const canEdit = atLeast(roleInTeam(project.orgId, project.teamId), 'editor');
  const { environments } = useEnvironments(project.id);
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [active, setActive] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!environments) return;
    setDrafts(environments.environments.map(toDraft));
    setActive(environments.active ?? '');
  }, [environments]);

  const set = (i: number, patch: Partial<Draft>) => setDrafts((d) => d.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const setRow = (i: number, r: number, patch: Partial<Row>) => set(i, { rows: drafts[i].rows.map((x, j) => (j === r ? { ...x, ...patch } : x)) });
  const add = () => {
    const names = drafts.map((d) => d.name);
    const name = ['dev', 'qa', 'staging', 'prod'].find((n) => !names.includes(n)) ?? `env-${drafts.length + 1}`;
    const keys = drafts[0]?.rows.map((r) => ({ key: r.key, value: '' })) ?? [{ key: 'baseUrl', value: '' }];
    setDrafts([...drafts, { name, rows: keys }]);
    if (!active) setActive(name);
  };
  const save = async () => {
    setBusy(true);
    try {
      const body = {
        active: drafts.some((d) => d.name === active) ? active : drafts[0]?.name ?? null,
        environments: drafts.map((d) => ({ name: d.name.trim(), variables: Object.fromEntries(d.rows.filter((r) => r.key.trim()).map((r) => [r.key.trim(), r.value])) })),
      };
      await api.saveEnvironments(project.id, body);
      announceEnvironments(project.id);
      toast.notify(drafts.length ? 'Environments saved' : 'Environments removed');
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="panel" id="environments">
      <div className="section">
        <h3>Environments</h3>
        <p className="muted small">
          Give the same mock different values for dev, qa or staging. Stubs read a value with <code>{"{{env 'baseUrl'}}"}</code> and can be limited to
          some environments. Callers pick one with the <code>X-MockHub-Environment</code> header; otherwise the active one answers.
        </p>
        {environments === null ? (
          <p className="muted small">Loading…</p>
        ) : (
          <div className="stack env-list">
            {drafts.length === 0 && <p className="small">No environments: every call gets the same answers.</p>}
            {drafts.map((d, i) => (
              <fieldset key={i} className="env-card">
                <legend className="sr-only">Environment {d.name}</legend>
                <div className="env-head">
                  <label className="check">
                    <input type="radio" name="active-env" checked={active === d.name} disabled={!canEdit} onChange={() => setActive(d.name)} />
                    Active
                  </label>
                  <input type="text" className="mono env-name" aria-label={`Name of environment ${i + 1}`} value={d.name} disabled={!canEdit}
                    onChange={(e) => { if (active === d.name) setActive(e.target.value); set(i, { name: e.target.value }); }} />
                  {canEdit && (
                    <button type="button" className="btn ghost icon small" aria-label={`Remove ${d.name}`} onClick={() => setDrafts(drafts.filter((_, j) => j !== i))}>
                      <Trash size={14} />
                    </button>
                  )}
                </div>
                <div className="rows">
                  {d.rows.map((r, j) => (
                    <div className="row env-row" key={j}>
                      <input type="text" className="mono" aria-label={`${d.name} variable ${j + 1} name`} placeholder="baseUrl" value={r.key} disabled={!canEdit}
                        onChange={(e) => setRow(i, j, { key: e.target.value })} />
                      <input type="text" className="mono" aria-label={`${d.name} variable ${j + 1} value`} placeholder="https://qa.example.com" value={r.value} disabled={!canEdit}
                        onChange={(e) => setRow(i, j, { value: e.target.value })} />
                      {canEdit && (
                        <button type="button" className="btn ghost icon small" aria-label={`Remove ${d.name} variable ${j + 1}`} onClick={() => set(i, { rows: d.rows.filter((_, k) => k !== j) })}>
                          <X size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                  {canEdit && (
                    <div><button type="button" className="btn small ghost" onClick={() => set(i, { rows: [...d.rows, { key: '', value: '' }] })}><Plus size={13} /> Variable</button></div>
                  )}
                </div>
              </fieldset>
            ))}
            {canEdit && (
              <div className="btn-row">
                <button type="button" className="btn small" onClick={add}><Plus size={13} /> Add environment</button>
                <span style={{ flex: 1 }} />
                <button type="button" className="btn primary small" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save environments'}</button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
