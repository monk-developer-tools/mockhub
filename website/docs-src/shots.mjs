export const SHOTS = {
  assistant: 'The AI assistant has staged four customer API stubs for review',
  audit: "The audit log for today, with Li Wei's project change opened to show the exact local and UTC time",
  backup: 'The backup page with organisations and projects ticked for backup',
  callouts: 'The Callouts tab with every callback sent, its headers, body and the answer it got',
  crud: 'The Data tab listing customer records saved by a CRUD resource',
  editor: 'The MockHub stub editor with the Petstore example: routes on the left and the editor for GET /pets/{id} on the right',
  faults: 'The stub editor with Realistic latency, a failure rate and a rate limit',
  jwt: 'A token endpoint whose response signs a real RS256 JWT for the caller',
  mcp: 'A new access token with the MCP setup command for Claude Code',
  migrate: 'Importing a WireMock mappings folder into a MockHub project',
  openapi: 'Stubs generated from an OpenAPI file, grouped by tag, with one open in the editor',
  overview: 'A project overview with stats, a connect-your-app snippet and last-hour traffic',
  requests: 'The Requests tab with a traffic chart, search filters and recent calls with their status and matched stub',
  scenarios: 'A cart stub that answers only in the Started state and moves the scenario to Item added',
  snapshots: 'Comparing a project with its Release 1.4 snapshot: two stubs added since',
  soap: 'Editing a SOAP stub that matches Add requests where a is 2 and b is 3',
  teams: 'The organisation page: Organisation, Teams and Projects shown as a map, with team cards below',
  webhooks: 'A stub that calls back after answering, with a templated URL, headers and body',
};

export function shotFigure(name, caption) {
  const alt = SHOTS[name];
  if (!alt) return null;
  const base = `/assets/img/shots/${name}`;
  const esc = (t) => t.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
  return `<figure class="docs-shot">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="${base}-dark.webp 1440w, ${base}-dark@2x.webp 2880w" sizes="(max-width: 900px) 94vw, 760px">
    <img src="${base}-light.webp" srcset="${base}-light.webp 1440w, ${base}-light@2x.webp 2880w" sizes="(max-width: 900px) 94vw, 760px" alt="${esc(alt)}" width="1440" height="900" loading="lazy">
  </picture>
  <figcaption>${caption}</figcaption>
</figure>`;
}
