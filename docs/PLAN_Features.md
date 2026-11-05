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
| Inspect HTTP requests | Requests tab, live, JSON formatting, matched and unmatched, search | ✅ | — |
| Mock rules and request matching | Form and JSON editor, all WireMock matchers | ✅ | — |
| Advanced matching filters | Body JSONPath/XPath, regex, headers, query, cookies | ✅ | — |
| Execution order of rules | Matching order view, drag or arrows to reorder | ✅ | — |
| CRUD routes | CRUD resources with `_sort`, `_order`, `_limit`, `_offset` | 🟡 | F7 |
| HTTP callout rule | Webhooks, forward, wait for callout, callout log, retries, OAuth connections | ✅ | — |
| Large and binary responses | Response files with content type | ✅ | — |
| Template engine: request data, control flow, arrays, strings, maths, dates | Handlebars helpers and the Insert dynamic value picker | ✅ | — |
| Template engine: state (counters, stores, lists) | State extension, saved per project | ✅ | — |
| Template engine: JWT, `len`, `object`, `json`, validation helpers | JWT sign/decode/JWKS, value and validation helpers, weighted responses | ✅ | — |
| Fake data and locales | `fake` helper, server locale, data generator | ✅ | — |
| SOAP from WSDL | WSDL import, SOAP faults, operation detection from sample requests | ✅ | — |
| REST from OpenAPI with generated data | OpenAPI import with realistic data | ✅ | — |
| GraphQL from SDL | Schema import, generated answers, operation stubs, console | ✅ | — |
| gRPC from proto | Descriptor-set upload, JSON stubs per method, gRPC status codes, reflection | ✅ | — |
| Mutual TLS | HTTPS port, own certificate, trusted client CAs, per-project requirement | ✅ | — |
| Default response | Project setting, templated, hidden managed stub | ✅ | — |
| Latency profiles | Per-stub delays plus a project default and load-dependent delay | ✅ | — |
| Rate limits | Per project and per stub, 429 with Retry-After and X-RateLimit headers | ✅ | — |
| CORS origin allow list | Per project allow list, credentials, simulated failure | ✅ | — |
| Proxying and intercepting | Proxy and record per stub or project | ✅ | — |
| Forward proxy (browser/OS proxy) | `MOCKHUB_PROXY` with HTTPS interception through a MockHub CA | ✅ | F11 |
| Local tunnelling | `mockhub.jar tunnel` and `tunnel://name` targets for proxy stubs and callouts | ✅ | F12 |
| White-labelled domains | Custom domains per project, matched on the Host header | ✅ | F11 |
| Request history: search, facets, 10-day retention | On-disk history, search, facets, HAR, configurable retention (default 7 days) | ✅ | — |
| Snapshots: create, restore, pull to another endpoint | Snapshots with diff, restore, pull between projects, file download and upload | ✅ | — |
| Stateful data management UI | Data tab for CRUD records and recorded state contexts | ✅ | — |
| Datasets (CSV/JSON shared across projects) | Organisation datasets, helpers, browser, table editor | ✅ | — |
| Sharing and teams | Organisations, teams, roles, SSO | ✅ | — |
| API connections (stored OAuth for outbound calls) | Organisation connections for callouts and proxies, encrypted | ✅ | — |
| Mock server security (required header) | Project API key header (401) and IP allow list (403) | ✅ | — |
| AI rules generator | AI assistant with staged diffs | ✅ | — |
| Agentic mode (MCP) | MCP server with 22 tools, direct or review tokens, audited | ✅ | — |
| Contract drift detection | Missing | ❌ | F13 |
| Live traffic (RPS) | Traffic chart 5m–24h and 30 days, average and peak RPS, drag to select | ✅ | — |
| Test data editor for OpenAPI mocks | Edit generated stubs by hand, AI body generator | 🟡 | F8 |
| HTTP echo server | Missing as a built-in | ❌ | F11 |
| "Mock these APIs" button | Missing | ❌ | F11 |
| Error code reference | Scattered across docs | 🟡 | F1 |
| Account management | Users, SSO, account dialog, personal access tokens | ✅ | — |
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
- [x] Docs generator (`website/scripts/build-site.mjs`), shared layout, sidebar, breadcrumbs, previous/next, table of
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
- [x] Shared header (Product menu built from the docs groups) and footer in `website/docs-src/layout.mjs`; the build
  writes them into the homepage and tutorials between `<!-- site:name -->` markers, and the build is idempotent
- [x] New homepage: two-column hero with a "Call a live mock" card that calls the public demo and shows the status,
  time and matched stub; works-with strip; numbers; feature cards per docs group; use-case cards
- [x] Tutorials page moved to the shared layout
- [x] Phone, dark mode and keyboard check (menu opens on click or hover, closes on Escape or focus leaving)
- [ ] Logos instead of names in the works-with strip (needs permission-safe artwork)

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
- [x] Default response
- [x] Rate limits with headers
- [x] Project latency profile and load-dependent delay
- [x] CORS allow list and failure simulation
- [x] IP allow list
- [x] Drag to reorder and matching order view
- [x] Assistant tools, tests, docs, tutorial

Notes from building it:
- Rate limits use fixed windows (per second, minute or hour) rather than a token bucket: the counts match the
  `X-RateLimit-*` headers exactly. Counters are in memory, per instance.
- CORS is now handled by MockHub instead of WireMock's `--enable-stub-cors`, so it can differ per project.
  WireMock's own unmatched 404 can't carry CORS headers; a default response fixes that for browser apps.
- The matching order view lists the real order WireMock tries stubs in, plus what answers when nothing matches.
  Explaining the winner for one sample request is left to the request log's closest-stub view.
- MCP tools for runtime settings come with Phase F4; the in-app assistant has `get_runtime` and `set_runtime`.

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
- [x] History store, retention and size cap
- [x] Search API and facets
- [x] HAR export
- [x] Live traffic counters and chart
- [x] Callouts stored with their trigger
- [x] Tests (including restart), docs, tutorial

Notes: per-second counters are kept for the last hour and per-minute counters (with the peak second) for 24 hours,
which is what the windows need. cURL comes per request from the detail panel rather than for a selection. History is
not in backups. The assistant's `list_requests` became `search_requests`; MCP gets it in F4.

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
- [x] Tokens: model, store, API, UI
- [x] MCP transport and tools
- [x] Staged or direct changes, audit
- [x] Tests with a real MCP client in the system test
- [x] Docs and tutorial

Notes: the MCP endpoint answers every POST with plain JSON and keeps no session, so it needs no sticky load
balancing; GET returns 405 (no server-initiated stream). Tools call the MockHub API in-process, so authorisation,
validation and audit are shared with the UI. Token limits are checked before the admin shortcut, so a scoped admin
token stays scoped. Review tokens stage stub and runtime changes only; other writes are refused. Deleting a project
narrows tokens scoped to it and deletes a token left with none. Staged changes show in the Assistant tab even when
the in-app assistant is off.

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
- [x] Snapshot store and API
- [x] Diff and restore with a safety snapshot
- [x] Pull between projects
- [x] UI, tests, docs

Notes: snapshots live in `data/mockhub/snapshots/{projectId}` and are deleted with the project; the backup file does
not include them (download one to keep a version). Stubs are stored project-relative in matching order and restored
in reverse so equal-priority order is kept; managed default-response stubs are rebuilt from the runtime settings.
Restoring or pulling never changes the API key or stored records. A pull gives stubs ids derived from the target
project and the source id, so pulling again shows only real changes. The last 10 safety snapshots are kept and up to
50 others per project. The pull source needs view access, and a scoped token can't pull from outside its scope.
Review tokens and the assistant stage snapshot and restore changes; a snapshot in a batch is applied first.
Restore swaps stubs one by one, so a caller can briefly see a partial set during a large restore.

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
- [x] JWT helpers and JWKS template
- [x] Miscellaneous and validation helpers
- [x] Weighted responses
- [x] Picker, docs, tests

Notes: the picker catalogue moved to `ui/src/model/dynamicValues.json` (with a `$R` placeholder for the request
variable) so the UI, the generated helper docs and `PickerCatalogueIntegrationTest` share it; the test renders every
entry against a real server, in a response or, for callout-only entries, a callout. Validation helpers throw a marked
template error that a response transformer turns into 400 (or `status=`) with `{"error": message}`. The RS256 key is
created on first use in `data/mockhub/jwt-key.json`; it is not in backups or snapshots. `jwtDecode` does not check
signatures. Weighted responses work in either transformer order; an entry without a body keeps the stub's body and
the answer carries `X-MockHub-Weighted-Choice`. `matches` is WireMock's own helper, so `check` wraps it for validation.

### Phase F6b — Product website and no-login tools

Goal: mockhub.devops-monk.com reads like a professional product site such as beeceptor.com. Every feature has its own
page reachable straight from the menu, and a visitor can use real MockHub features without an account. Studied pages
(November 2026): beeceptor.com home, /soap-mock-server/, /openapi-mock-server/, /crud-api/, /csv-to-api/,
/webhook-integration/, /service-virtualization/, /performance-testing-service-virtualization/, /ai/,
/resources/http-echo/, /resources/test-data-generator/, /resources/2fa-code-generator/, /http-debugger/,
/virtual-sandbox/explore/.

We list only what MockHub really does.

#### Navigation

Header: logo, then four menus and two buttons.
- **Service Virtualization** (who it is for):
  - Service virtualization overview (`/service-virtualization/`)
  - Performance and chaos testing (`/performance-testing/`): delays, faults, weighted responses, rate limits
  - Test environments per team (`/test-environments/`): projects, teams, roles, snapshots, pull dev into qa
  - Frontend before backend (`/contract-first/`): OpenAPI import, fake data, CRUD
  - Partner APIs and webhooks (`/webhook-mocking/`): callouts, wait-for-partner
  - AI and MCP (`/ai/`): assistant, coding agents over MCP
- **Mock Servers** (what you mock):
  - REST / JSON mock API (`/mock-api/`)
  - OpenAPI mock server (`/openapi-mock-server/`)
  - SOAP mock server from WSDL (`/soap-mock-server/`)
  - CRUD API that stores data (`/crud-api/`)
  - Stateful mocks (`/stateful-mocks/`): scenarios, state helpers
  - JWT and OAuth mock server (`/oauth-mock-server/`)
  - Move from WireMock (`/wiremock-alternative/`)
  - Ready-made examples (`/examples/`): the bundled example projects, each with a live URL
- **Tools** (free, no sign-up), each marked with a "No login" badge in the menu:
  - Instant mock server (`/tools/instant-mock/`)
  - HTTP echo (`/tools/http-echo/`)
  - Webhook catcher (`/tools/webhook-catcher/`)
  - Template playground (`/tools/template-playground/`)
  - Fake data generator (`/tools/fake-data/`)
  - JWT encoder and decoder (`/tools/jwt/`)
  - cURL to stub (`/tools/curl-to-stub/`)
- **Resources**: Docs, Tutorials, Changelog, Compare, GitHub.
- Buttons: "Get started" (ghost, to quick start) and "Try it free, no login" (primary, to the instant mock).

Mega menus show an icon, a title and a one-line description per entry, plus a side panel with one featured link,
like Beeceptor's. They open on click or hover, close on Escape or when focus leaves, and on phones become accordions.

Footer, in five columns:
- Mock servers
- Service virtualization
- Free tools
- Resources: Docs, Tutorials, Helper reference, API reference, Changelog
- Compare and project: vs WireMock, vs Mockoon, vs Postman mock servers, GitHub, licence, security

Bottom bar: demo status (live check of the demo's health endpoint), Privacy, Terms, ©.

#### Page template (one data file, many pages)

Pages come from `website/site-src/pages.mjs`, one object per page, rendered by `build-site.mjs` into `/<slug>/index.html`
with the shared header and footer. A page lists these sections in this order and leaves out any it does not need:
1. **Hero:**
   - An eyebrow (the menu group).
   - An H1 that names the outcome.
   - A one-line subtitle.
   - Two CTAs: "Try it, no login", or the page's own tool, and "Read the docs".
   - On the right, a live widget or a product screenshot in a browser frame.
2. **Proof strip.** Works with: OpenAPI, WSDL, WireMock, Postman, Bruno, Docker, Kubernetes, Entra ID.
3. **How it works.** Three numbered steps, each with a small screenshot.
4. **The problem.** Three or four pain cards ("Why contracts alone aren't enough").
5. **Benefit grid.** Six to nine cards, each with an icon, a title, two lines of text and a link to the docs.
6. **Code example.** A request and its answer, with tabs (cURL, JavaScript, Python, Java), copy button, and a "Run it"
   button that calls the live demo.
7. **FAQ.** Four to eight questions, as `<details>` elements with FAQPage structured data.
8. **Related.** Three cards linking to sibling pages.
9. **Closing band.** One sentence that defines the term, for search, plus the two CTAs.

#### Professional UI standards for the site

The site uses one design system, defined as tokens in `website/assets/site.css`.

Typography:
- One sans family for text and one mono family for code, both self-hosted.
- Type scale 14/16/18/22/28/36/48/60, line length 60–75 characters.
- Headings tight (1.1), body 1.6.

Colour:
- Light and dark themes defined as tokens.
- One brand accent, used only for actions and highlights.
- Neutral greys for surfaces.
- Status colours used only for status.
- WCAG AA contrast in both themes.

Layout:
- 12-column grid, max width 1200 px.
- Section rhythm on an 8 px scale.
- Generous whitespace.
- Phone layout at 360 px with no horizontal scroll and a 16 px gutter.

Imagery:
- Real product screenshots only: fresh, light and dark pairs, at DPR 2 in WebP, inside a consistent browser frame
  with a soft shadow.
- No stock photos.
- Icons from one set (Phosphor) at one weight.

Components:
- Buttons: primary, secondary and ghost.
- Badges, including a "No login" badge.
- Cards: feature, use case and pain.
- Step list, code tabs, FAQ accordion, comparison table, CTA band, toast, live-request widget.
- One stylesheet, no framework, under 60 KB.

Motion:
- Fade and rise on scroll, at most 200 ms.
- Respects `prefers-reduced-motion`.
- Nothing autoplays.

Quality bar:
- Lighthouse 95+ for performance, accessibility, best practices and SEO on every page.
- Keyboard navigation and visible focus everywhere.
- No layout shift: images carry width and height.

SEO:
- Unique title and description on every page.
- Canonical URLs.
- Open Graph image per page, generated at build time from the page title.
- `sitemap.xml` and `robots.txt`.
- SoftwareApplication structured data on the home page, FAQPage on feature pages.
- The build fails on a broken internal link or a missing alt text.

#### No-login tools

There are two kinds of tool: tools that run only in the browser, and tools that run on the public demo server.

**Browser-only tools** (static, nothing is sent anywhere):
- **JWT encoder and decoder.** Paste a token to see its header and claims, with expiry shown in local time. Build and
  sign HS256 or RS256 tokens with WebCrypto, and verify against a pasted JWKS or secret. Links to the JWT mock-server
  page.
- **cURL to stub.** Paste a cURL command and get a WireMock / MockHub stub JSON:
  - The method, path, query, headers and body become matchers.
  - Editable as a form.
  - Copy, download, or "Open in instant mock".
- **Stub JSON checker.** Paste WireMock JSON to get it validated against the schema, formatted, and explained in
  plain English ("matches GET /orders/{id} when header X is present").

**Demo-server tools.** These run on the public demo with `MOCKHUB_PUBLIC_TOOLS=true`, which is off by default so
private installs never expose them:
- **HTTP echo**
  - Any method on `/echo/**` answers JSON describing the request: method, path, query, headers, body, parsed form or
    multipart, client IP and size.
  - Also `/status/{code}`, `/delay/{ms}` (up to 10 s) and `/headers`.
  - Nothing is stored.
- **Webhook catcher**
  - "Create a bin" returns `/bin/{id}` and a private view link.
  - Every request sent there is captured and shown live (SSE), with headers, body, a replay-as-cURL button, and a
    configurable response status and body.
  - A bin keeps its last 100 requests and is deleted 24 hours after the last request.
- **Instant mock server**
  - "Create mock" makes a temporary anonymous project with a random slug (`/s/{slug}/...`) and a secret manage link
    (capability URL, token stored hashed). No account.
  - Opens a trimmed editor: the real stub form, the dynamic-value picker, templates, the traffic list and "Try it".
  - Can start empty, from an uploaded OpenAPI or WSDL file (up to 500 KB, the existing importers), from a CSV or JSON
    file as a CRUD resource (up to 50 records), or from a bundled example.
  - Limits:
    - 20 stubs.
    - 1,000 requests a day.
    - 64 KB per body.
    - 100 requests of history.
    - Deleted 7 days after the last change.
  - "Keep it" exports a backup, or claims the project into an account on instances with sign-in.
- **Template playground**
  - Type a Handlebars template and a sample request (method, path, headers, body), then see the rendered answer,
    status and errors as you type.
  - Uses the real WireMock engine and every MockHub helper.
  - The left rail is the picker catalogue (`dynamicValues.json`), so every helper is one click away.
- **Fake data generator**
  - Pick fields from the Datafaker catalogue (name, e-mail, address, IBAN…), a locale and a count up to 100.
  - Output as JSON, CSV or SQL inserts.
  - "Host as API" turns the result into an instant mock.

Safety for the demo-server tools. Each tool:
- Refuses outbound calls: no proxy, callouts, webhooks or `fileName` responses in anonymous projects, to stop SSRF.
- Has a per-IP rate limit (for example 60 requests a minute and 5 new bins or mocks an hour) and returns 429 with
  `Retry-After`.
- Caps request size (1 MB echo, 64 KB elsewhere).
- Has render time limits on templates.
- Is cleaned up by a scheduled job.
- Serves pages with `noindex` on anonymous URLs.
- Has a strict CSP and a "Report abuse" link.
- Keeps no personal data beyond the captured requests, and says so on the page.

#### Checklist

- W1 Site framework:
  - [x] Tokens and component CSS (the UI standards above)
  - [x] `pages.mjs` data file and page renderer
  - [x] New header with the mega menus and a phone accordion
  - [x] Five-column footer
  - [x] Sitemap, robots, structured data
  - [x] OG image per page, generated from the page title (`website/scripts/build-og.mjs`, images committed)
  - [x] Link and alt-text checks in the build
- W2 Feature pages:
  - [x] Six service-virtualization pages
  - [x] Eight mock-server pages
  - [x] Each page has a code example and an FAQ
  - [x] Fresh light and dark screenshots for every page (11 new pairs; tutorial images stay on the tutorials page)
- W3 Browser-only tools:
  - [x] JWT
  - [x] cURL to stub
  - [x] Stub JSON checker
  - [x] Tests for every parser and converter (`node --test website/tests/`, run in CI)
- W4 Demo-server tools, backend:
  - [x] `MOCKHUB_PUBLIC_TOOLS` flag
  - [x] Echo, status and delay
  - [x] Webhook bins (polling instead of SSE)
  - [x] Anonymous projects with capability links, limits and a cleanup job
  - [x] Template render endpoint
  - [x] Fake data endpoint
  - [x] Rate limiting and safety rules above (for the tools built so far)
  - [x] Unit, integration and system tests for the tools built so far
  - [x] SSRF refusal test, with anonymous projects
- W5 Demo-server tools, UI:
  - [x] Trimmed instant-mock editor (a website form, see notes)
  - [x] Bin viewer
  - [x] Playground
  - [x] Fake data page
  - [x] HTTP echo page
  - [x] Spec, CSV and example starters, and "Host as API" on the fake data page
  - [x] "Keep it" export (claim dropped, see notes)
- W6 Compare and polish:
  - [x] vs WireMock, vs Mockoon and vs Postman mock servers pages, factual and dated (checked 4 November 2026; Apache-2.0 LICENSE added)
  - [x] Homepage hero CTA becomes "Try it free, no login"
  - [x] Lighthouse 95+ on every page (all 71 pages, mobile: performance 95–100, accessibility, best practices and SEO 100)
  - [x] Phone, dark-mode and keyboard pass
- [x] Docs, a tutorial ("Try MockHub without an account"), README, deploy (demo env flag, nginx limits)

Notes (W1, W2): pages live in `website/site-src/pages.mjs` and render through `website/site-src/render.mjs`. The build
writes them to `website/<slug>/`, lists the generated folders in `website/.gitignore`, and writes `sitemap.xml` and
`robots.txt`. Menus are built from the page data, so a page appears in its menu and the footer as soon as it is added.
The Tools menu appears when the first tool page exists. The build fails on any internal link to a missing page or
anchor, a missing image or an image without alt text. nginx serves `<path>/index.html` when it exists and proxies
everything else to the demo. The examples page is generated from `src/main/resources/examples`. Pages reuse existing
product screenshots for now. The build also stopped indenting code blocks in the docs, which had broken every
multi-line example.

Notes (W4): the tools live in `io.mockhub.publictools` behind one request filter, `PublicToolsFilter`, which runs
before every other extension and only looks at `/__public/**`. When the flag is off it passes those requests on, so
private installs behave as before. Limits per client address use the existing `RateLimiter`: 120 requests a minute in
total, 30 renders or fake-data calls a minute, and 10 new bins an hour. Bins live in memory (200 bins, 50 requests
each, 10 KB per stored body, deleted after 24 idle hours), and their tokens are stored as SHA-256 hashes. The bin
viewer will poll `?after={seq}` rather than hold an SSE connection, which is simpler behind nginx and costs little at
these sizes. The render endpoint doesn't use WireMock's engine as is, because a template can loop over request data
and an output cap after rendering is too late. It builds its own Handlebars with the same helpers, each wrapped to
count calls, loop runs, text produced and size arguments against a per-render budget with a 2-second deadline. It
streams output into a 256 KB writer and runs on two worker threads with a short queue. `systemValue`, `hostname`,
`assign` and partials are off. Fake-data expressions may not contain numbers above 1,000.

Notes (W5, first part): the four server tools are website pages, `/tools/webhook-bin/`, `/tools/http-echo/`,
`/tools/template-playground/` and `/tools/fake-data/`, that call `/__public/` on the same origin. They are built like
the browser tools: markup in `website/site-src/tools/`, a page script each, and shared logic in `http.js` (header
parsing, cURL, error wording) and `fakeformat.js` (CSV and SQL), both tested with `node --test`. The playground's helper
list and the fake data types are embedded at build time from the UI's `dynamicValues.json` and `generator.ts`, so they
match the product. The bin page keeps the bin's token in `localStorage` and polls every 2 seconds while the tab is
visible. The tools index splits the server tools from the browser tools, and each server tool says what it stores.

Notes (instant mock): an instant mock is a normal project in a private organisation with the fixed id
`instant-mocks`, so it is served, matched and journalled like any other project. `POST /__public/mocks` returns a slug
(`m-` and 10 letters or digits) and a 32-character token stored as a SHA-256 hash in `instant-mocks.json`; the website
keeps both in `localStorage` and in the URL hash, which works as the private edit link. Limits: 200 mocks, 20 stubs
each, 1,000 requests a day, 64 KB per stub and per request body, deleted 7 days after the last change by an hourly
job. `InstantStubRules` allows a fixed list of stub fields and refuses proxying, callouts, webhooks, `bodyFileName`,
other transformers and the matchers that run user regexes, XPath or JSON schema, since a slow pattern in one mock
would slow down every project. `response-template` is swapped for a sandboxed transformer with the playground's
budget and a 1-second limit. With the flag on, server roles below admin don't apply to private organisations, so the
public demo login can't see instant mocks, and the native WireMock admin API is admin-only. Two changes from the
plan: the editor is a small website form (method, path, status, type, delay, body, templating) with a JSON mode,
rather than the React stub form, which needs the whole app; and "Keep it" exports WireMock mappings only, because the
demo has no sign-up to claim into.

Notes (instant-mock starters): `POST /__public/mocks` takes `from: openapi | wsdl | records | example`. A starter
builds its stubs once the project exists (the WSDL importer needs the base path), and the project is deleted again if
anything fails. Stubs from a spec or an example go through the same rules; ones that can't run are left out and
listed in `skipped`, and only the first 20 OpenAPI operations become stubs (the importer takes a `limit` and still
counts every operation). The rules accept three fixed shapes that MockHub writes itself: the CRUD stub, rebuilt from
its parameters so its pattern is always ours; the SOAP body XPath the WSDL importer writes; and `matches ".*"`. Records
become a CRUD resource; a `RecordPolicy` in `CrudService` caps instant mocks at 200 records of 4 KB. Three of the ten
examples qualify (fake data, orders CRUD, shopping cart); the rest use regexes, proxying, webhooks or files. CSV is
parsed in the browser (`parseCsv`). The fake data page's "Host as API" sends up to 50 records as a records starter.
Two fixes on the way: the OpenAPI sample builder now stops at 5,000 nodes per body, because schemas whose properties
all `$ref` the next schema could expand without limit; and the client address for public limits and IP allow-lists
is now the last `X-Forwarded-For` entry, since nginx appends the real address to whatever the client sent.

Notes (W3): the tools are plain ES modules in `website/assets/js/tools/`: pure logic (`jwt.js`, `curl.js`,
`stubcheck.js`) plus one page script each, with markup in `website/site-src/tools/`. They are tested with Node's
built-in runner instead of vitest, so the website needs no package of its own. JWT signing and checking use Web Crypto.
The RS256 key pair is made in the browser and never stored. `/tools/` lists them, and the Tools menu marks each one
"No login".

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
- [x] State viewer and persistence (`ProjectStateStore` saves contexts under the reserved `_state` resource of the request's project; Data tab State view; `/projects/{id}/state` API)
- [x] CRUD query syntax and pagination (`CrudQuery`: eq/ne/gt/gte/lt/lte, OR on repeats, dotted fields, `sort=-a,b`, `page/size`, old `_sort` style kept)
- [x] Limits and errors (`MOCKHUB_MAX_RECORDS`, default 10,000 per resource and per project's state, oldest first; every CRUD error has a stable `code` such as `invalid_json_body` and `not_found`, and 404s name the resource and id)
- [x] Tests, docs (unit, integration and system tests per item; Recorded state docs page, CRUD limits and error codes, config, limits and README)

### Phase F8 — Datasets

Goal: upload test data once and use it from any project in the organisation.

- Organisation datasets from CSV, JSON or NDJSON: name, key, identity fields, lookup indexes, typed CSV columns.
- Template helpers `dataset-one` and `dataset` with up to three equality filters.
- Upload modes: update and add, or add only. Limits: size, record count, record size.
- Dataset browser with search and a preview of a helper's result.
- Test data editor for OpenAPI mocks: open a generated stub's body as a table and edit rows, or bind it to a dataset.

Checklist:
- [x] Dataset store, upload and indexes (`io.mockhub.dataset`: CSV with typed or inferred columns, JSON, NDJSON; replace, upsert and add modes by up to 3 identity fields; up to 5 indexes; 20 MB, 100,000 records, 64 KB per record, 100 datasets per organisation; `/orgs/{id}/datasets` API, editor role in the organisation to change)
- [x] Template helpers (`dataset-one` and `dataset` with up to 3 equality filters and `limit`; organisation found from the request's project; Datasets group in the picker)
- [x] Browser and test data editor (Datasets page with search, paging, upload, settings and a lookup preview; stub editor "Edit as table" for JSON bodies that are lists, with "Move the rows to a dataset" binding the body to a dataset helper)
- [x] Backup, tests, docs (datasets travel with their organisation in backups; a copy into another organisation adds missing ones; Datasets docs page, README)

### Phase F9 — API connections and reliable callouts

Goal: callouts and proxies reach authenticated partners.

- Organisation API connections: OAuth 2.0 client credentials (token URL, client id, secret, scope, header prefix),
  secrets encrypted at rest, a "test token" button, token cache with expiry.
- Callouts and proxy stubs choose a connection; the token is added as the Authorization header.
- Callout retries: count, backoff and which statuses to retry; each attempt in the callout log.
- Callout log stored on disk with the history store from F3.

Checklist:
- [x] Connections with encrypted secrets (`io.mockhub.connection`: OAuth2 client credentials or a fixed header, AES-256-GCM with `MOCKHUB_SECRET_KEY` or a generated `mockhub/secret.key`, token cache until 30 s before expiry, test button with a masked preview; `/orgs/{id}/connections`, organisation manager to change; Connections page; up to 50 per organisation; in backups without secrets)
- [x] Token injection in callouts and proxies (`connection` on webhooks and wait-for-callout replaces a same-named header and is logged as `••• (connection key)`; `mockhub-connection` transformer on proxy stubs adds the header once and keeps other proxy headers; "Sign with" in the stub editor)
- [x] Retries (`retry: {count ≤ 5, backoffMs, statuses}`, default `429,5xx` plus network errors, wait doubling up to 30 s; webhooks reschedule in the background, wait-for-callout retries inline; each attempt logged with its number)
- [x] Tests, docs (Connections & retries docs page, config, README)

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
- [x] GraphQL (graphql-java 26.1; `graphql` import of SDL or introspection JSON into one `mockhub-graphql` endpoint stub that executes queries against the schema with generated data: field-name aware values, custom scalars by name, list sizes from first/limit up to 20, arguments and inputs echoed into same-named fields, stable per arguments, undeclared directives ignored, POST/GET/batch/application/graphql, 50,000-value and depth-15 limits; operation stubs match operationName and variables; GraphQL service type, schema response mode and console with example operations and "save answer as a stub"; Library example)
- [x] gRPC (`wiremock-grpc-extension-jetty12` 0.11.0 behind a MockHub server factory; projects upload protobuf descriptor sets (`protoc --include_imports --descriptor_set_out`), well-known types resolved without imports, 4 MB and 20 per project, a service belongs to one project and clashing services, messages or files are refused; descriptors load without a restart, travel in backups and go with the project; stubs keep the `/package.Service/Method` path unprefixed, match the request message as JSON and metadata as headers, answer with protobuf JSON or a gRPC status and reason, support templating; unary, server-streaming (one message) and client-streaming (first matching message); unmatched calls get UNIMPLEMENTED; server reflection for grpcurl; calls recorded in the owning project's history; gRPC service type, method pickers, starter stubs, grpcurl command)
- [x] SOAP auto-detect (`SoapMessage` finds version, operation, namespace and SOAPAction with a StAX parser that refuses DTDs; `soap` import of pasted sample requests makes a stub per operation plus a `mockhub-soap` endpoint at priority 10 that answers any operation with `<Operation>Response` or a fault; create stub from a logged SOAP request keeps version, action and a response envelope; "Fill operation from example" in the editor)
- [x] Mutual TLS (`MOCKHUB_HTTPS_PORT` turns on WireMock's HTTPS connector; admins paste a server certificate chain and key (PKCS#8 or PKCS#1 RSA, key checked against the certificate, stored sealed with the secret key) and trusted client CAs on the Server page, applied with a Jetty SSL reload and kept after restarts; client certificates are requested (want, not need) so untrusted ones count as anonymous; a servlet filter strips forged `X-MockHub-Client-Cert-*` headers and sets subject, issuer and SHA-256 from the TLS session, also for gRPC metadata; per-project runtime `requireClientCert` answers 403 / PERMISSION_DENIED; the HTTPS port can't move at runtime, so it stays an environment setting)
- [x] Tests, docs, examples (Inventory gRPC example with unary, server-streaming and client-streaming methods, statuses, metadata matching and templating; example descriptor sets installed with the project and refused with 409 when the service is already served; examples page shows grpcurl for gRPC; the WireMock comparison now lists GraphQL and gRPC)

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
- [x] Echo template (`mockhub-echo` transformer shared with the public echo tool: method, project-relative path, query, headers without proxy headers, body, parsed JSON and form fields, 1 MB limit, stub status, headers and delays kept; "Echo request" response type in the editor; Echo server example installed at `/echo`)
- [x] Custom domains (`domains` on a project, up to 10 host names, validated and unique across projects; a servlet filter rebases requests whose Host matches, before WireMock sees them, so matching, templates, the request log and runtime rules work unchanged; base path still works; `/__admin` and gRPC untouched; Custom domains panel in project settings; backups keep names not taken by another project; docs with nginx and Caddy)
- [x] Mock these APIs button (`POST /openapi/fetch` downloads an OpenAPI URL for editors, 5 MB, 20 s, 3 redirects, private addresses refused on every hop unless `MOCKHUB_PLAYGROUND_ALLOW_PRIVATE`; the `mock-these-apis?openapi=` page previews it and creates a project with its stubs; Link, Markdown and HTML snippets with a public SVG badge; sidebar and command palette entries)
- [x] Forward proxy with interception (`MOCKHUB_PROXY=true` turns on WireMock browser proxying on the main port with a CA keystore kept in `/data/mockhub/proxy`; the jar manifest exports `sun.security.x509` so certificates can be generated; a servlet filter refuses clients outside `MOCKHUB_PROXY_ALLOW` (loopback and private by default) and rebases proxied calls into the project chosen on the Server page, so stubs answer and misses are forwarded to the real service and recorded; public `proxy/ca.pem`; Forward proxy panel with CA download and setup commands; docs for curl, browsers, macOS and Android. The plan's separate port became the main port, which is how WireMock serves it)
- [x] Tests, docs (unit, integration and system tests for each item; docs pages for echo, custom domains and the forward proxy; import docs for Mock these APIs)

### Phase F12 — Local tunnel

Goal: a stub can proxy to a service on a developer's laptop, so webhooks reach localhost.

- `mockhub-tunnel` CLI (a single Java or Node binary) opens a WebSocket to MockHub with a personal access token and
  registers a tunnel name.
- Proxy stubs and callouts can target `tunnel://<name>/path`; MockHub sends the request through the socket and
  streams the answer back.
- Tunnel list in the account dialog with status and traffic.
- Limits on body size and concurrent requests; tunnels close when the token is revoked.

Checklist:
- [x] Tunnel protocol and server side (HTTP long polling instead of a WebSocket, so it passes through nginx with no new dependency and holds no server thread while waiting: `POST /__admin/mockhub/tunnel/{name}/next` waits up to 25 s for a request, `POST .../responses/{id}` returns the answer; JSON messages with header pairs and base64 bodies; the first poll registers the name for its owner, who needs the editor role and a token that isn't review-only; a name held by someone else gets 409; `GET /tunnels` and `DELETE /tunnels/{name}`)
- [x] CLI (`java -jar mockhub.jar tunnel --server --token --name --to [--workers]`, also `MOCKHUB_URL` and `MOCKHUB_TOKEN`; workers poll, call the local service and send the answer back; 502 when the local service can't be reached; stops on 4xx such as a revoked token, retries on 5xx and network errors)
- [x] Proxy and callout targets (`tunnel://name/path` as a proxy target, then-call and wait-for-callout URL; only projects the owner can edit and the token allows; 5 MB bodies, 20 waiting requests per tunnel, 5 tunnels per user, 30 s per request; errors in `X-MockHub-Tunnel-Error`; revoking the token closes its tunnels; Tunnels tab in the account dialog with status and traffic)
- [x] Tests, docs (unit tests for the hub, messages and CLI options; integration tests with a real CLI and a local service for proxy, callout, offline, revoke and name clashes; Local tunnel docs page)

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
| F6b Website and no-login tools | L | Every feature gets a page; visitors try MockHub without an account |
| F7 State and CRUD | S | Small, user-visible |
| F8 Datasets | M | Builds on F7 |
| F9 API connections | S | Builds on F3 |
| F10 Protocols | L | Depends on extension compatibility |
| F11 Routing extras | M | Independent |
| F12 Local tunnel | M | Needs F4 tokens |
| F13 Contract drift | L | Needs F3 history |
