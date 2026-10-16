import { ArrowUp, Check, ChevronDown, ChevronRight, RotateCcw, Sparkles, Trash2, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { AssistantStep, ChatMessage, ProposedChange, Stub } from '../../api/types';
import { ChatText } from '../../components/ChatText';
import { StubRoute } from '../../components/StubRoute';
import { useToast } from '../../components/Toasts';
import { collapse, diffLines } from '../../model/diff';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

const STORAGE_KEY = 'mockhub.chat.';
const POLL_MS = 1500;

export const SUGGESTIONS = [
  'Create CRUD endpoints for /customers with realistic example data',
  'Add a 404 response for unknown order ids',
  'Create stubs for the requests that matched nothing',
  'Make every POST answer after a 2 second delay',
  'Add a SOAP GetCustomer operation at /ws/customers',
];

interface Conversation {
  messages: ChatMessage[];
  pending: ProposedChange[];
  jobId?: string;
}

function load(projectId: string): Conversation {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY + projectId);
    if (raw) return JSON.parse(raw) as Conversation;
  } catch {
    return { messages: [], pending: [] };
  }
  return { messages: [], pending: [] };
}

function save(projectId: string, conversation: Conversation) {
  try {
    sessionStorage.setItem(STORAGE_KEY + projectId, JSON.stringify(conversation));
  } catch {
    return;
  }
}

export function stepsLabel(steps: AssistantStep[]) {
  const count = (tools: string[]) => steps.filter((s) => tools.includes(s.tool)).length;
  const parts: string[] = [];
  const looked = count(['list_stubs', 'get_stub']);
  const logs = count(['list_requests']);
  const staged = count(['create_stub', 'update_stub', 'delete_stub']);
  const errors = steps.filter((s) => s.detail.startsWith('Error')).length;
  if (looked) parts.push('read the stubs');
  if (logs) parts.push('checked the request log');
  if (staged) parts.push(`staged ${staged} change${staged === 1 ? '' : 's'}`);
  if (errors) parts.push(`fixed ${errors} mistake${errors === 1 ? '' : 's'}`);
  return parts.join(' · ');
}

export function AssistantTab() {
  const { project, reloadProject } = useProject();
  const { aiStatus, canEdit, isAdmin, refreshProjects } = useApp();
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const [conversation, setConversation] = useState<Conversation>(() => load(project.id));
  const [input, setInput] = useState('');
  const [starting, setStarting] = useState(false);
  const [liveSteps, setLiveSteps] = useState<AssistantStep[]>([]);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const autoSent = useRef(false);
  const mounted = useRef(true);

  const { messages, pending, jobId } = conversation;
  const thinking = starting || Boolean(jobId);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    save(project.id, conversation);
  }, [project.id, conversation]);
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages.length, thinking, liveSteps.length]);

  useEffect(() => {
    if (!jobId) return;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const finish = (update: (c: Conversation) => Conversation) => {
      setLiveSteps([]);
      setConversation((c) => update({ messages: c.messages, pending: c.pending }));
      inputRef.current?.focus();
    };
    const poll = async () => {
      try {
        const job = await api.assistantJob(project.id, jobId);
        if (stopped) return;
        if (job.status === 'running') {
          setLiveSteps(job.steps);
          timer = setTimeout(poll, POLL_MS);
        } else if (job.status === 'done' && job.reply) {
          const reply = job.reply;
          finish((c) => ({
            messages: [...c.messages, { role: 'assistant', content: reply.reply || 'Done.', steps: reply.steps }],
            pending: reply.changes,
          }));
        } else {
          setError(job.error || 'The assistant failed. Try again.');
          finish((c) => c);
        }
      } catch (e) {
        if (stopped) return;
        if (e instanceof ApiError && e.status === 404) {
          setError(e.message);
          finish((c) => c);
        } else {
          timer = setTimeout(poll, POLL_MS * 2);
        }
      }
    };
    timer = setTimeout(poll, POLL_MS / 2);
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [project.id, jobId]);

  const send = async (text: string) => {
    const content = text.trim();
    if (!content || thinking) return;
    const next = [...messages, { role: 'user' as const, content }];
    setConversation({ messages: next, pending });
    setInput('');
    setError('');
    setStarting(true);
    try {
      const job = await api.startAssistant(project.id, next, pending);
      if (mounted.current) setConversation((c) => ({ ...c, jobId: job.id }));
      else save(project.id, { ...load(project.id), jobId: job.id });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setStarting(false);
    }
  };

  useEffect(() => {
    const prompt = (location.state as { prompt?: string } | null)?.prompt;
    if (prompt && !autoSent.current && aiStatus?.enabled) {
      autoSent.current = true;
      navigate(location.pathname, { replace: true, state: null });
      send(prompt);
    }
  });

  const apply = async (changes: ProposedChange[]) => {
    setApplying(true);
    try {
      const result = await api.applyChanges(project.id, changes);
      const failed = result.results.filter((r) => !r.ok);
      const appliedIds = new Set(result.results.filter((r) => r.ok).map((r) => r.changeId));
      const note = failed.length
        ? `Applied ${result.applied} change${result.applied === 1 ? '' : 's'}. ${failed.length} failed: ${failed.map((f) => f.error).join('; ')}`
        : `Applied ${result.applied} change${result.applied === 1 ? '' : 's'}.`;
      setConversation((c) => ({
        messages: [...c.messages, { role: 'assistant', content: note }],
        pending: c.pending.filter((p) => !appliedIds.has(p.id)),
      }));
      if (failed.length) toast.fail(new Error(note));
      else toast.notify(note);
      await Promise.all([reloadProject(), refreshProjects()]);
    } catch (e) {
      toast.fail(e);
    } finally {
      setApplying(false);
    }
  };

  const discard = (ids: string[]) =>
    setConversation((c) => ({ messages: c.messages, pending: c.pending.filter((p) => !ids.includes(p.id)) }));

  const reset = () => {
    setConversation({ messages: [], pending: [] });
    setLiveSteps([]);
    setError('');
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send(input);
    }
  };

  if (aiStatus && !aiStatus.enabled) {
    return (
      <div className="panel empty">
        <Sparkles size={22} />
        <h2>The AI assistant is off</h2>
        <p>
          Connect MockHub to OpenRouter, a LiteLLM proxy or any OpenAI-compatible API, then describe the mocks you need in plain
          English.
        </p>
        {isAdmin ? (
          <Link className="btn primary" to="/admin/ai">Set up the assistant</Link>
        ) : (
          <p className="muted small">Ask an admin to turn it on under Administration → AI assistant.</p>
        )}
      </div>
    );
  }

  return (
    <div className={`assistant${pending.length ? ' has-changes' : ''}`}>
      <div className="panel chat">
        <div className="panel-head">
          <h2><Sparkles size={15} /> Assistant</h2>
          <span className="btn-row">
            {aiStatus && <span className="muted small" title={aiStatus.providerLabel}>{aiStatus.model}</span>}
            {messages.length > 0 && (
              <button className="btn ghost small" onClick={reset} title="Start a new conversation">
                <RotateCcw size={13} /> New chat
              </button>
            )}
          </span>
        </div>
        <div className="chat-log" aria-live="polite">
          {messages.length === 0 && (
            <div className="chat-intro">
              <p>
                Describe the API you need, paste an OpenAPI spec, a WSDL or a few curl commands, or ask for changes to the stubs in{' '}
                <strong>{project.name}</strong>. Nothing changes until you review and apply it.
              </p>
              <div className="suggestions">
                {SUGGESTIONS.map((s) => (
                  <button key={s} className="suggestion" onClick={() => send(s)}>{s}</button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`chat-msg ${m.role}`}>
              {m.role === 'assistant' && m.steps && m.steps.length > 0 && <div className="chat-steps">{stepsLabel(m.steps)}</div>}
              {m.role === 'assistant' ? <ChatText text={m.content} /> : <div className="chat-text user-text">{m.content}</div>}
            </div>
          ))}
          {thinking && (
            <div className="chat-msg assistant thinking" role="status">
              <span className="dots" aria-hidden="true"><i /><i /><i /></span>
              <span>
                Working on it…{liveSteps.length > 0 && <span className="chat-steps-live"> {stepsLabel(liveSteps)}</span>}
                <span className="muted small chat-leave-note">You can switch tabs. The reply will be here when you come back.</span>
              </span>
            </div>
          )}
          {error && <div className="callout danger" role="alert">{error}</div>}
          <div ref={endRef} />
        </div>
        <form className="composer" onSubmit={(e: FormEvent) => { e.preventDefault(); send(input); }}>
          <textarea
            ref={inputRef}
            aria-label="Message the assistant"
            placeholder="e.g. Add GET /orders/{id} that returns 404 for id 0"
            rows={Math.min(8, Math.max(2, input.split('\n').length))}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <button className="btn primary icon" type="submit" aria-label="Send" disabled={!input.trim() || thinking}>
            <ArrowUp size={16} />
          </button>
        </form>
      </div>

      {pending.length > 0 && (
        <div className="panel changes">
          <div className="panel-head">
            <h2>Review {pending.length} change{pending.length === 1 ? '' : 's'}</h2>
            <span className="btn-row">
              <button className="btn small" onClick={() => discard(pending.map((p) => p.id))}>Discard all</button>
              <button className="btn primary small" disabled={!canEdit || applying} onClick={() => apply(pending)}>
                <Check size={13} /> Apply all
              </button>
            </span>
          </div>
          {!canEdit && <p className="muted small changes-note">You can review these, but an editor has to apply them.</p>}
          <ul className="change-list">
            {pending.map((c) => (
              <ChangeCard key={c.id} change={c} basePath={project.basePath} canApply={canEdit && !applying}
                onApply={() => apply([c])} onDiscard={() => discard([c.id])} />
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

const ACTION_LABEL = { create: 'New', update: 'Change', delete: 'Delete' };

function ChangeCard({ change, basePath, canApply, onApply, onDiscard }: {
  change: ProposedChange;
  basePath: string;
  canApply: boolean;
  onApply: () => void;
  onDiscard: () => void;
}) {
  const [open, setOpen] = useState(change.action === 'update');
  const shown: Stub = (change.stub ?? change.before ?? {}) as Stub;
  const before = change.before ? JSON.stringify(change.before, null, 2) : '';
  const after = change.stub ? JSON.stringify(change.stub, null, 2) : '';
  return (
    <li className={`change ${change.action}`}>
      <div className="change-head">
        <span className={`change-kind ${change.action}`}>{ACTION_LABEL[change.action]}</span>
        <StubRoute stub={shown} basePath={basePath} />
      </div>
      {change.summary && <p className="change-summary">{change.summary}</p>}
      <div className="btn-row change-actions">
        <button className="btn ghost small" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? <ChevronDown size={13} /> : <ChevronRight size={13} />} {change.action === 'update' ? 'Diff' : 'JSON'}
        </button>
        <span style={{ flex: 1 }} />
        <button className="btn ghost small" onClick={onDiscard} aria-label={`Discard: ${change.summary}`}>
          {change.action === 'delete' ? <X size={13} /> : <Trash2 size={13} />} Discard
        </button>
        <button className="btn small" disabled={!canApply} onClick={onApply} aria-label={`Apply: ${change.summary}`}>
          <Check size={13} /> Apply
        </button>
      </div>
      {open && <Diff before={before} after={after} />}
    </li>
  );
}

function Diff({ before, after }: { before: string; after: string }) {
  const lines = collapse(diffLines(before, after), before && after ? 3 : 1000);
  return (
    <pre className="diff" aria-label="Changes">
      {lines.map((l, i) =>
        l.kind === 'gap' ? (
          <span key={i} className="diff-gap">⋯ {l.count} unchanged line{l.count === 1 ? '' : 's'}</span>
        ) : (
          <span key={i} className={`diff-${l.kind}`}>{l.kind === 'add' ? '+ ' : l.kind === 'remove' ? '- ' : '  '}{l.text}</span>
        ),
      )}
    </pre>
  );
}
