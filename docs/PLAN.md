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
