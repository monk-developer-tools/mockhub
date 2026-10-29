import CodeMirror from '@uiw/react-codemirror';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { json } from '@codemirror/lang-json';
import { xml } from '@codemirror/lang-xml';
import { EditorView } from '@codemirror/view';
import { tags } from '@lezer/highlight';
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

const highlight = syntaxHighlighting(HighlightStyle.define([
  { tag: [tags.propertyName, tags.tagName], color: 'var(--accent)' },
  { tag: [tags.string, tags.attributeValue], color: 'var(--ok)' },
  { tag: [tags.number, tags.bool, tags.null, tags.attributeName], color: 'var(--warn)' },
  { tag: [tags.comment, tags.meta], color: 'var(--muted)', fontStyle: 'italic' },
  { tag: [tags.invalid], color: 'var(--danger)' },
]));

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
    return [...lang, theme, highlight, EditorView.lineWrapping, EditorView.contentAttributes.of({ 'aria-label': label })];
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
