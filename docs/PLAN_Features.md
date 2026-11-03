# MockHub feature plan: parity with hosted mock services

Compared with Beeceptor's product documentation (https://beeceptor.com/docs/beeceptor-features/ and its sub-guides,
November 2026). This plan replaces Phases 15 and 16 of `PLAN.md` and its "Not planned" list. We build the phases
one at a time, in the order below, and tick the checklist as we go.

Every phase ships with:
- Backend, unit tests, integration tests and a system test where the feature crosses a restart or a real network
  call. Tests use only `// Given`, `// When`, `// Then`.
- UI with vitest tests, built on the design system from Phase F0.
- An AI assistant tool or prompt update (and, from Phase F4, an MCP tool) so the feature can be set up by chat.
- A docs page, a tutorial step where it helps a user, a homepage mention when it is a headline feature, and a
  README update.
- Backup, export and WireMock portability considered: a feature either survives a backup and restore or the docs
  say it does not.

## 1. Where we stand

Legend: ✅ have it · 🟡 partial · ❌ missing

| Beeceptor feature | MockHub today | Status | Phase |
|---|---|---|---|
| Create an endpoint | Projects with their own base path and URL | ✅ | — |
| Inspect HTTP requests | Requests tab, live, JSON formatting, matched and unmatched | ✅ | F3 adds search |
| Mock rules and request matching | Form and JSON editor, all WireMock matchers | ✅ | — |
| Advanced matching filters | Body JSONPath/XPath, regex, headers, query, cookies | ✅ | — |
| Execution order of rules | Priority number only, no drag to reorder | 🟡 | F2 |
| CRUD routes | CRUD resources with `_sort`, `_order`, `_limit`, `_offset` | 🟡 | F7 |
| HTTP callout rule | Webhooks, forward, wait for callout, callout log | ✅ | F9 adds retries and OAuth |
| Large and binary responses | Response files with content type | ✅ | — |
| Template engine: request data, control flow, arrays, strings, maths, dates | Handlebars helpers and the Insert dynamic value picker | ✅ | — |
| Template engine: state (counters, stores, lists) | State extension in memory | 🟡 | F7 |
| Template engine: JWT, `len`, `object`, `json`, validation helpers | Missing | ❌ | F6 |
| Fake data and locales | `fake` helper, server locale, data generator | ✅ | — |
| SOAP from WSDL | WSDL import, SOAP faults | ✅ | F10 adds SOAPAction auto-detect |
| REST from OpenAPI with generated data | OpenAPI import with realistic data | ✅ | — |
| GraphQL from SDL | Missing | ❌ | F10 |
| gRPC from proto | Missing | ❌ | F10 |
| Mutual TLS | Only through raw `WIREMOCK_OPTIONS` | 🟡 | F10 |
| Default response | Manual catch-all stub | 🟡 | F2 |
| Latency profiles | Per-stub fixed, random, log-normal and trickle delays; no project default or load-dependent delay | 🟡 | F2 |
| Rate limits | Missing | ❌ | F2 |
| CORS origin allow list | Server-wide on/off switch | 🟡 | F2 |
| Proxying and intercepting | Proxy and record per stub or project | ✅ | — |
| Forward proxy (browser/OS proxy) | Missing | ❌ | F11 |
| Local tunnelling | Missing | ❌ | F12 |
| White-labelled domains | Missing | ❌ | F11 |
| Request history: search, facets, 10-day retention | In-memory journal, text filter, no retention | 🟡 | F3 |
| Snapshots: create, restore, pull to another endpoint | Server backup and restore only | 🟡 | F5 |
| Stateful data management UI | Data tab for CRUD records; counters and stores not visible | 🟡 | F7 |
| Datasets (CSV/JSON shared across projects) | Missing | ❌ | F8 |
| Sharing and teams | Organisations, teams, roles, SSO | ✅ | — |
| API connections (stored OAuth for outbound calls) | Missing | ❌ | F9 |
| Mock server security (required header) | Project API key header, 401 | ✅ | F2 adds IP allow list |
| AI rules generator | AI assistant with staged diffs | ✅ | — |
| Agentic mode (MCP) | Missing | ❌ | F4 |
| Contract drift detection | Missing | ❌ | F13 |
| Live traffic (RPS) | Activity bars only | 🟡 | F3 |
| Test data editor for OpenAPI mocks | Edit generated stubs by hand, AI body generator | 🟡 | F8 |
| HTTP echo server | Missing as a built-in | ❌ | F11 |
| "Mock these APIs" button | Missing | ❌ | F11 |
| Error code reference | Scattered across docs | 🟡 | F1 |
| Account management | Users, SSO, account dialog | ✅ | F4 adds tokens |
| Product documentation as a card-based site | One long docs page | 🟡 | F1 |

## 2. Phases

### Phase F0 — A professional UI

Goal: the product looks like a mature commercial tool, consistently, on every page, in both themes and at phone width.

Design system:
- Tokens on `:root` for colour (neutral scale, one brand accent, success/warning/danger/info), radius, shadow,
  spacing (4px scale), and type (Inter for UI, JetBrains Mono for code, a fixed type scale with 13/14/16/20/24/30).
  Replace hard-coded values in `app.css` with tokens and split the 5,000-line file into `tokens.css`, `base.css`,
  `components/*.css` and `pages/*.css`.
- Component kit in `ui/src/components/ui/`: `Button`, `IconButton`, `Input`, `Select`, `Textarea`, `Switch`,
  `Checkbox`, `Segmented`, `Tabs`, `Badge`, `StatusPill`, `Card`, `PageHeader`, `Section`, `DataTable` (sticky header,
  sortable columns, row selection, empty and loading states, pagination), `Drawer`, `Dialog`, `Tooltip`, `Menu`,
  `EmptyState`, `Callout`, `Kbd`, `CodeBlock`. Each with a test.
- Shell: grouped sidebar (Workspace, Projects, Administration) with collapsed mode, a top bar with breadcrumbs,
  project switcher, global search (existing ⌘K), help menu linking to the matching docs page, and the account menu.
- Page template: `PageHeader` (title, description, primary action, secondary actions), then content in cards. Every
  list page gets the same toolbar: search, filters as chips, view options, bulk actions.
- Project page: tabs become a secondary sidebar or an overflow-aware tab bar with icons and counts (Stubs 24,
  Requests 1.2k, Callouts 3 failed).
- Stub list: dense table with method badges, path, name, status, hits and last call, priority, on/off switch, folder
  grouping and drag to reorder (Phase F2 adds the reorder backend).
- Stub editor: two-column layout on wide screens (request on the left, response on the right), sticky action bar,
  section navigation, and a live preview panel.
- Empty states with one clear action and a docs link; skeleton loading everywhere; toasts for every save.
- Accessibility: focus rings, keyboard reach for every control, colour contrast AA in both themes, reduced motion.
- Visual regression: a script that captures key pages in light and dark at desktop and phone width, kept in the repo
  for review on each phase.

Checklist:
- [x] Tokens (type scale, spacing, radii, top bar and rail sizes) and the stylesheet split into `tokens.css`,
      `base.css`, `controls.css`, `menu.css`, `shell.css`, `tables.css`; feature styles still live in `app.css`
- [x] Component kit, first part: `PageHeader`, `Breadcrumbs`, `Menu` (keyboard support), `EmptyState`, `useDismiss`
- [x] New shell: top bar with breadcrumbs, help menu (docs for the current page, shortcuts, search, issues), theme
      switch; sidebar sections; sidebar collapses to an icon rail (`[` or the footer button), remembered per browser
- [x] Every top-level page uses `PageHeader`; the project header is compact (URL pill, team, two-line description)
- [x] Consistent table headers and row hover; panel toolbars wrap on phones; the dashboard hides the URL column on phones
- [x] Empty states with a docs link for stubs, requests and callouts
- [x] Accessibility: menu keyboard navigation, focus returns to the trigger, collapsed links keep their names
- [x] `npm run screens` captures key pages in light and dark at desktop and phone width; `ui/scripts/demo.mjs`
      seeds a realistic "Acme" demo through the API for screenshots
- [x] Homepage screenshots refreshed (dashboard, overview, editor, requests, teams, people, audit, backup, SOAP)
- [x] Audit log lists only the project fields that really changed (found while staging the demo)
- [ ] Remaining kit components (`Button`, `Input`, `DataTable`, `Drawer`, `Dialog`) as React components; today these
      are shared CSS classes, which already give one look
- [ ] Split the feature styles in `app.css` per page
- [ ] Stub list as a dense table and a two-column editor. Decision for now: keep the list-and-editor layout, which
      already shows the request and response side by side with the list; revisit with user feedback
- [ ] Refresh the assistant and migration homepage shots (need a model provider and a WireMock folder) and the
      tutorial images

### Phase F1 — Product documentation like a product

Goal: `website/docs/` becomes a multi-page documentation site in the style of Beeceptor's product docs.

- Landing page "Product documentation": title, short intro, search box, and a grid of feature cards. Each card has an
  icon, title, one-line description or "N guides", and an arrow. Cards are grouped under headings: Get started, Mock
  APIs, Dynamic responses, Protocols, Runtime behaviour, Traffic and debugging, Data, Collaboration and security, AI
  and agents, Operations.
- One page per feature (`/docs/<feature>/`), with a left sidebar mirroring the groups, breadcrumbs, an "On this page"
  table of contents, previous and next links, "Edit on GitHub", copyable code blocks and screenshots.
- Group pages ("Template engine", "Protocols", "Runtime behaviour") list their guides as cards, like Beeceptor's.
- Template helper reference: one page per helper family generated from `ui/src/model/dynamicValues.ts`, so the
  picker and the docs never drift.
- Error code reference: every MockHub error status and header (`X-MockHub-Callout-Error`, simulated errors, 401 API
  key, 429 rate limit, 502 callout, unmatched 404) with the cause and the fix.
- Client-side search over a JSON index built at publish time (no external service).
- Static generation stays simple: a small Node script renders Markdown pages in `website/docs-src/` into HTML with one
  shared layout, run by `deploy-site.sh`. No framework.
- The UI's help menu and each empty state link to the matching page.

Checklist:
- [x] Docs generator (`website/scripts/build-docs.mjs`), shared layout, sidebar, breadcrumbs, previous/next, table of
  contents. Pages are HTML fragments in `website/docs-src/pages/` rather than Markdown, so the existing docs moved over
  unchanged and no Markdown dependency is needed. The page list and groups live in `website/docs-src/docs.mjs`.
- [x] Landing page with grouped feature cards and search (`/` focuses it, arrow keys pick a result)
- [x] Existing docs split into 32 feature pages, no content lost; old `/docs/#anchor` links redirect to the new page
- [x] Helper reference generated from the picker data
- [x] Error code reference
- [x] Links from the UI, the homepage and the tutorials to the new pages
- [x] Deploy script builds the docs; the build fails on a broken docs link or a duplicate heading id
- [ ] Group pages listing their guides as cards (the landing page covers this for now)
- [ ] Screenshots inside feature pages

### Phase F1b — Website redesign

Goal: the marketing site at mockhub.devops-monk.com looks like a product site such as beeceptor.com, and shares one
header, footer and visual system with the new docs.

- Header with a Product menu (feature groups with icons, linking to docs pages), Docs, Tutorials, GitHub and a
  primary "Try the demo" button.
- Hero with a clear one-line promise, a live mock URL example (`curl` and its answer), and two calls to action.
- Logo strip of what MockHub works with (OpenAPI, WSDL, WireMock, Postman, Bruno, Docker, Kubernetes, Entra ID).
- Feature sections as icon cards grouped like the docs, each linking to its docs page.
- "How it works" in three steps, use-case cards (test environments, contract-first, demos, load tests), and a
  comparison with plain WireMock.
- Footer with columns: Product, Docs, Resources, Project.
- Same tokens, light and dark, phone layout without horizontal scroll, screenshots refreshed.

Checklist:
- [ ] Shared header and footer partials used by the homepage, tutorials and docs
- [ ] New homepage sections
- [ ] Tutorials page moved to the shared layout
- [ ] Phone, dark mode and accessibility check

### Phase F2 — Runtime behaviour

Goal: project-level controls for how mocks behave, without writing stubs.

- **Default response**: project setting for unmatched requests (status, headers, body, templating allowed). Served by
  a lowest-priority stub MockHub manages and hides from the list, so WireMock export keeps working.
- **Rate limits**: per project and optionally per stub; requests per second, minute or hour; answers 429 with
  `Retry-After` and `X-RateLimit-*` headers. Token bucket in memory per project and stub.
- **Latency profile**: project default delay (fixed, random range, log-normal) applied to stubs without their own
  delay, plus a load-dependent profile that adds delay as requests per second grow.
- **CORS**: per project allow list of origins, methods and headers, credentials on/off, and "simulate CORS failure"
  which omits the headers on purpose.
- **IP allow list**: per project CIDR list next to the API key setting.
- **Execution order**: drag to reorder stubs in the list; MockHub rewrites priorities in steps of 10 within the
  project; a "Show matching order" view explains which stub wins for a sample request.
- Assistant and MCP: tools to read and change project runtime settings.

Checklist:
- [ ] Default response
- [ ] Rate limits with headers
- [ ] Project latency profile and load-dependent delay
- [ ] CORS allow list and failure simulation
- [ ] IP allow list
- [ ] Drag to reorder and matching order view
- [ ] Assistant tools, tests, docs, tutorial

### Phase F3 — Request history and live traffic

Goal: find any request from the last days and watch traffic during load tests.

- History store on disk: append-only NDJSON segments per project per hour under `/data/mockhub/history/`, retention
  `MOCKHUB_HISTORY_DAYS` (default 7), size cap per project, bodies capped at 64 KB. Survives restarts and backups can
  skip it.
- Search: path, method, status range, matched/unmatched, stub, header name/value, body text, time range. Facets with
  counts for method, status class and stub.
- Export a selection as HAR or cURL; replay into the playground.
- Live traffic: per-second counters kept for 24 hours; chart with 5m/15m/1h/6h/24h windows, drag to select a range,
  summary of total, average RPS, peak RPS and duration. Daily totals for 30 days.
- Callout log moves onto the same store so callouts appear next to the request that triggered them.
- Assistant and MCP: `search_requests` with the same filters.

Checklist:
- [ ] History store, retention and size cap
- [ ] Search API and facets
- [ ] HAR export
- [ ] Live traffic counters and chart
- [ ] Callouts stored with their trigger
- [ ] Tests (including restart), docs, tutorial

### Phase F4 — Personal access tokens and MCP (agentic mode)

Goal: Claude, Cursor, Codex and CI can create and manage mocks directly.

- Personal access tokens: created in the account dialog, shown once, hashed at rest, with name, expiry, role ceiling
  and project scope; revocable; last used time shown. Accepted as `Authorization: Bearer` on the MockHub API.
- MCP server at `/__admin/mockhub/mcp` (Streamable HTTP), authenticated with a token. Tools: project info, list,
  inspect, create, update, delete and reorder stubs; CRUD resources; chaos and latency; callouts; request history
  search and clear; state data; project runtime settings; template helper reference.
- Each token chooses "apply directly" or "stage for review"; staged changes appear in the assistant's review panel.
- Every MCP change is audited with the token name.
- Docs page with setup snippets for Claude Code, Claude Desktop, Cursor and Codex, and example prompts.

Checklist:
- [ ] Tokens: model, store, API, UI
- [ ] MCP transport and tools
- [ ] Staged or direct changes, audit
- [ ] Tests with a real MCP client in the system test
- [ ] Docs and tutorial

### Phase F5 — Snapshots

Goal: save, compare and restore a project's configuration, and promote it between projects.

- A snapshot holds stubs, response files, project settings (runtime behaviour, API key name but not its value) and
  CRUD resource definitions. It does not hold records, history or secrets; the docs say so.
- Create with a name and purpose; list with author and time; compare two snapshots or a snapshot with now as a stub
  diff; restore (editor role, confirmation, a safety snapshot first); delete.
- Pull a snapshot into another project (dev to qa), rewriting the base path.
- Download and upload a snapshot as a file, which Beeceptor cannot do.
- Assistant and MCP: create and restore snapshots, so "try this change and roll back" works by chat.

Checklist:
- [ ] Snapshot store and API
- [ ] Diff and restore with a safety snapshot
- [ ] Pull between projects
- [ ] UI, tests, docs

### Phase F6 — Template engine completion

Goal: close the helper gaps so every Beeceptor template has a MockHub equivalent.

- JWT helpers: `jwt` to sign (HS256/RS256, claims from the request), `jwtDecode` to read claims from a header, and a
  JWKS stub template for OAuth test servers.
- Miscellaneous: `len`, `object` (build JSON), `json` (stringify), `toNumber`, `default`.
- Validation helpers: `isEmail`, `isUuid`, `isNumber`, `matches`, `required` that make a stub answer 400 with a
  message.
- Weighted responses: one stub answers with one of several responses by weight (`mockhub-weighted` transformer), with
  form editor support.
- Picker and generated docs gain these groups; the verification test renders every picker entry against a real
  server.

Checklist:
- [ ] JWT helpers and JWKS template
- [ ] Miscellaneous and validation helpers
- [ ] Weighted responses
- [ ] Picker, docs, tests

### Phase F7 — State and CRUD parity

Goal: the Data tab manages all state, and CRUD resources accept Beeceptor's query syntax.

- Data tab shows counters, key-value stores and lists next to CRUD records; edit, reset and delete each.
- State persisted to disk like CRUD records, included in backups.
- CRUD filters: `=`, `!=`, `>=`, `<=`, `<`, `>` via `field[gte]=` style and plain `field=value`, repeated values as
  OR, dot notation for nested fields, booleans.
- Sort `sort=-price,name`; pagination with `limit/offset` or `page/size`; the old `_sort` style keeps working.
- Configurable id field and 10-character random ids; 400 `invalid_json_body`; 404 with a clear body.
- Record limit per resource with oldest-first purge, configurable.

Checklist:
- [ ] State viewer and persistence
- [ ] CRUD query syntax and pagination
- [ ] Limits and errors
- [ ] Tests, docs

### Phase F8 — Datasets

Goal: upload test data once and use it from any project in the organisation.

- Organisation datasets from CSV, JSON or NDJSON: name, key, identity fields, lookup indexes, typed CSV columns.
- Template helpers `dataset-one` and `dataset` with up to three equality filters.
- Upload modes: update and add, or add only. Limits: size, record count, record size.
- Dataset browser with search and a preview of a helper's result.
- Test data editor for OpenAPI mocks: open a generated stub's body as a table and edit rows, or bind it to a dataset.

Checklist:
- [ ] Dataset store, upload and indexes
- [ ] Template helpers
- [ ] Browser and test data editor
- [ ] Backup, tests, docs

### Phase F9 — API connections and reliable callouts

Goal: callouts and proxies reach authenticated partners.

- Organisation API connections: OAuth 2.0 client credentials (token URL, client id, secret, scope, header prefix),
  secrets encrypted at rest, a "test token" button, token cache with expiry.
- Callouts and proxy stubs choose a connection; the token is added as the Authorization header.
- Callout retries: count, backoff and which statuses to retry; each attempt in the callout log.
- Callout log stored on disk with the history store from F3.

Checklist:
- [ ] Connections with encrypted secrets
- [ ] Token injection in callouts and proxies
- [ ] Retries
- [ ] Tests, docs

### Phase F10 — More protocols

Goal: mock GraphQL, gRPC and SOAP services from their schemas, and serve mutual TLS.

- GraphQL: import SDL or introspection JSON; match by operation name and variables; generated responses with fake
  data that follow the schema; GraphiQL-style Try it. Candidate extension `wiremock-graphql-extension`; verify on 3.13.
- gRPC: import `.proto`; unary methods first; JSON-written responses converted to protobuf; behaviour overrides.
  Candidate `wiremock-grpc-extension`; verify on Jetty 12, else a MockHub implementation.
- SOAP: detect operations from SOAPAction or the body's first element automatically when no WSDL is given.
- Mutual TLS: Server page settings for the HTTPS port, server certificate and trusted client CAs; per project "require
  client certificate".

Checklist:
- [ ] GraphQL
- [ ] gRPC
- [ ] SOAP auto-detect
- [ ] Mutual TLS
- [ ] Tests, docs, examples

### Phase F11 — Routing extras

Goal: the smaller routing and sharing features.

- Echo server: a built-in `/echo` project template that returns the request as JSON (method, path, query, headers,
  body).
- Custom domains: map a host name to a project, so `payments.mocks.example.com/` answers without the base path. Uses
  the Host header; TLS stays with the reverse proxy; docs give nginx and Caddy examples.
- "Mock these APIs" button: a public link that imports an OpenAPI URL into a new project on this MockHub, with an
  embeddable badge for API docs.
- Forward proxy: an opt-in HTTP proxy port with HTTPS interception through a MockHub CA certificate, recording into a
  chosen project. Setup guides for browsers, curl, macOS and Android.

Checklist:
- [ ] Echo template
- [ ] Custom domains
- [ ] Mock these APIs button
- [ ] Forward proxy with interception
- [ ] Tests, docs

### Phase F12 — Local tunnel

Goal: a stub can proxy to a service on a developer's laptop, so webhooks reach localhost.

- `mockhub-tunnel` CLI (a single Java or Node binary) opens a WebSocket to MockHub with a personal access token and
  registers a tunnel name.
- Proxy stubs and callouts can target `tunnel://<name>/path`; MockHub sends the request through the socket and
  streams the answer back.
- Tunnel list in the account dialog with status and traffic.
- Limits on body size and concurrent requests; tunnels close when the token is revoked.

Checklist:
- [ ] Tunnel protocol and server side
- [ ] CLI
- [ ] Proxy and callout targets
- [ ] Tests, docs

### Phase F13 — Contract drift detection

Goal: show where real or mocked traffic disagrees with an OpenAPI contract.

- Attach an OpenAPI spec to a project (from import or upload).
- A background check compares traffic from the history store with the spec: undocumented paths, methods, parameters
  and headers; request and response schema mismatches; status codes and content types not in the spec.
- Drift page: grouped findings with counts, first and last seen, example request; mark as expected.
- "Generate patch" asks the AI assistant for a minimal OpenAPI patch shown as a diff; "Apply" updates the stored spec.
- Optional e-mail or webhook notification for new drift.

Checklist:
- [ ] Spec storage and validator
- [ ] Drift detection job and findings API
- [ ] Drift page and AI patch
- [ ] Notifications
- [ ] Tests, docs

## 3. Order and size

| Phase | Size | Why here |
|---|---|---|
| F0 UI | L | Every later phase builds on the component kit |
| F1 Docs | M | Every later phase adds a docs page in the new format |
| F1b Website | M | First impression; shares the docs layout |
| F2 Runtime behaviour | M | Most-asked gaps, small each |
| F3 History and live traffic | L | Needed by F9 and F13 |
| F4 Tokens and MCP | M | High value, reuses the assistant's tools |
| F5 Snapshots | M | Safe rollback before bigger changes |
| F6 Template engine | S | Picker parity |
| F7 State and CRUD | S | Small, user-visible |
| F8 Datasets | M | Builds on F7 |
| F9 API connections | S | Builds on F3 |
| F10 Protocols | L | Depends on extension compatibility |
| F11 Routing extras | M | Independent |
| F12 Local tunnel | M | Needs F4 tokens |
| F13 Contract drift | L | Needs F3 history |
