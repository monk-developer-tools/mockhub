import { ArrowUDownLeft, Gauge, Globe, ShieldCheck, Timer } from '@phosphor-icons/react';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { LatencyMode, RatePeriod, RuntimeSettings } from '../../api/types';
import { useToast } from '../../components/Toasts';
import { type CorsMode, type RuntimeForm, toForm, toSettings } from '../../model/runtime';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

const LATENCY: [LatencyMode, string][] = [['none', 'None'], ['fixed', 'Fixed'], ['uniform', 'Random range'], ['lognormal', 'Realistic']];
const CORS: [CorsMode, string][] = [['server', 'Server default'], ['custom', 'Allow list'], ['fail', 'Fail on purpose']];

export function RuntimePanel() {
  const { project } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const [saved, setSaved] = useState<RuntimeSettings | null>(null);
  const [form, setForm] = useState<RuntimeForm | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let live = true;
    api.runtime(project.id).then((s) => {
      if (!live) return;
      setSaved(s);
      setForm(toForm(s));
    }).catch(toast.fail);
    return () => {
      live = false;
    };
  }, [project.id]);

  if (!form || !saved) return null;

  const set = <K extends keyof RuntimeForm>(key: K, value: RuntimeForm[K]) => setForm({ ...form, [key]: value });
  const changed = JSON.stringify(form) !== JSON.stringify(toForm(saved));

  const save = async () => {
    setBusy(true);
    try {
      const next = await api.updateRuntime(project.id, toSettings(form));
      setSaved(next);
      setForm(toForm(next));
      toast.notify('Runtime behaviour saved');
    } catch (e) {
      toast.fail(e);
    } finally {
      setBusy(false);
    }
  };

  const where = project.root ? 'this project' : project.basePath;

  return (
    <div className="panel runtime-panel">
      <div className="section">
        <h3>Runtime behaviour</h3>
        <p className="muted small">
          How every call to {where} behaves, whichever stub answers it.
          {!canEdit && ' You can look, but only editors can change these.'}
        </p>
      </div>
      <fieldset className="runtime-fields" disabled={!canEdit || busy}>
        <Rule icon={<ArrowUDownLeft size={15} weight="duotone" />} title="Default response" on={form.defaultOn} onToggle={(v) => set('defaultOn', v)}
          text={form.defaultOn ? 'Calls that no stub matches get this answer instead of WireMock’s 404 page.' : 'Calls that no stub matches get WireMock’s 404 page, which lists the closest stubs.'}>
          <div className="grid two">
            <label className="field">
              <span>Status</span>
              <input type="number" min={100} max={599} value={form.defaultStatus} onChange={(e) => set('defaultStatus', e.target.value)} />
            </label>
            <label className="check runtime-check">
              <input type="checkbox" checked={form.defaultTemplated} onChange={(e) => set('defaultTemplated', e.target.checked)} />
              Use templates like <span className="mono">{'{{request.path}}'}</span>
            </label>
          </div>
          <label className="field">
            <span>Headers <span className="hint">one per line, Name: value</span></span>
            <textarea className="mono" rows={2} value={form.defaultHeaders} onChange={(e) => set('defaultHeaders', e.target.value)} />
          </label>
          <label className="field">
            <span>Body</span>
            <textarea className="mono" rows={4} value={form.defaultBody} onChange={(e) => set('defaultBody', e.target.value)} />
          </label>
        </Rule>

        <Rule icon={<Gauge size={15} weight="duotone" />} title="Rate limit" on={form.rateOn} onToggle={(v) => set('rateOn', v)}
          text={form.rateOn ? 'Calls over the limit get a 429 with Retry-After and X-RateLimit headers, like a real API.' : 'No limit. Turn it on to test how your service handles 429s.'}>
          <div className="runtime-inline">
            <input type="number" min={1} aria-label="Requests" value={form.rateRequests} onChange={(e) => set('rateRequests', e.target.value)} />
            <span>requests per</span>
            <select aria-label="Period" value={form.ratePer} onChange={(e) => set('ratePer', e.target.value as RatePeriod)}>
              <option value="second">second</option>
              <option value="minute">minute</option>
              <option value="hour">hour</option>
            </select>
          </div>
          <p className="muted small">A stub can have its own, stricter limit in its Failures section.</p>
        </Rule>

        <Rule icon={<Timer size={15} weight="duotone" />} title="Latency"
          text="Slows every answer down. Stubs that set their own delay keep it.">
          <div className="segmented" role="group" aria-label="Delay">
            {LATENCY.map(([mode, label]) => (
              <button key={mode} type="button" aria-pressed={form.latencyMode === mode} onClick={() => set('latencyMode', mode)}>{label}</button>
            ))}
          </div>
          {form.latencyMode === 'fixed' && (
            <Ms label="Delay" value={form.fixedMs} onChange={(v) => set('fixedMs', v)} />
          )}
          {form.latencyMode === 'uniform' && (
            <div className="grid two">
              <Ms label="Shortest" value={form.minMs} onChange={(v) => set('minMs', v)} />
              <Ms label="Longest" value={form.maxMs} onChange={(v) => set('maxMs', v)} />
            </div>
          )}
          {form.latencyMode === 'lognormal' && (
            <div className="grid two">
              <Ms label="Typical delay" value={form.medianMs} onChange={(v) => set('medianMs', v)} />
              <label className="field">
                <span>Spread <span className="hint">0.1 steady, 1 very spiky</span></span>
                <input type="number" step={0.05} min={0.01} max={2} value={form.sigma} onChange={(e) => set('sigma', e.target.value)} />
              </label>
            </div>
          )}
          <label className="check">
            <input type="checkbox" checked={form.loadOn} onChange={(e) => set('loadOn', e.target.checked)} />
            Get slower under load
          </label>
          {form.loadOn && (
            <div className="runtime-inline">
              <span>Above</span>
              <input type="number" min={1} aria-label="Requests per second" value={form.loadAfterRps} onChange={(e) => set('loadAfterRps', e.target.value)} />
              <span>requests a second, add</span>
              <input type="number" min={1} aria-label="Milliseconds per extra request" value={form.loadMsPerRequest} onChange={(e) => set('loadMsPerRequest', e.target.value)} />
              <span>ms for each extra one</span>
            </div>
          )}
        </Rule>

        <Rule icon={<Globe size={15} weight="duotone" />} title="CORS"
          text={form.corsMode === 'server'
            ? 'Follows the server setting. By default any website can call the mock from a browser.'
            : form.corsMode === 'fail'
              ? 'Browsers block every call, so you can see how your frontend handles a CORS error. Calls from servers and curl still work.'
              : 'Only these websites can call the mock from a browser.'}>
          <div className="segmented" role="group" aria-label="CORS">
            {CORS.map(([mode, label]) => (
              <button key={mode} type="button" aria-pressed={form.corsMode === mode} onClick={() => set('corsMode', mode)}>{label}</button>
            ))}
          </div>
          {form.corsMode === 'custom' && (
            <>
              <label className="field">
                <span>Allowed origins <span className="hint">one per line, e.g. https://app.example.com, https://*.example.com or *</span></span>
                <textarea className="mono" rows={3} value={form.corsOrigins} onChange={(e) => set('corsOrigins', e.target.value)} />
              </label>
              <div className="grid two">
                <label className="field">
                  <span>Methods</span>
                  <input type="text" className="mono" value={form.corsMethods} onChange={(e) => set('corsMethods', e.target.value)} />
                </label>
                <label className="field">
                  <span>Request headers <span className="hint">empty allows any</span></span>
                  <input type="text" className="mono" value={form.corsHeaders} onChange={(e) => set('corsHeaders', e.target.value)} />
                </label>
              </div>
              <label className="check">
                <input type="checkbox" checked={form.corsCredentials} onChange={(e) => set('corsCredentials', e.target.checked)} />
                Allow cookies and credentials
              </label>
            </>
          )}
        </Rule>

        <Rule icon={<ShieldCheck size={15} weight="duotone" />} title="IP allow list"
          text="Only these addresses can call the mock; everyone else gets a 403. Leave it empty to allow everyone.">
          <label className="field">
            <span>Addresses and ranges <span className="hint">one per line, e.g. 10.0.0.0/8 or 2001:db8::/32</span></span>
            <textarea className="mono" rows={3} value={form.ipAllowList} onChange={(e) => set('ipAllowList', e.target.value)} />
          </label>
        </Rule>
      </fieldset>
      {canEdit && changed && (
        <div className="section runtime-save">
          <span className="muted small">Unsaved changes</span>
          <button className="btn" type="button" disabled={busy} onClick={() => setForm(toForm(saved))}>Discard</button>
          <button className="btn primary" type="button" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
        </div>
      )}
    </div>
  );
}

function Rule({ icon, title, text, on, onToggle, children }: {
  icon: ReactNode;
  title: string;
  text: string;
  on?: boolean;
  onToggle?: (on: boolean) => void;
  children: ReactNode;
}) {
  const open = on ?? true;
  return (
    <div className="section runtime-rule">
      <h4>
        <span className="runtime-title">{icon}{title}</span>
        {onToggle && (
          <label className="switch">
            <input type="checkbox" role="switch" checked={open} aria-label={title} onChange={(e) => onToggle(e.target.checked)} />
          </label>
        )}
      </h4>
      <p className="muted small">{text}</p>
      {open && <div className="stack runtime-body">{children}</div>}
    </div>
  );
}

function Ms({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="field runtime-ms">
      <span>{label}</span>
      <span className="input-suffix">
        <input type="number" min={0} max={60000} value={value} onChange={(e) => onChange(e.target.value)} />
        <span className="muted small">ms</span>
      </span>
    </label>
  );
}
