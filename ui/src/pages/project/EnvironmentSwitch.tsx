import { Stack } from '@phosphor-icons/react';
import type { Project } from '../../api/types';
import { api } from '../../api/client';
import { useToast } from '../../components/Toasts';
import { atLeast, useApp } from '../../state/AppContext';
import { announceEnvironments, useEnvironments } from '../../state/useEnvironments';

export function EnvironmentSwitch({ project }: { project: Project }) {
  const { roleInTeam } = useApp();
  const toast = useToast();
  const { environments } = useEnvironments(project.id);
  if (!environments || environments.environments.length === 0) return null;
  const canEdit = atLeast(roleInTeam(project.orgId, project.teamId), 'editor');
  const change = async (name: string) => {
    try {
      await api.switchEnvironment(project.id, name);
      announceEnvironments(project.id);
      toast.notify(`Now answering as ${name}`);
    } catch (e) {
      toast.fail(e);
    }
  };
  return (
    <label className="meta-item env-switch" title="Active environment. Callers can pick another with the X-MockHub-Environment header.">
      <Stack size={15} aria-hidden="true" />
      <span className="sr-only">Active environment</span>
      <select value={environments.active ?? ''} disabled={!canEdit} onChange={(e) => change(e.target.value)} aria-label="Active environment">
        {environments.environments.map((e) => <option key={e.name}>{e.name}</option>)}
      </select>
    </label>
  );
}
