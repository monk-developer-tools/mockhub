import { ArrowLineDown, ArrowLineUp, GitBranch } from '@phosphor-icons/react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../../../api/client';
import type { GitEvent, GitLinkView, Project } from '../../../api/types';
import { Confirm, Modal } from '../../../components/Modal';
import { Skeleton } from '../../../components/Skeleton';
import { useToast } from '../../../components/Toasts';
import { timeAgo } from '../../../model/time';
import { atLeast, useApp } from '../../../state/AppContext';
import { restoredNote, useDiff } from './SnapshotDialogs';
import { SnapshotDiffView } from './SnapshotDiffView';

export function shortCommit(commit: string | null | undefined): string {
  return commit ? commit.slice(0, 7) : '';
}

function eventLine(label: string, e: GitEvent | undefined): string | null {
  if (!e) return null;
  return `${label} ${timeAgo(e.at)} by ${e.by}${e.commit ? ` · ${shortCommit(e.commit)}` : ''}`;
}

export function GitPanel({ project, onPulled }: { project: Project; onPulled: () => void }) {
  const { canEdit, roleInTeam } = useApp();
  const toast = useToast();
  const canLink = atLeast(roleInTeam(project.orgId, project.teamId), 'manager');
  const [link, setLink] = useState<GitLinkView | null>(null);
  const [editing, setEditing] = useState(false);
  const [pulling, setPulling] = useState(false);
  const [unlinking, setUnlinking] = useState(false);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => api.gitLink(project.id).then(setLink).catch(toast.fail), [project.id]);
  useEffect(() => {
    load();
  }, [load]);

  const push = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const r = await api.gitPush(project.id, message.trim());
      toast.notify(r.changed ? `Pushed ${r.stubs} stub${r.stubs === 1 ? '' : 's'} as ${shortCommit(r.commit)}` : 'Nothing to push: the repository already matches');
      setMessage('');
      load();
    } catch (err) {
      toast.fail(err);
    } finally {
      setBusy(false);
    }
  };
  const unlink = async () => {
    try {
      await api.unlinkGit(project.id);
      setUnlinking(false);
      toast.notify('Unlinked. The repository keeps its files.');
      load();
    } catch (err) {
      toast.fail(err);
    }
  };

  return (
    <section className="panel git-panel" aria-label="Git">
      <div className="panel-head">
        <div>
          <h2><GitBranch size={18} aria-hidden="true" /> Git</h2>
          <p className="muted small">Keep this project in a repository as one file per stub, review changes in pull requests and pull them back.</p>
        </div>
        {link?.linked && canLink && (
          <span className="btn-row">
            <button className="btn ghost small" onClick={() => setEditing(true)}>Change</button>
            <button className="btn ghost small" onClick={() => setUnlinking(true)}>Unlink</button>
          </span>
        )}
      </div>
      <div className="panel-body">
        {!link && <Skeleton rows={2} label="Loading Git link" />}
        {link && !link.linked && (
          canLink ? (
            <div className="git-empty">
              <p className="small">Not linked yet. You need an HTTPS repository URL and an access token that can push.</p>
              <button className="btn primary small" onClick={() => setEditing(true)}><GitBranch size={14} /> Link a repository</button>
            </div>
          ) : (
            <p className="muted small">Not linked. A manager of this project's team can link a repository.</p>
          )
        )}
        {link?.linked && (
          <div className="stack">
            <p className="small mono git-where">
              {link.url} · {link.branch} · {link.folder ? `${link.folder}/` : 'repository root'}
            </p>
            <p className="muted small">
              {[eventLine('Pushed', link.lastPush), eventLine('Pulled', link.lastPull)].filter(Boolean).join(' · ') || 'Not pushed or pulled yet.'}
              {!link.tokenSet && ' · No token: only public repositories can be read, and pushing needs one.'}
            </p>
            {canEdit && (
              <form className="git-actions" onSubmit={push}>
                <label className="field grow">
                  <span className="sr-only">Commit message</span>
                  <input value={message} onChange={(e) => setMessage(e.target.value)} placeholder={`Update ${project.name} mocks from MockHub`} aria-label="Commit message" />
                </label>
                <button className="btn primary small" disabled={busy}><ArrowLineUp size={14} /> {busy ? 'Pushing…' : 'Push'}</button>
                <button type="button" className="btn small" onClick={() => setPulling(true)}><ArrowLineDown size={14} /> Pull</button>
              </form>
            )}
          </div>
        )}
      </div>
      {editing && link && <LinkDialog project={project} link={link} onClose={() => setEditing(false)} onSaved={(l) => { setLink(l); setEditing(false); }} />}
      {pulling && <GitPullDialog project={project} onClose={() => setPulling(false)} onDone={() => { setPulling(false); load(); onPulled(); }} />}
      {unlinking && (
        <Confirm
          title="Unlink the repository?"
          message="MockHub forgets the URL and the token. The repository and this project stay as they are."
          confirmLabel="Unlink"
          onConfirm={unlink}
          onCancel={() => setUnlinking(false)}
        />
      )}
    </section>
  );
}

function LinkDialog({ project, link, onClose, onSaved }: {
  project: Project; link: GitLinkView; onClose: () => void; onSaved: (l: GitLinkView) => void;
}) {
  const toast = useToast();
  const [url, setUrl] = useState(link.url ?? '');
  const [branch, setBranch] = useState(link.branch ?? 'main');
  const [folder, setFolder] = useState(link.linked ? link.folder ?? '' : link.suggestedFolder ?? '');
  const [username, setUsername] = useState(link.username ?? '');
  const [token, setToken] = useState('');
  const [busy, setBusy] = useState(false);
  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const saved = await api.saveGitLink(project.id, { url: url.trim(), branch: branch.trim(), folder: folder.trim(), username: username.trim(), token: token.trim() || undefined });
      toast.notify('Linked. Push to write the project to the repository.');
      onSaved(saved);
    } catch (err) {
      toast.fail(err);
      setBusy(false);
    }
  };
  return (
    <Modal
      title={link.linked ? 'Change the Git link' : 'Link a Git repository'}
      description="Push writes mockhub.json, mappings/ and __files/ in the folder and leaves the rest of the repository alone."
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" form="git-link" disabled={busy || !url.trim()}>{busy ? 'Saving…' : 'Save'}</button>
        </>
      }
    >
      <form id="git-link" className="stack" onSubmit={save}>
        <label className="field">
          <span>Repository URL</span>
          <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://github.com/acme/mocks.git" required />
        </label>
        <div className="grid two">
          <label className="field">
            <span>Branch</span>
            <input value={branch} onChange={(e) => setBranch(e.target.value)} placeholder="main" />
          </label>
          <label className="field">
            <span>Folder</span>
            <input value={folder} onChange={(e) => setFolder(e.target.value)} placeholder="Repository root" />
          </label>
        </div>
        <div className="grid two">
          <label className="field">
            <span>Access token</span>
            <input type="password" value={token} onChange={(e) => setToken(e.target.value)} autoComplete="off"
              placeholder={link.tokenSet ? 'Saved; leave empty to keep it' : 'Needs read and write access'} />
          </label>
          <label className="field">
            <span>Username (optional)</span>
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Only Bitbucket needs one" />
          </label>
        </div>
        <p className="muted small">The token is stored encrypted and never shown again. SSH URLs aren't supported.</p>
      </form>
    </Modal>
  );
}

function GitPullDialog({ project, onClose, onDone }: { project: Project; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const diff = useDiff(() => api.gitPreview(project.id), [project.id]);
  const pull = async () => {
    setBusy(true);
    try {
      const r = await api.gitPull(project.id);
      toast.notify(`Pulled ${shortCommit(r.commit)}. ${restoredNote({ name: '', safety: r.safety, stubs: r.stubs, files: r.files })}`);
      onDone();
    } catch (err) {
      toast.fail(err);
      setBusy(false);
    }
  };
  return (
    <Modal
      title="Pull from Git"
      description={`Replace the stubs, response files and runtime behaviour of ${project.name} with what the repository holds. A safety snapshot is saved first.`}
      wide
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={busy || !diff || diff.identical} onClick={pull}>{busy ? 'Pulling…' : 'Pull into this project'}</button>
        </>
      }
    >
      {diff ? <SnapshotDiffView diff={diff} basePath={project.basePath} /> : <Skeleton rows={3} label="Reading the repository" />}
    </Modal>
  );
}
