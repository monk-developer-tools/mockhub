# MockHub — Plan

A self-hosted, team-friendly mock server for REST and SOAP built on **WireMock 3**, with a web UI,
per-project mock URLs, Postman/Bruno export, restart-safe persistence, shipped as **one fat JAR in one
Docker image** that runs on AWS ECS (Fargate) with EFS.

## Architecture

```
                 ┌────────────────── one container, one port (8080) ───────────────────┐
  browser  ───►  │  /__admin/mockhub/ui        React UI (bundled in the JAR)            │
                 │  /__admin/mockhub/api/*     MockHub API (WireMock AdminApiExtension) │
  devs/tests ──► │  /__admin/*                 Native WireMock admin API (all features) │
  services   ──► │  /{project-slug}/...        Mocked REST + SOAP endpoints             │
                 └──────────────────────────────┬──────────────────────────────────────┘
                                                │ persists
                              /data (EFS on ECS, a volume locally)
                              ├── mappings/      WireMock stub JSON (one file per stub)
                              ├── __files/       response body files
                              └── mockhub/projects.json
```

Why this shape:

* **WireMock is the engine, unchanged.** MockHub is a WireMock extension, so every WireMock feature
  works: matchers, templating, scenarios, faults, delays, proxying, recording, webhooks.
  The UI has a full JSON editor for anything the form doesn't cover.
* **One port.** UI, API and mocks share port 8080, so ECS needs one target group and one ALB rule.
* **Persistence that doesn't depend on the UI.** A `StubLifecycleListener` marks every stub
  `persistent`, so stubs created by the UI, the native admin API, recordings or imports are all written
  to `/data/mappings`. A restart or a new task reloads them.
* **Projects = URL prefix.** Project `payments` gets base URL `https://mocks.company.com/payments`.
  Stubs are stored with the full path and tagged `metadata.mockhub.projectId`; the UI shows paths
  relative to the project.

## Phases

| # | Phase | Contents |
|---|-------|----------|
| 1 | Core server | Fat JAR, `Main` with env-driven defaults, extension, forced persistence, projects store, project + stub API, static UI serving |
| 2 | UI foundation | React + Vite + TS. Projects dashboard, project page, stub list, search, copy base URL |
| 3 | Stub editor (REST) | Form editor (method, URL match types, query/headers/body matchers, response, headers, templating, delay, fault, proxy, scenarios, priority) + raw JSON editor + "Try it" |
| 4 | SOAP | SOAP form mode (SOAPAction, XPath/operation matching, SOAP 1.1/1.2, fault builder), WSDL import that generates one stub per operation and serves `?wsdl` |
| 5 | Exports | Postman v2.1 collection (with tests), Bruno collection (zip of `.bru`), WireMock mappings JSON; WireMock-JSON import; OpenAPI import |
| 6 | Observability | Per-project request log, unmatched requests and near misses with "create stub from request", scenarios view + reset, recorder |
| 7 | Ops | Optional admin auth, health endpoint, Dockerfile (jlink runtime, distroless, non-root), docker-compose, GitHub Actions → Docker Hub (amd64+arm64), ECS CloudFormation (Fargate + EFS + ALB) |

## Ideas from similar products

| Product | Idea worth taking |
|---|---|
| Microcks | Build mocks from contracts (OpenAPI, WSDL, Postman). → WSDL + OpenAPI import |
| Mockoon | Several responses per route, picked by rules. → WireMock priorities and scenarios, plus templates in the editor |
| Beeceptor | Each project gets its own endpoint and a live request inspector. → Per-project base URL and request log |
| WireMock Cloud | Record and playback, chaos (faults and delays), a stub from an unmatched request |
| EasyMocker | Service → Operation → Response for SOAP, plus a built-in test client. → SOAP mode and "Try it" |

## More suggestions (backlog)

1. **Git sync.** Mirror `/data` to a Git repo so mocks are versioned and reviewable.
2. **SSO.** Use ALB OIDC auth in front of `/__admin/*` (Okta/Azure AD/Cognito). No code change is needed.
3. **Multiple replicas.** Today run `desiredCount=1`, because WireMock state lives in memory. To scale out,
   add a file watcher or `POST /__admin/mappings/reset`-style reload broadcast, or move to a shared DB store.
4. **Environments per project.** For example `dev`/`qa` response sets, switched with a scenario state.
5. **Contract validation.** Reject requests that don't match the imported OpenAPI schema.
6. **Faker helpers.** Realistic data in templates (WireMock 3 ships `{{randomValue}}`; add datafaker).
7. **Audit log.** Who changed which stub, and when.

## Engineering conventions

- SOLID: services depend on ports (`ProjectRepository`, `StubGateway`); exporters/importers are strategies in registries;
  controllers are small and registered in one composition root (`MockHubExtensionFactory`); project changes
  propagate to stubs through an observer (`ProjectListener`).
- No comments in production code. Tests use `// Given`, `// When`, `// Then`.
- Tests live in `src/test` by package: unit (`io.mockhub.<feature>`), `io.mockhub.integration` (in-process server),
  `io.mockhub.system` (the packaged fat JAR run as a process, including restart).
  `./gradlew test` runs all; `unitTest`, `integrationTest`, `systemTest` run one level.

## Progress checklist

Ticked as each item is implemented and verified.

### Phase 1 — Core server
- [x] Gradle project (Kotlin DSL + wrapper), fat JAR (`build/libs/mockhub.jar`), `Main` with env config (PORT, MOCKHUB_DATA_DIR, MOCKHUB_PUBLIC_URL, MOCKHUB_USERS, WIREMOCK_OPTIONS)
- [x] Forced persistence of every stub (`StubLifecycleListener`) → survives restart
- [x] Projects store (`/data/mockhub/projects.json`, atomic writes)
- [x] Project API: list/create/update/delete, slug change re-prefixes stubs, delete removes stubs
- [x] Project stub API: list/create/update/delete/clone with project-relative URLs
- [x] Project request log API (filtered journal), clear log
- [x] Static UI serving with SPA fallback at `/__admin/mockhub/ui/`
- [x] Optional auth (admin / editor / viewer) over the whole admin API

### Phase 2 — UI foundation
- [x] Vite + React + TS app, layout, theme (light/dark)
- [x] Projects dashboard (create, edit, delete, base URL copy, counts)
- [x] Project page with tabs: Stubs, Requests, Import/Export, Settings
- [x] Login screen when auth is on

### Phase 3 — REST stub editor
- [x] Form: method, URL match type, query/header/cookie/body matchers, priority, name
- [x] Response: status, headers, body (JSON/text/XML/file), templating, delay, fault, proxy
- [x] Scenarios (name / required state / new state)
- [x] Raw JSON editor (full WireMock power), form ↔ JSON sync
- [x] "Try it" panel calling the mock from the browser

### Phase 4 — SOAP
- [x] SOAP form mode (SOAP 1.1/1.2, SOAPAction, operation/XPath match, envelope + fault templates)
- [x] WSDL import → stub per operation with XSD-generated sample request/response, `?wsdl` endpoint, unknown-op fault

### Phase 5 — Import / export
- [x] Postman v2.1 collection (folders, baseUrl variable, status tests)
- [x] Bruno collection zip (`bruno.json`, environment, `.bru` per request, asserts)
- [x] WireMock mappings export + import
- [x] OpenAPI (JSON/YAML) import → stubs from examples

### Phase 6 — Observability
- [x] Request log with detail drawer, matched/unmatched filter
- [x] Near misses + "create stub from request"
- [x] Scenarios view, reset
- [x] Recorder (start/stop/snapshot to project)
- [x] All-stubs view incl. stubs not in a project, assign to project

### Phase 6b — Built-in examples
- [x] Example catalog bundled in the JAR (REST basics, templating, scenarios/stateful flow, faults & delays, SOAP calculator from WSDL)
- [x] API: list examples, install one as a project
- [x] UI: "Explore examples" on the dashboard and empty states, one-click install
- [x] Stub editor template gallery (REST + SOAP snippets)

### Phase 7 — Ops
- [x] Dockerfile (multi-stage, jlink JRE, distroless, non-root, /data volume)
- [x] docker-compose for local use
- [x] GitHub Actions: test, build, push multi-arch to Docker Hub
- [x] ~~AWS: CloudFormation~~ replaced by Terraform (phase 8)
- [x] README with quick start

### Phase 8 — Terraform for AWS ECS
- [x] Terraform root module (`deploy/terraform`): ECS Fargate, EFS + access point, ALB (+HTTPS), IAM, logs, security groups
- [x] `terraform validate` + tfvars example, remove CloudFormation, update docs

### Phase 9 — Product website (mockhub.devops-monk.com)
- [x] Static site in `website/` (landing, features, SOAP, exports, deploy, docs quick start)
- [x] Inspect VPS read-only (nginx sites, ports, certbot) — do not touch existing apps or Postgres
- [x] Add one nginx server block + TLS (certbot), `nginx -t` before reload
- [x] Deploy script `website/deploy.sh` (rsync), verify https://mockhub.devops-monk.com

### Phase 10 — Organisations, users and single sign-on
Design:
- **Organisation → projects.** Every project belongs to one organisation (`orgId`). Mock URLs stay `/<base-path>/…` and base paths stay unique on the server, so existing clients keep working. On first start an organisation called "Default" is created and projects without an organisation move into it.
- **Roles.** Organisation roles: viewer < editor < owner. Each user also has a server role: none, viewer, editor or admin. A user's role in an organisation is the higher of the server role and the organisation membership. Admins manage everything (users, SSO, server, backups). Owners manage their organisation's settings and members.
- **Users.** There are three sources:
  - **env:** `MOCKHUB_USERS`, read-only, the break-glass admin.
  - **local:** created in the UI, passwords stored as PBKDF2.
  - **azure:** created on first SSO sign-in.
  Users are stored in `<data>/mockhub/users.json` and organisations in `organizations.json`.
- **Sessions.** `POST /auth/login` returns a signed token (HMAC-SHA256, secret in `<data>/mockhub/secret.key`, 12-hour lifetime) that the UI sends as `Authorization: Bearer`. Basic auth still works for scripts. Changing a password or disabling a user invalidates that user's existing tokens.
- **Sign-in on/off.** Sign-in is required as soon as there is any user (env or local) or SSO is enabled. With sign-in off, everyone is an anonymous admin. The UI offers "create the first admin" to turn it on.
- **SSO (Microsoft Entra ID).** OIDC authorisation code flow with PKCE, configured in the UI by an admin. Settings:
  - tenant ID, client ID and client secret (write-only; `MOCKHUB_SSO_CLIENT_SECRET` overrides it)
  - cloud
  - button label
  - allowed e-mail domains
  - default server role
  - default organisation and role
  - mappings from Entra groups or app roles to an organisation role or server role
  - "Allow password sign-in"
- **SSO flow:**
  1. The browser opens `/auth/sso/azure/start`.
  2. MockHub redirects to Entra, which redirects back to `/auth/sso/azure/callback`.
  3. MockHub checks the id_token: RS256 signature against the tenant JWKS, plus issuer, audience, expiry and nonce.
  4. MockHub creates or updates the user and redirects to the UI with a one-time code (60 seconds) that the UI exchanges for a session token.
  5. Group-derived roles are recomputed on every sign-in and kept separate from roles set by hand in the UI.
- **Code structure:** packages `org`, `user`, `auth`, `sso`. Ports `OrganizationRepository`, `UserRepository` and `SsoSettingsRepository`, plus `HttpTransport` so Entra can be faked in tests. Strategy `SsoProvider` (`AzureEntraProvider`; others can be added). `AccessPolicy` makes every authorisation decision.

Checklist:
- [x] Backend: organisations, users, tokens, access policy, project → organisation, migration
- [x] Backend: Entra SSO (settings API, test connection, start/callback/exchange)
- [x] UI: organisation switcher, organisation settings and members, users admin, SSO settings with setup guide, Microsoft button on login, account/password dialog
- [x] Tests: unit + integration (fake Entra on WireMock) + system (users/orgs survive restart) + UI
- [x] Docs + website: organisations, users, SSO setup guide

### Phase 11 — AI assistant (OpenRouter, LiteLLM, OpenAI-compatible)
Ideas from the market: Beeceptor and WireMock Cloud let AI agents act on mocks through tools (MCP), and general
models already know WireMock's stub format. MockHub builds this in, with a review step.

Design:
- Admin settings (`ai.json`, key write-only, `MOCKHUB_AI_API_KEY` overrides). Strategy `AiProvider`: OpenRouter
  (default base URL, `HTTP-Referer`/`X-Title` attribution, `openrouter/auto`), LiteLLM proxy (key optional, its
  model names), other OpenAI-compatible. Port `LlmTransport` (HTTP/1.1, JDK client) so tests fake the model.
- Per-project chat: tool-calling loop (max rounds configurable). Read tools: `list_stubs`, `get_stub`,
  `list_requests`. Write tools *stage* changes (`create_stub`, `update_stub`, `delete_stub`) after validating
  them; nothing is written until a user applies. Staged changes are kept on the client and sent back each turn.
- Viewers may chat; applying needs editor. Request-log content is marked as untrusted data in the prompt.
- UI: Assistant tab (chat, suggestions, review panel with diffs, apply/discard per change or all), admin page
  with provider picker, model list from `/models`, test connection; "Describe the API" in the new-project dialog.

Checklist:
- [x] Backend: settings, providers, chat client, tools, assistant loop, apply, access rules
- [x] UI: assistant tab, review and diff, admin page, describe-on-create
- [x] Tests: unit (fake model), integration (fake OpenAI-compatible server on WireMock), UI
- [x] Docs + website
- [x] Background chat jobs with live progress: replies survive switching tabs or reloading, no proxy timeouts with slow models, staged work kept if the provider fails part-way

### Phase 12 — Selective backup and restore
- Backup a selection: whole organisations, single projects, or a mix (`POST /backup`), named after what it holds.
- Restore with a preview (`POST /restore/preview`): per project it shows new, replaces existing, or base path
  taken (with the new path). Choose projects, target organisation and replace-or-copy. Replace makes the
  project's stubs match the backup exactly; copy gives new ids and a free base path and rewrites stub URLs.
- Older backup files still restore.

Checklist:
- [x] Backend: `BackupService` (export, preview, restore), `BackupSelection`, `RestoreOptions`, admin-only
- [x] UI: Administration → Backup & restore with organisation/project tree, preview and options
- [x] Tests: unit, integration (backup one project, preview, restore as copy on another server), UI
- [x] Docs + website

### Phase 13 — Teams inside organisations, avatars
- Organisation › teams › people. Teams own projects; roles are set per team, and organisation-wide roles still apply
  to every team. Effective role on a project = max(organisation-wide, team role). Team owners manage their team.
- Startup migration: every organisation gets a *General* team and projects without a team move there.
- Users page: access tree to set organisation-wide and per-team roles when creating or editing a user.
- Entra group mappings can target a team; backups carry teams and restore can place projects in a chosen team.
- UI: Teams section and team pages, team picker for projects, team filter and column on the dashboard, sidebar
  grouped by team, generated SVG avatars for people and marks for teams.

Checklist:
- [x] Backend: `Team`, `TeamService`, `TeamMembership`, `TeamChoice`, `TeamController`, team-aware `AccessPolicy`
- [x] Users, SSO mapping, backup and examples aware of teams; migration on startup
- [x] UI: team pages, access editor, avatars, grouping and filters
- [x] Tests: unit, integration (`TeamsIntegrationTest`), UI
- [x] Docs, tutorial and website

## Roadmap: closing the gap with hosted mock services

Compared feature by feature with Beeceptor (October 2026). MockHub already covers no-code mocking, request
inspection, templating, OpenAPI/WSDL import, proxy and record, delays and faults, scenarios, an AI generator and team
sharing. The phases below close the gaps that matter most. Where an official WireMock extension exists we use it, as
long as it works with WireMock 3.13 on Jetty 12; otherwise we build a small extension of our own.

### Phase 14 — CRUD routes, fake data, webhooks and body files (done)

**14a. CRUD routes backed by a data store**
- Goal: `POST /customers` stores an object, `GET /customers/{id}` returns it, `PUT`/`PATCH` change it, `DELETE`
  removes it, `GET /customers` lists them. No code, schema-less JSON.
- Engine: `org.wiremock.extensions:wiremock-state-extension` (0.10.1) for record/delete state and state lookups in
  templates. Spike first: confirm it loads with WireMock 3.13 + Jetty 12 and our extension factory.
- "New CRUD resource" in the Stubs tab: resource name (`customers`), id field (`id`, generated UUID or taken from the
  body), optional seed records. Generates the five stubs, grouped in one folder, editable like any stub.
- **Data** tab per project: see stored records per resource, add/edit/delete one, clear a resource, reset everything.
- Persistence: the store is in memory by default. Add an option to save it to `/data/mockhub/state/<project>.json`
  and reload on start, so demo data survives restarts. Backups include it.
- Counters and simple key/value stores (step counters, "last order id") through the same extension.
- AI assistant: tool to create a CRUD resource; system prompt explains state helpers.

**14b. Realistic fake data**
- Goal: `{{fake 'Name.fullName'}}`, `{{fake 'Address.city'}}`, `{{fake 'Commerce.productName'}}` in any response, in
  many locales.
- Engine: decide after a spike between `org.wiremock.extensions:wiremock-faker-extension` (still beta, 0.3.0-beta.1)
  and our own `TemplateHelperProviderExtension` backed by Datafaker. Prefer whichever is stable on 3.13; an own
  helper is a few dozen lines and avoids a beta dependency.
- Locale: server default `MOCKHUB_FAKE_LOCALE` (default `en`) and `locale='de'` per use. (Simpler than a project
  setting; revisit if teams ask for it.)
- Editor: "Insert" menu in the body editor with common helpers (name, e-mail, phone, address, company, UUID, date,
  number range, lorem), and a live preview of the rendered response.
- OpenAPI import: fill strings by format and field name (`email`, `phone`, `city`, `name`, `uuid`, `date-time`)
  instead of fixed sample values; arrays get 3–5 generated items.
- AI assistant: knows the helper and uses it for realistic data.

**14c. Webhooks/callouts in the form editor, and response body files**
- Webhooks: a "Then call" section in the editor: method, URL (templated), headers, body (templated), delay (fixed or
  random). Saved as WireMock `serveEventListeners` (webhook support already ships in the image). Multiple callouts
  per stub. Remove the "unsupported in the form" warning for webhooks.
- Callout log: moved to Phase 15 with request history. It needs MockHub to send webhooks itself instead of through
  WireMock's built-in listener, which would break compatibility with existing WireMock mappings.
- Body files: upload, list, download and delete files under `__files` per project (`/projects/{id}/files`, size
  limit, content type kept). The editor's *File* body type gets a picker and an upload button.
- Large responses: allow binary files (images, PDFs, zips) with the right content type.
- Backups and exports include the files a project uses.

Spike results (16 Oct 2026):
- `wiremock-state-extension` 0.10.1 supports WireMock 3.7+ and takes a pluggable store. It keeps state as flat
  string properties and its lists can't be updated, so schema-less JSON CRUD on top of it would be fragile. Decision:
  CRUD resources use MockHub's own transformer (`mockhub-crud`) and a per-project JSON data store saved under
  `/data/mockhub/data/`. The state extension is still bundled (in memory) for counters and step tracking.
- `wiremock-faker-extension` 0.3.0-beta.1 targets WireMock 4 betas, so it can't load on 3.13. Decision: a MockHub
  template helper `fake` backed by Datafaker 2.7.0 (Java 17+).

Checklist:
- [x] Spike: state and faker extensions on WireMock 3.13 + Jetty 12 (decide engine for 14b)
- [x] 14a backend: state extension wiring, CRUD generator, data API, persistence on disk, backup support
- [x] 14a UI: New CRUD resource dialog, Data tab
- [x] 14b backend: fake helper, locale setting, OpenAPI import uses realistic data
- [x] 14b UI: Fake data menu in the editor (preview through Try it)
- [x] 14c backend: webhooks through WireMock's built-in listener, body file API, backup of files
- [x] 14c UI: Then call section, file picker and upload, Response files panel
- [x] AI assistant prompt updated for CRUD resources and fake data
- [x] Tests: unit, integration (CRUD round trip, persistence across restart, webhook delivered to a test server,
      file upload and serve), system, UI
- [x] Docs, tutorials (CRUD resource, fake data, webhooks) and website

**14d. Gaps found against a competitor's guides (17 Oct 2026)**
- Turn a stub off without deleting it: `metadata.mockhub.disabled`, enforced with a `mockhub-disabled` custom
  matcher that never matches. The API hides the matcher and restores any custom matcher the stub had.
- Usage per stub: call count and last call from the request log (`GET /projects/{id}/usage`), shown in the stub list
  and the editor.
- Random failures: `metadata.mockhub.errorRate` `{percent, status, body, fault}` applied by a global transformer;
  failed calls carry `X-MockHub-Simulated-Error`. SOAP stubs fail with a SOAP fault.
- API keys per project: header and value in project settings; a stub request filter answers 401 (JSON, or a SOAP
  1.1/1.2 fault). `OPTIONS` and `?wsdl` stay open. Try it, snippets and Postman/Bruno exports send the key.
- C# and Apex code snippets.
- *Generate with AI* for a single response body (`POST /projects/{id}/ai/body`, editor role).

Checklist:
- [x] Backend: stub switch, usage, error rate transformer, API key filter, AI body generator
- [x] UI: on/off switch and usage, Failures section, API key panel, AI body dialog, C# and Apex snippets
- [x] Tests: unit, integration, UI
- [x] Docs, tutorials and website

**14e. Playground, data generator and use-case guides (17 Oct 2026)**
- Playground: server-side requests to mocks or public APIs (no CORS limits), status/time/size/headers/body, cURL copy,
  save a response as a stub, replay from the request log. Per-user history (100 entries, secrets hidden, 64 KB bodies).
  Private, loopback and link-local targets refused unless `MOCKHUB_PLAYGROUND_ALLOW_PRIVATE=true`; no redirects;
  2 MB and 30 s limits. Own mocks are called locally; `/__admin` is refused. Editor role.
- Data generator: `POST /fake/generate` and a dialog in the stub editor and CRUD dialog; typed numbers and booleans,
  nested fields, up to 1,000 records.
- Dashboard "How it works" strip; Playground quick action on the project overview; Hugging Face provider for the AI
  assistant.
- Tutorials: use-case flows mapped to tutorials and built-in examples; Playground and data generator tutorials.

Checklist:
- [x] Backend: playground service, target guard, history, data generator, access rules
- [x] UI: Playground page, save as stub, replay, data generator dialog, dashboard strip
- [x] Tests: unit, integration, UI
- [x] Docs, tutorials with use cases, website

### Phase 15 — Agents, history and access control (recommended)

- **MCP server.** Let Claude, Cursor and other agents create, edit and inspect mocks. Expose the assistant's tools
  (`list_stubs`, `create_stub`, `update_stub`, `delete_stub`, `list_requests`, CRUD resources) over MCP (Streamable
  HTTP) at `/__admin/mockhub/mcp`, authenticated with a personal access token and limited to the user's teams.
  Changes can apply directly or be staged for review, set per token.
- **Personal access tokens.** Long-lived tokens for CI and MCP, created in the account dialog, scoped by role.
- **Request history on disk with search.** Keep requests for a configurable time (default 7 days) in an append-only
  file per project; search by path, method, status, header and body text; filter matched/unmatched; export as HAR.
- **Live traffic.** Requests per second chart and counts for a chosen period, for load tests.
- **Webhook callout log.** Show outgoing webhook calls, their status and timing next to the request that triggered
  them.
- **Per-project protection.** Optional access key: mocks answer only when a header (e.g. `X-Mock-Key`) matches.
- **Rate limits.** Per project or per stub: requests per second/minute; return 429 with `Retry-After` when exceeded.
- **Default response.** A project setting for what unmatched requests get (status, body), instead of a manual
  catch-all stub.
- **CORS allow list.** Per project origins instead of the server-wide on/off switch.

Checklist:
- [ ] Personal access tokens
- [ ] MCP server with staged or direct changes
- [ ] Request history store, search and HAR export
- [ ] Live traffic chart
- [ ] Access key, rate limits, default response, CORS allow list
- [ ] Tests, docs and tutorials

### Phase 16 — More protocols and versioning (later)

- **GraphQL** mocks from an SDL or introspection: match by operation name and variables. Candidate:
  `io.github.nilwurtz:wiremock-graphql-extension` (0.9.0, built against WireMock 3.9); verify on 3.13.
- **gRPC** mocks from `.proto` files with `org.wiremock:wiremock-grpc-extension` (1.0.0-beta.5); verify it works on
  Jetty 12, otherwise wait for a stable release.
- **Project versions.** Save a named snapshot of a project's stubs and settings, compare two versions, roll back.
- **Datasets.** Upload CSV or JSON test data once per team and use it from templates in any project.
- **Mutual TLS and forward proxy** in the Server page instead of raw `WIREMOCK_OPTIONS`.

### Not planned

- Local tunnel to a laptop: use ngrok or cloudflared, which do this well.
- Contract drift detection and API governance: a separate product.
- Stored OAuth credentials for outbound calls: revisit if webhooks need authenticated targets.
