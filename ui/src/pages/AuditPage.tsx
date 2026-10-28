import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { AuditPage as AuditData } from '../api/types';
import { AuditList } from '../components/AuditList';
import { Skeleton } from '../components/Skeleton';
import { useToast } from '../components/Toasts';
import { useApp } from '../state/AppContext';

export function AuditPage() {
  const { orgs, projects, isAdmin } = useApp();
  const { fail } = useToast();
  const [orgId, setOrgId] = useState('');
  const [projectId, setProjectId] = useState('');
  const [data, setData] = useState<AuditData | null>(null);
  const choices = useMemo(() => projects.filter((p) => !orgId || p.orgId === orgId), [projects, orgId]);

  useEffect(() => {
    let live = true;
    setData(null);
    api.audit({ orgId, projectId }).then((d) => live && setData(d)).catch(fail);
    return () => {
      live = false;
    };
  }, [orgId, projectId, fail]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Audit log</h1>
          <p className="muted">
            {isAdmin
              ? 'Every change anyone made: projects, stubs, teams, people and settings.'
              : 'Changes in the organisations and teams you manage.'}
          </p>
        </div>
      </div>
      <div className="panel panel-body">
        {data ? (
          <AuditList
            entries={data.entries}
            retentionDays={data.retentionDays}
            showProject={!projectId}
            scope={
              <>
                {orgs.length > 1 && (
                  <select aria-label="Organisation" value={orgId} onChange={(e) => { setOrgId(e.target.value); setProjectId(''); }}>
                    <option value="">All organisations</option>
                    {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                  </select>
                )}
                <select aria-label="Project" value={projectId} onChange={(e) => setProjectId(e.target.value)}>
                  <option value="">All projects</option>
                  {choices.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </>
            }
          />
        ) : (
          <Skeleton rows={6} label="Loading audit log" />
        )}
      </div>
    </div>
  );
}
