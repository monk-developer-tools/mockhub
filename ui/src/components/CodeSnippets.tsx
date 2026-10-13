import { useState } from 'react';
import type { SampleRequest } from '../model/sampleRequest';
import { SNIPPET_LANGUAGES, snippet, type SnippetLanguage } from '../model/snippets';
import { CopyButton } from './CopyButton';

const KEY = 'mockhub.snippet';

function readLanguage(): SnippetLanguage {
  try {
    const v = localStorage.getItem(KEY);
    return SNIPPET_LANGUAGES.some((l) => l.id === v) ? (v as SnippetLanguage) : 'curl';
  } catch {
    return 'curl';
  }
}

export function CodeSnippets({ request, baseUrl }: { request: SampleRequest; baseUrl: string }) {
  const [language, setLanguage] = useState<SnippetLanguage>(readLanguage);
  const code = snippet(language, request, baseUrl);
  const choose = (l: SnippetLanguage) => {
    setLanguage(l);
    try {
      localStorage.setItem(KEY, l);
    } catch {
      return;
    }
  };
  return (
    <div className="snippets">
      <div className="snippets-head">
        <div className="segmented" role="tablist" aria-label="Snippet language">
          {SNIPPET_LANGUAGES.map((l) => (
            <button key={l.id} role="tab" aria-selected={l.id === language} aria-pressed={l.id === language} onClick={() => choose(l.id)}>
              {l.label}
            </button>
          ))}
        </div>
        <CopyButton text={code} label={`Copy ${SNIPPET_LANGUAGES.find((l) => l.id === language)?.label} snippet`} />
      </div>
      <pre className="block snippet-code">{code}</pre>
    </div>
  );
}
