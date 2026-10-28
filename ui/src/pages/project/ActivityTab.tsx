import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../../api/client';
import type { AuditPage } from '../../api/types';
import { AuditList } from '../../components/AuditList';
import { Skeleton } from '../../components/Skeleton';
import { useToast } from '../../components/Toasts';
import type { ProjectContext } from './ProjectPage';

export function ActivityTab() {
  const { project } = useOutletContext<ProjectContext>();
  const { fail } = useToast();
  const [page, setPage] = useState<AuditPage | null>(null);

  useEffect(() => {
    let live = true;
    api.projectAudit(project.id).then((p) => live && setPage(p)).catch(fail);
    return () => {
      live = false;
    };
  }, [project.id, project.stubCount, fail]);

  return (
    <div className="panel">
      <div className="panel-head">
        <div>
          <h2>Activity</h2>
          <p className="muted small">Who created, changed or deleted what in this project.</p>
        </div>
      </div>
      {page ? <AuditList entries={page.entries} retentionDays={page.retentionDays} /> : <Skeleton rows={4} label="Loading activity" />}
    </div>
  );
}
