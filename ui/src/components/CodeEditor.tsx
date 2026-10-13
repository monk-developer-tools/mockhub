import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { xml } from '@codemirror/lang-xml';
import { EditorView } from '@codemirror/view';
import { useMemo } from 'react';

export type CodeLanguage = 'json' | 'xml' | 'text';

const theme = EditorView.theme({
  '&': { backgroundColor: 'var(--surface)', color: 'var(--ink)' },
  '.cm-content': { fontFamily: 'var(--font-code)', caretColor: 'var(--accent)' },
  '.cm-gutters': { backgroundColor: 'var(--surface-sunken)', color: 'var(--muted)', border: 'none' },
  '.cm-activeLine': { backgroundColor: 'transparent' },
  '.cm-activeLineGutter': { backgroundColor: 'var(--accent-soft)' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: 'var(--accent-soft) !important' },
});

interface Props {
  value: string;
  onChange?: (value: string) => void;
  language?: CodeLanguage;
  minHeight?: string;
  maxHeight?: string;
  readOnly?: boolean;
  label: string;
}

export function CodeEditor({ value, onChange, language = 'json', minHeight = '120px', maxHeight = '420px', readOnly, label }: Props) {
  const extensions = useMemo(() => {
    const lang = language === 'json' ? [json()] : language === 'xml' ? [xml()] : [];
    return [...lang, theme, EditorView.lineWrapping, EditorView.contentAttributes.of({ 'aria-label': label })];
  }, [language, label]);
  return (
    <div className="code-box">
      <CodeMirror
        value={value}
        onChange={onChange}
        extensions={extensions}
        editable={!readOnly}
        readOnly={readOnly}
        minHeight={minHeight}
        maxHeight={maxHeight}
        theme="none"
        basicSetup={{ foldGutter: false, highlightActiveLine: false, autocompletion: false }}
      />
    </div>
  );
}
