import { PageHeader } from '../../components/ui/PageHeader';
import { START_GROUPS } from '../../model/starts';
import { StartGrid } from '../NewMockPage';

export function ToolsPage() {
  const tools = START_GROUPS.filter((g) => g.id === 'tools');
  return (
    <div className="page">
      <PageHeader title="Tools" description="The same tools as on the website, kept in your projects: longer history, your team, the stub editor, the AI assistant, snapshots and Git." />
      <div className="stack">
        <StartGrid groups={tools} />
      </div>
    </div>
  );
}
