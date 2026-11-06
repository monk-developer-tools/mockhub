export const SHOTS = {
  'access-tokens': 'The Access tokens tab in the account dialog with an editor token for the laptop tunnel, used just now',
  assistant: 'The AI assistant has staged four customer API stubs for review',
  audit: "The audit log for today, with Li Wei's project change opened to show the exact local and UTC time",
  backup: 'The backup page with organisations and projects ticked for backup',
  callouts: 'The Callouts tab with every callback sent, its headers, body and the answer it got',
  connections: 'The Connections page with an OAuth2 client for a payments sandbox and a header API key, and how to use them in callouts and proxies',
  contract: 'The Contract tab for the Petstore project: five open findings grouped by kind, each with a count and a link to its calls',
  crud: 'The Data tab listing customer records saved by a CRUD resource',
  'custom-domains': 'The Custom domains panel in project settings with two host names and the address each one answers at',
  datasets: 'The Datasets page with a Products dataset of five records, its columns and the helpers to use it in a stub',
  echo: 'An Echo request response in the stub editor: it answers with JSON describing the request it received',
  editor: 'The MockHub stub editor with the Petstore example: routes on the left and the editor for GET /pets/{id} on the right',
  faults: 'The stub editor with Realistic latency, a failure rate and a rate limit',
  'forward-proxy': 'The Forward proxy panel with the Petstore project chosen, the CA download and ready-made curl, environment and Java settings',
  graphql: 'The GraphQL console in a GraphQL stub, with an example operation picker, query, variables and a run button',
  grpc: 'A gRPC stub for inventory.v1.StockService GetItem, with the service and method picked from the descriptor set',
  jwt: 'A token endpoint whose response signs a real RS256 JWT for the caller',
  mcp: 'A new access token with the MCP setup command for Claude Code',
  migrate: 'Importing a WireMock mappings folder into a MockHub project',
  mtls: 'The HTTPS and client certificates panel: HTTPS on, a server certificate field and trusted client CAs',
  openapi: 'Stubs generated from an OpenAPI file, grouped by tag, with one open in the editor',
  overview: 'A project overview with stats, a connect-your-app snippet and last-hour traffic',
  requests: 'The Requests tab with a traffic chart, search filters and recent calls with their status and matched stub',
  scenarios: 'A cart stub that answers only in the Started state and moves the scenario to Item added',
  snapshots: 'Comparing a project with its Release 1.4 snapshot: two stubs added since',
  soap: 'Editing a SOAP stub that matches Add requests where a is 2 and b is 3',
  teams: 'The organisation page: Organisation, Teams and Projects shown as a map, with team cards below',
  tunnels: 'The Tunnels tab in the account dialog with two tunnels online, laptop and payments-dev, and the command to start one',
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
