import { ArrowCounterClockwise } from '@phosphor-icons/react';
import { EmptyArt } from '../../components/Illustrations';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Scenario, Stub } from '../../api/types';
import { Loading } from '../../components/Loading';
import { useToast } from '../../components/Toasts';
import { useApp } from '../../state/AppContext';
import { useProject } from './ProjectPage';

export function ScenariosTab() {
  const { project } = useProject();
  const { canEdit } = useApp();
  const toast = useToast();
  const [scenarios, setScenarios] = useState<Scenario[] | null>(null);

  const load = useCallback(async () => {
    try {
      const [all, stubs] = await Promise.all([api.scenarios(), api.stubs(project.id)]);
      const names = new Set(stubs.map((s: Stub) => s.scenarioName).filter(Boolean));
      setScenarios(all.filter((s) => names.has(s.name)));
    } catch (e) {
      toast.fail(e);
    }
  }, [project.id, toast.fail]);

  useEffect(() => {
    load();
  }, [load]);

  const setState = async (name: string, state: string) => {
    try {
      await api.setScenarioState(name, state);
      toast.notify(`${name} is now in “${state}”`);
      load();
    } catch (e) {
      toast.fail(e);
    }
  };

  if (!scenarios) return <Loading />;
  if (scenarios.length === 0) {
    return (
      <div className="panel empty">
        <EmptyArt kind="scenarios" />
        <h2>No scenarios in this project</h2>
        <p>
          Scenarios let the same request get different answers as state changes, like an empty cart turning into a cart with
          items. Give stubs a scenario name and states in the editor, or install the shopping cart example.
        </p>
      </div>
    );
  }
  return (
    <div className="panel">
      {scenarios.map((s) => (
        <div className="section" key={s.name}>
          <h3>
            {s.name}
            {canEdit && (
              <button className="btn small" onClick={() => setState(s.name, 'Started')} disabled={s.state === 'Started'}>
                <ArrowCounterClockwise size={14} /> Reset
              </button>
            )}
          </h3>
          <div className="btn-row">
            {s.possibleStates.map((state) => (
              <button
                key={state}
                className={`btn small${state === s.state ? ' primary' : ''}`}
                disabled={!canEdit}
                aria-pressed={state === s.state}
                onClick={() => setState(s.name, state)}
                title={state === s.state ? 'Current state' : `Move to ${state}`}
              >
                {state}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
