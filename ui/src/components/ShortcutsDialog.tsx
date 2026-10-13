import { MOD_LABEL } from '../state/hotkeys';
import { Modal } from './Modal';

const SHORTCUTS: [string[], string][] = [
  [[MOD_LABEL, 'K'], 'Open the command palette'],
  [['?'], 'Show keyboard shortcuts'],
  [['/'], 'Search stubs'],
  [['N'], 'New REST stub'],
  [[MOD_LABEL, 'S'], 'Save the stub you are editing'],
  [['Esc'], 'Close dialogs'],
];

export function ShortcutsDialog({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="Keyboard shortcuts" onClose={onClose}>
      <table className="kv-table shortcuts">
        <tbody>
          {SHORTCUTS.map(([keys, label]) => (
            <tr key={label}>
              <td>
                {keys.map((k) => (
                  <kbd key={k}>{k}</kbd>
                ))}
              </td>
              <td>{label}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Modal>
  );
}
