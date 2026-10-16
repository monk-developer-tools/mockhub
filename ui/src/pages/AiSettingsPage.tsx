import { ArrowSquareOut, CheckCircle, Sparkle, XCircle } from '@phosphor-icons/react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { AiSettings, AiTestResult } from '../api/types';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

const GUIDES: Record<string, { hint: string; keyHint: string; modelHint: string; link?: { href: string; label: string } }> = {
  openrouter: {
    hint: 'One key for hundreds of models from Anthropic, OpenAI, Google, Meta, Mistral and others.',
    keyHint: 'Create one at openrouter.ai/keys. It starts with sk-or-.',
    modelHint: 'openrouter/auto picks a model for you. Choose a model that supports tool calling, e.g. anthropic/claude-sonnet-4.5 or openai/gpt-4.1.',
    link: { href: 'https://openrouter.ai/keys', label: 'Get an OpenRouter key' },
  },
  litellm: {
    hint: 'Use the LiteLLM proxy your company already runs, with its budgets, logging and approved models.',
    keyHint: 'A LiteLLM virtual key or the master key. Leave empty if the proxy has no auth.',
    modelHint: 'The model_name from the proxy config, e.g. gpt-4o or claude-sonnet.',
    link: { href: 'https://docs.litellm.ai/docs/proxy/quick_start', label: 'LiteLLM proxy docs' },
  },
  openai: {
    hint: 'OpenAI, Azure OpenAI, Ollama, vLLM, LM Studio or anything else that speaks the OpenAI chat completions API.',
    keyHint: 'The bearer token for the API, if it needs one.',
    modelHint: 'The model id the API expects, e.g. gpt-4.1 or llama3.1.',
  },
};

export function AiSettingsPage() {
  const toast = useToast();
  const { refreshAiStatus } = useApp();
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [test, setTest] = useState<AiTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setSettings(await api.aiSettings());
    } catch (e) {
      toast.fail(e);
    }
  }, [toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  if (!settings) return <div className="page"><Skeleton rows={6} label="Loading AI settings" /></div>;

  const provider = settings.providers.find((p) => p.id === settings.provider) ?? settings.providers[0];
  const guide = GUIDES[settings.provider] ?? GUIDES.openai;
  const set = <K extends keyof AiSettings>(key: K, value: AiSettings[K]) => setSettings({ ...settings, [key]: value });
  const body = (): Partial<AiSettings> => {
    const { providers: _providers, apiKeySet: _set, apiKeyFromEnvironment: _env, ...rest } = settings;
    return { ...rest, apiKey: apiKey || undefined };
  };

  const changeProvider = (id: string) => {
    setSettings({ ...settings, provider: id, baseUrl: '', model: '' });
    setTest(null);
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const saved = await api.saveAiSettings(body());
      setSettings(saved);
      setApiKey('');
      await refreshAiStatus();
      toast.notify(saved.enabled ? 'The assistant is on. Open any project and choose Assistant.' : 'AI settings saved');
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setTesting(true);
    try {
      setTest(await api.testAi(body()));
    } catch (err) {
      setTest({ ok: false, error: err instanceof Error ? err.message : String(err) });
    } finally {
      setTesting(false);
    }
  };

  const model = settings.model || provider.defaultModel;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>AI assistant</h1>
          <p className="muted">Let people describe mocks in plain English and review what the assistant stages before it goes live.</p>
        </div>
        <span className={`status-pill ${settings.enabled ? 'ok' : 'off'}`}>{settings.enabled ? 'On' : 'Off'}</span>
      </div>

      <form className="split sso" onSubmit={save}>
        <div className="stack">
          <div className="panel">
            <div className="panel-head">
              <h2><Sparkle size={15} /> Model provider</h2>
              <label className="switch">
                <input type="checkbox" checked={settings.enabled} onChange={(e) => set('enabled', e.target.checked)} />
                <span>Enabled</span>
              </label>
            </div>
            <div className="panel-body stack">
              <div className="provider-picker" role="radiogroup" aria-label="Provider">
                {settings.providers.map((p) => (
                  <button
                    type="button"
                    key={p.id}
                    role="radio"
                    aria-checked={settings.provider === p.id}
                    className="provider-option"
                    onClick={() => changeProvider(p.id)}
                  >
                    <strong>{p.label}</strong>
                    <span className="muted small">{GUIDES[p.id]?.hint}</span>
                  </button>
                ))}
              </div>
              <div className="grid two">
                <label className="field">
                  <span>Base URL</span>
                  <input type="url" className="mono" placeholder={provider.defaultBaseUrl} value={settings.baseUrl} onChange={(e) => set('baseUrl', e.target.value)} />
                  <span className="hint">Leave empty for {provider.defaultBaseUrl}. MockHub calls &lt;base URL&gt;/chat/completions.</span>
                </label>
                <label className="field">
                  <span>API key {!provider.requiresApiKey && <span className="hint">optional</span>}</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    disabled={settings.apiKeyFromEnvironment}
                    placeholder={settings.apiKeyFromEnvironment ? 'Set by MOCKHUB_AI_API_KEY' : settings.apiKeySet ? 'Saved. Type to replace it.' : 'Paste the key'}
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                  />
                  <span className="hint">{guide.keyHint} Stored on the server and never shown again.</span>
                </label>
                <label className="field">
                  <span>Model</span>
                  <input type="text" className="mono" list="ai-models" placeholder={provider.defaultModel || 'model id'} value={settings.model} onChange={(e) => set('model', e.target.value)} />
                  <datalist id="ai-models">{test?.models?.map((m) => <option key={m} value={m} />)}</datalist>
                  <span className="hint">{guide.modelHint}</span>
                </label>
                <label className="field">
                  <span>Creativity <span className="hint">{settings.temperature.toFixed(1)}</span></span>
                  <input type="range" min={0} max={1} step={0.1} value={settings.temperature} onChange={(e) => set('temperature', Number(e.target.value))} />
                  <span className="hint">Low values give predictable stubs. 0.2 works well.</span>
                </label>
              </div>
              <div className="btn-row">
                <button type="button" className="btn" onClick={runTest} disabled={testing}>{testing ? 'Checking…' : 'Test connection'}</button>
                {guide.link && (
                  <a className="small" href={guide.link.href} target="_blank" rel="noreferrer">{guide.link.label} <ArrowSquareOut size={12} /></a>
                )}
              </div>
              {test && (
                <span className={`test-result ${test.ok ? 'ok' : 'fail'}`} role="status">
                  {test.ok ? <CheckCircle size={15} /> : <XCircle size={15} />}
                  {test.ok
                    ? `Connected. ${test.modelCount} model${test.modelCount === 1 ? '' : 's'} available${model && !test.modelFound ? `, but “${model}” isn't one of them` : ''}.`
                    : test.error}
                </span>
              )}
            </div>
          </div>
          <div className="save-bar">
            <button className="btn primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save settings'}</button>
          </div>
        </div>

        <aside className="panel guide">
          <div className="panel-head"><h2>How the assistant works</h2></div>
          <ol className="guide-steps">
            <li>In any project, open <strong>Assistant</strong> and describe what you need, or paste an OpenAPI spec, a WSDL or curl commands.</li>
            <li>The assistant reads the project's stubs and recent requests, then <strong>stages</strong> new stubs, changes and deletions.</li>
            <li>You review each change as a diff and apply the ones you want. Nothing changes until someone with the editor role applies it.</li>
            <li>Viewers can ask questions and draft changes, but only editors can apply them.</li>
          </ol>
          <div className="panel-body small muted">
            MockHub sends the conversation, the project's stubs and, when asked, recent request logs to the provider you choose. Pick
            a provider your organisation allows, or run models yourself behind LiteLLM.
          </div>
        </aside>
      </form>
    </div>
  );
}
