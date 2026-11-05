import { CloudArrowDown } from '@phosphor-icons/react';
import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { FetchedSpec } from '../api/types';
import { CopyButton } from '../components/CopyButton';
import { useToast } from '../components/Toasts';
import { PageHeader } from '../components/ui/PageHeader';
import { badgeHtml, badgeMarkdown, BADGE_PATH, isHttpUrl, mockLink, specFromSearch } from '../model/openapiLink';
import { useApp } from '../state/AppContext';
import { slugify } from './ProjectDialog';

export function MockTheseApisPage() {
  const { search } = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const { orgs, currentOrg, writableTeams, refreshProjects, setCurrentOrg } = useApp();
  const [url, setUrl] = useState(specFromSearch(search));
  const [spec, setSpec] = useState<FetchedSpec | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [creating, setCreating] = useState(false);
  const writableOrgs = orgs.filter((o) => writableTeams(o.id).length > 0);
  const orgId = currentOrg && writableTeams(currentOrg.id).length ? currentOrg.id : writableOrgs[0]?.id ?? '';
  const teams = writableTeams(orgId);
  const [teamId, setTeamId] = useState('');
  const chosenTeam = teamId || teams[0]?.id || '';
  const effectiveSlug = slugTouched ? slug : slugify(name);
  const origin = window.location.origin;

  const load = async (target: string) => {
    if (!isHttpUrl(target)) {
      setError('Enter the http or https URL of an OpenAPI or Swagger file');
      return;
    }
    setLoading(true);
    setError('');
    setSpec(null);
    try {
      const fetched = await api.fetchOpenApi(target.trim());
      setSpec(fetched);
      setName(fetched.title || 'Imported API');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const initial = specFromSearch(search);
    if (initial) void load(initial);
  }, [search]);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    if (!spec) return;
    setCreating(true);
    try {
      const project = await api.createProject({ name, slug: effectiveSlug, description: `Mocked from ${spec.url}`, orgId, teamId: chosenTeam });
      const result = await api.importInto(project.id, 'openapi', spec.content);
      await refreshProjects();
      if (orgId !== currentOrg?.id) setCurrentOrg(orgId);
      toast.notify(`Created “${project.name}” with ${result.imported} stubs`);
      navigate(`/projects/${project.id}/stubs`);
    } catch (err) {
      toast.fail(err);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title="Mock these APIs"
        description="Turn an OpenAPI or Swagger file on the web into a mock project on this server: one stub per operation, with example responses."
      />
      <div className="panel">
        <form className="section" onSubmit={(e) => { e.preventDefault(); void load(url); }}>
          <h3>API description</h3>
          <div className="btn-row">
            <input style={{ flex: 1, minWidth: 240 }} type="url" value={url} aria-label="OpenAPI URL" placeholder="https://api.example.com/openapi.yaml"
              onChange={(e) => setUrl(e.target.value)} />
            <button className="btn" type="submit" disabled={loading || !url.trim()}>
              <CloudArrowDown size={14} /> {loading ? 'Loading…' : 'Load'}
            </button>
          </div>
          {error && <div className="callout danger" role="alert" style={{ marginTop: 10 }}><div>{error}</div></div>}
        </form>

        {spec && (
          <form className="section" onSubmit={create}>
            <h3>New project</h3>
            <p className="muted small" style={{ marginBottom: 10 }}>
              <strong>{spec.title || 'Untitled API'}</strong>{spec.version && <> version {spec.version}</>}: {spec.operations} operation{spec.operations === 1 ? '' : 's'}.
            </p>
            {writableOrgs.length === 0 ? (
              <p className="muted small">You need the editor role in a team to create projects. Ask a manager to add you.</p>
            ) : (
              <div className="rows">
                <div className="grid two">
                  <label className="field">
                    <span>Name</span>
                    <input value={name} required onChange={(e) => setName(e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Base path</span>
                    <input value={effectiveSlug} required onChange={(e) => { setSlug(e.target.value); setSlugTouched(true); }} />
                  </label>
                </div>
                {teams.length > 1 && (
                  <label className="field" style={{ maxWidth: 320 }}>
                    <span>Team</span>
                    <select value={chosenTeam} onChange={(e) => setTeamId(e.target.value)}>
                      {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                    </select>
                  </label>
                )}
                <div className="btn-row">
                  <button className="btn primary" type="submit" disabled={creating || !name.trim() || !effectiveSlug}>
                    {creating ? 'Creating…' : 'Create mock project'}
                  </button>
                </div>
              </div>
            )}
          </form>
        )}
      </div>

      {isHttpUrl(url) && (
        <div className="panel">
          <div className="section">
            <h3>Add a button to your API docs</h3>
            <p className="muted small" style={{ marginBottom: 10 }}>
              Readers who click it come to this page on this server with the file filled in. They sign in, then create their own mock.
            </p>
            <p style={{ marginBottom: 10 }}><img src={`${origin}${BADGE_PATH}`} alt="Mock these APIs" height={28} /></p>
            <SnippetRow label="Link" value={mockLink(origin, url)} />
            <SnippetRow label="Markdown" value={badgeMarkdown(origin, url)} />
            <SnippetRow label="HTML" value={badgeHtml(origin, url)} />
          </div>
        </div>
      )}
    </div>
  );
}

function SnippetRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="field" style={{ marginBottom: 8 }}>
      <span>{label} <CopyButton text={value} label={`Copy ${label}`} /></span>
      <pre className="block" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{value}</pre>
    </div>
  );
}
