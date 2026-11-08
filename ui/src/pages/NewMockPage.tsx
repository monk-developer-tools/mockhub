import {
  ArrowSquareOut, Books, BracketsCurly, CheckSquareOffset, Cube, Database, FileArrowUp, FileCode, Graph, Lightning, LockKey,
  Plugs, Plus, Robot, ShieldCheck, Sparkle, Table, Terminal, TreeStructure, UsersThree, Waveform, WebhooksLogo,
} from '@phosphor-icons/react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/ui/PageHeader';
import { START_GROUPS, type StartCard } from '../model/starts';
import { useApp } from '../state/AppContext';
import { ProjectDialog } from './ProjectDialog';

export const WEBSITE = 'https://mockhub.devops-monk.com';

const ICONS: Record<string, ReactNode> = {
  openapi: <FileArrowUp size={20} weight="duotone" />,
  wsdl: <FileCode size={20} weight="duotone" />,
  graphql: <Graph size={20} weight="duotone" />,
  grpc: <Plugs size={20} weight="duotone" />,
  crud: <Database size={20} weight="duotone" />,
  json: <BracketsCurly size={20} weight="duotone" />,
  postman: <Cube size={20} weight="duotone" />,
  examples: <Books size={20} weight="duotone" />,
  empty: <Plus size={20} weight="duotone" />,
  csv: <Table size={20} weight="duotone" />,
  webhook: <WebhooksLogo size={20} weight="duotone" />,
  proxy: <Waveform size={20} weight="duotone" />,
  echo: <TreeStructure size={20} weight="duotone" />,
  tunnel: <Terminal size={20} weight="duotone" />,
  'test-data': <Sparkle size={20} weight="duotone" />,
  totp: <LockKey size={20} weight="duotone" />,
  ai: <Robot size={20} weight="duotone" />,
  chaos: <Lightning size={20} weight="duotone" />,
  teams: <UsersThree size={20} weight="duotone" />,
  contract: <ShieldCheck size={20} weight="duotone" />,
  ci: <CheckSquareOffset size={20} weight="duotone" />,
};

export function StartCardView({ card, onEmpty }: { card: StartCard; onEmpty: () => void }) {
  const { aiStatus, canEdit, canManage, isAdmin } = useApp();
  const aiOff = card.needsAi && !aiStatus?.enabled;
  const createOff = (card.kind && !canEdit) || (card.createsProject && !canManage);
  const body = (
    <>
      <span className="start-icon">{ICONS[card.id]}</span>
      <span className="start-text">
        <strong>{card.title}{card.href && <ArrowSquareOut size={13} aria-label="opens the website" />}</strong>
        <span>{card.blurb}</span>
        {aiOff && <span className="start-note">{isAdmin ? 'Turn on an AI provider in AI settings first.' : 'Needs AI, which an admin turns on in AI settings.'}</span>}
        {createOff && <span className="start-note">{card.createsProject ? 'Creating projects needs the manager role in a team.' : 'Creating mocks needs the editor role.'}</span>}
      </span>
    </>
  );
  if (createOff || (aiOff && !isAdmin)) return <div className="start-card disabled" aria-disabled="true">{body}</div>;
  if (aiOff && isAdmin) return <Link className="start-card" to="/admin/ai">{body}</Link>;
  if (card.createsProject) return <button type="button" className="start-card" onClick={onEmpty}>{body}</button>;
  if (card.href) return <a className="start-card" href={`${WEBSITE}${card.href}`} target="_blank" rel="noreferrer">{body}</a>;
  return <Link className="start-card" to={card.to ?? '/'}>{body}</Link>;
}

export function StartGrid({ groups = START_GROUPS, compact = false }: { groups?: typeof START_GROUPS; compact?: boolean }) {
  const [creating, setCreating] = useState(false);
  return (
    <>
      {groups.map((group) => (
        <section key={group.id} className="start-group" aria-labelledby={`start-${group.id}`}>
          <div className="start-group-head">
            <h2 id={`start-${group.id}`}>{group.title}</h2>
            {!compact && <p className="muted small">{group.lede}</p>}
          </div>
          <div className="start-grid">
            {group.cards.map((card) => <StartCardView key={card.id} card={card} onEmpty={() => setCreating(true)} />)}
          </div>
        </section>
      ))}
      {creating && <ProjectDialog onClose={() => setCreating(false)} />}
    </>
  );
}

export function NewMockPage() {
  const { canEdit } = useApp();
  return (
    <div className="page">
      <PageHeader title="Start a new mock" description="Pick what you have. Each card makes a working mock you can call straight away, in a new project or one you already edit." />
      {!canEdit && (
        <div className="callout">You can look at projects. To create mocks, ask a manager of your team for the editor role.</div>
      )}
      <div className="stack">
        <StartGrid />
      </div>
    </div>
  );
}
