# MockHub vs Beeceptor: gap plan

Checked on 2026-11-06 by going through the Beeceptor console (endpoint `wh0aa7e0827bd6669cc7`): every dialog, every rule type, every operator, the full Insert Dynamic Value list and the request log. Also every page under the header menus (Service Virtualization, Mock Servers, Tools, Features, Docs, Pricing) and the product docs sidebar.

Legend: ✅ have it · 🟡 partial · ❌ missing · ❔ to verify before building

Each item is built the usual way: UI, unit, integration and system tests (Given/When/Then), docs page, README and site copy. One phase at a time, deployed and checked live before the next.

---

## 1. What we already match

The signed-in MockHub app already covers almost all of Beeceptor's platform. These stay as they are:

| Beeceptor | MockHub |
|---|---|
| Mock rules, advanced matching, execution order | ✅ Stubs with all WireMock matchers, reorder |
| CRUD routes | ✅ CRUD resources with filters, sort, paging |
| HTTP callout rule | ✅ Webhooks, forward, wait for callout, retries |
| Large responses (file/blob) | ✅ Response files |
| Template engine, fake data, locales | ✅ Handlebars helpers, `fake`, 12 languages |
| Stateful helpers (counter, data-store, list) | ✅ State extension |
| Datasets (`dataset`, `dataset-one`) | ✅ Organisation datasets and helpers |
| JWT helpers | ✅ Sign, decode, JWKS |
| OpenAPI, SOAP/WSDL, GraphQL SDL, gRPC proto mocks | ✅ Importers (signed in) |
| Mutual TLS | ✅ |
| Default response, latency profiles, rate limits, CORS allow list | ✅ |
| Proxy and intercept, forward proxy, local tunnel, custom domains | ✅ |
| Request history with search, facets, retention | ✅ |
| Snapshots, stateful data UI, sharing and teams, SSO, audit logs, IP allow list | ✅ |
| API connections (OAuth for callouts) | ✅ |
| Mock server security (required header) | ✅ Project API key |
| AI rules generator, MCP agentic mode, contract drift detection | ✅ |
| Live traffic RPS | ✅ |
| HTTP echo server, "Mock these APIs" button, error code reference | ✅ |
| Webhook bin: capture, event badges, answer beside request, rules, weighted answers, delays, templates, forward, tunnel, AI draft, view-only share, several bins | ✅ |
| Free fake data generator as JSON/CSV/SQL, hosted as an API | ✅ |
| Free instant mock from OpenAPI, WSDL, CSV or JSON records, examples | ✅ |

---

## 2. Webhook bin gaps (free tool vs Beeceptor endpoint console)

### B1 — Request matching parity ✅ done
| Beeceptor | Ours | Status |
|---|---|---|
| Methods GET, POST, PUT, PATCH, DELETE, **QUERY**, OPTIONS, **TRACE**, Any | Added | ✅ |
| Path: exactly, starts with, contains, **doesn't contain**, **matches template `/users/:id`**, regex | Added | ✅ |
| Query parameter: equals, **not equals**, contains, **doesn't contain**, regex, **is absent**, is present | Added | ✅ |
| Body parameter (JSON, XML **or form data**): equals, not equals, contains, doesn't contain, **is null**, **is not null** | Added | ✅ |
| Body contains, body regex | ✅ | ✅ |
| Header regex | ✅ (and more ops) | ✅ |
| **SOAP action matches** | Added | ✅ |
| **GraphQL query (operation) matches** | Added | ✅ |

Build: add `QUERY`/`TRACE`, negative operators (`not_equals`, `not_contains`, `absent`) for every target, `is_null`/`not_null` for body fields, a `template` path operator that also fills `pathParam`, `form` and `xml` (XPath) targets, `soap_action` and `graphql_operation` targets.

Shipped: QUERY and TRACE; `not_equals`, `not_contains` and `absent` on every target (a missing value counts as not equal); `template` on the path with `{{pathParams.x}}`; `form`, `xml` (hardened XPath), `soap_action` and `graphql` targets. A JSON field that is `null` counts as absent, which covers Beeceptor's is null / is not null.

### B2 — Response editor parity ✅ done
| Beeceptor | Ours | Status |
|---|---|---|
| **Set Content-Type** picker (JSON, XML, HTML, JavaScript, CSV, text) | Type picker beside the body | ✅ |
| **Send a file/blob** as the body | Send a file, up to 16 KB | ✅ |
| `{}` pretty-print the body | `{ }` formats JSON | ✅ |
| **Rule description** (Additional information) | Description, up to 500 characters | ✅ |
| Weighted answers with a **description** and **percent** shown | Weights, description and % | ✅ |
| Single vs weighted switch | One answer / Weighted answers switch | ✅ |

### B3 — Dynamic values in the bin ✅ done
| Beeceptor | Ours | Status |
|---|---|---|
| Picker grouped into 13 categories: request components, control flow, data manipulation, JWT, stateful, comparison, string, arithmetic, date, array, validation, response components, Faker (300+) | Searchable, grouped picker built from the app's catalog | ✅ |
| Stateful helpers per endpoint (step-counter, data-store, list) | `counter`, `saveValue`/`savedValue`, `addToList`/`savedList`, per bin | ✅ |
| `pathParam`, `cookie` | `{{pathParams.id}}`, `{{request.cookies.x}}` | ✅ |

Shipped: the bin reads `ui/src/model/dynamicValues.json` through `dynamicValues('bin')`, which drops what the sandbox can't run (datasets, the app's state helper, `hostname`, base URL, request id) and adds a **Bin state** group. `BinCatalogTest` renders every item the bin offers. State lives in `state.json` beside `bin.json` (100 names, 4 KB a value, 64 KB in all), with `GET`/`DELETE /state` and a **Saved state** section in Rules. Validation helpers now answer with their own status instead of 500.

### B4 — Request log parity ✅ done
| Beeceptor | Ours | Status |
|---|---|---|
| Pretty-print toggle per body | Raw / Pretty per body | ✅ |
| Copy body | Copy body on both sides | ✅ |
| Copy as cURL | ✅ | ✅ |
| **Share one request and its response** as a permanent link | Per-request share key; works while the bin lives | ✅ |
| "A rule matched" icon that opens the rule | Open rule beside the answer | ✅ |
| Make a rule from a captured request | Make a rule from it | ✅ |
| Search and filter by path, method, status, event | Text, method and status filters, server side, disk included | ✅ |
| Delete one request; download a multipart file from a request | Delete one; multipart files and binary bodies kept as bytes and downloadable | ✅ |
| Export the log (JSON or HAR) | HAR 1.2 or JSON, newest 1,000 matching | ✅ |

Shipped: `BinLogFilter`, `BinHar`, `Bins.delete/one/shareKey/export`, raw bytes for bodies that aren't UTF-8 (`raw`, base64, up to 64 KB), deleted sequence numbers kept in `bin.json`.

### B5 — Callout rules in the bin ✅ done
| Beeceptor (callout rule) | Ours | Status |
|---|---|---|
| Synchronous: wait for the target, answer with its response | `mode: sync`, 15 s, 1 MB; failure is 502 *Callout failed* | ✅ |
| Asynchronous: mock answer now, call in the background | `mode: async`, random wait between min and max, up to 60 s | ✅ |
| Method "same as original" or a chosen one | `method: same` or GET, POST, PUT, PATCH, DELETE (and the rest by API) | ✅ |
| Original or custom templated payload | `payload: original` or `custom`; URL, body and headers take `{{…}}` | ✅ |
| Authentication | Bearer, Basic, custom header | ✅ |
| Result per request | `callout` on each logged request: state, status, time, error, async response | ✅ |

Not the app's callout code: that is tied to WireMock serve events and project connections. `BinCallout`, `BinCaller` (same guard as forward), `BinScheduler` (20 waiting per bin, 500 in all), `callouts.ndjson` for async results. New `MOCKHUB_BIN_ALLOW_PRIVATE` (default off) so self-hosters, and the end-to-end test, can call private addresses.

### B6 — CRUD routes in the bin ✅ done
| Beeceptor (CRUD route) | Ours | Status |
|---|---|---|
| Entity path and id field | `crud: {path, idField, idType}` on a rule; ids 1, 2, 3…, UUID or short | ✅ |
| GET list, GET one, POST, PUT, PATCH, DELETE | Same, plus filters, sort, paging and `X-Total-Count` (the app's `CrudService`) | ✅ |
| Stored records and a data view | Per bin, kept across restarts, deleted with the bin; *Stored records* in the Rules dialog; `GET/PUT/DELETE /data` | ✅ |

Limits: 500 records of 8 KB, 20 collections per bin. `BinRecords` (a `DataStore` in the bin's folder), `BinCrudRoute`, `BinCrud`.

### B7 — Bin settings ✅ done
| Beeceptor (endpoint settings) | Ours | Status |
|---|---|---|
| CORS on every answer, preflight passes | Per-bin switch; preflight echoes the asked method and headers, answers expose every header | ✅ |
| Simulated rate limit (429) | Per bin, per second, minute or hour, with `Retry-After` | ✅ |
| Required security header | Name and value; others get 401 | ✅ |
| Default latency | Up to 15 s, unless a rule sets its own delay | ✅ |

Found while checking: before this, a bin's preflight allowed only `Content-Type, Authorization, X-Bin-Token` and no QUERY or TRACE, and browsers could read only two answer headers. `BinOptions`, `BinGate`, `PUT /options`.

### B8 — AI parity ✅ done
| Beeceptor | Ours | Status |
|---|---|---|
| AI chat that makes static, dynamic, CRUD and callout rules, several at once | One description drafts up to five rules of every kind; Edit, Add, Add all; rules failing checks are listed as left out | ✅ |
| **Fix template with AI** when a template fails | *Fix with AI* on a failed preview, in rule answers and the default answer; `POST /draft/fix` | ✅ in the bin; the app is P2 |

Drafts and fixes share the 10-an-hour AI limit and need a sign-in.

### B9 — Bin API parity ✅ done
| Beeceptor API v2 | Ours | Status |
|---|---|---|
| Rules: list, get, create, replace, patch, delete, reorder, bulk replace | `GET/POST /rules`, `GET/PUT/PATCH/DELETE /rules/{ruleId}`, `PUT /rules/order`, `PUT /rules` | ✅ |
| Requests: get, delete, multipart download | `GET/DELETE /requests/{seq}` (B4), `GET /requests/{seq}/parts[/{index}]` | ✅ |
| State: list, upsert, delete | `GET/DELETE /state`, `PUT/DELETE /state/{counters,values,lists}/{name}` | ✅ |
| CRUD data | `GET/PUT/DELETE /data` (B6) | ✅ |

Rule ids are now unique within a bin (a duplicate gets a new one) and `order` is reserved. Parts download as attachments with `Content-Security-Policy: sandbox`, so an uploaded HTML file can't run on the site. `BinRuleEdits`, `BinMultipart`, `BinState.put/remove`.

### B10 — Keep a bin ✅ done
Beeceptor keeps free endpoints 30 days, and 90 once claimed. Ours are deleted 24 hours after the last request. Build: **Save to my MockHub**, which turns a bin and its rules into a project for signed-in users. That also joins the free tool to the full app.

`POST /__public/inspect/{id}/project` with a MockHub sign-in copies the bin into a project: rules become stubs in a *From webhook bin* folder (name, order, description, off-switch kept), CRUD records are seeded, callouts and weighted answers carry over, and the default answer becomes the project's default response. What converts differently or not at all comes back as `notes` and is listed in the dialog and the docs. The project opens with the sign-in carried over, and the link stays under My bins. 10 saves an hour per user.

### B11 — Onboarding ✅ done
Beeceptor shows a "Get started" checklist: add a rule, test the endpoint, inspect requests, generate fake data. We have the waiting card with a test webhook.

The bin page now adds a **Get started** card for a new owner: catch a request (test webhook button), look inside a request, answer with a rule (opens Rules), keep it in MockHub (opens Save). Steps tick themselves off from what actually happens, are remembered in the browser, and the card hides when all are done or when hidden; view-only visitors never see it.

---

## 3. Free tools gaps (Tools and Mock Servers menus)

| Beeceptor tool | Ours | Status | Phase |
|---|---|---|---|
| OpenAPI mock server, no login, from a file **or a URL** | Instant mock from a file, paste or public URL | ✅ | T1 |
| SOAP mock from WSDL file or URL | Instant mock from a WSDL file, paste or public URL | ✅ | T1 |
| **GraphQL mock from SDL, no login**, with a query explorer | Instant mock from SDL, introspection or a URL, with an explorer of example queries | ✅ | T1 |
| **gRPC mock from .proto, no login** | Signed-in only, from protoc descriptor sets | ❌ | T7 |
| JSON/XML server: paste a payload, get a live API | `/tools/json-server/`: each key a route, lists of objects as CRUD | ✅ JSON; XML via the instant mock editor | T3 |
| CSV to REST API (upload, preview, read/add/update/delete) | `/tools/csv-to-api/` with a preview and a records table | ✅ | T3 |
| **2FA code generator** (QR screenshot or secret to TOTP codes, plus an API) | `/tools/totp/` and `GET|POST /__public/totp` | ✅ | T2 |
| Test data generator: Handlebars templates, a sample JSON to imitate, AI picks the locale | Fields, Template, Sample and AI tabs | ✅ | T4 |
| **Virtual sandboxes**: ready mock copies of DHL, FedEx, UPS, AWS S3, GitHub, Amadeus and others | GitHub, Stripe and Twilio sandboxes from their published OpenAPI specs | 🟡 the carriers, Amadeus and S3 aren't done | T5 |
| HTTP traffic monitoring page (wrap a domain, inspect traffic) | `/tools/traffic-monitor/`, a bin set to forward | ✅ | T6 |
| Local tunnel page | `/tools/local-tunnel/`, a bin set to tunnel, with the command and a connected badge | ✅ | T6 |
| HTTP echo | ✅ | ✅ | — |

---

### T1 — GraphQL and spec URLs without login ✅ done
`POST /__public/mocks` takes `{"from": "graphql", "text": "..."}` (SDL or an introspection result, up to 500 KB) and `"url"` in place of `text` for OpenAPI, WSDL and GraphQL; URLs are fetched with the same private-address guard as the signed-in "mock from a URL" (`MOCKHUB_PLAYGROUND_ALLOW_PRIVATE`), capped at 500 KB, after the create limit is taken. Instant mocks now allow the `mockhub-graphql` transformer on its own, with the schema checked and outside the 64 KB stub budget. `GET /__public/mocks/{slug}/graphql` lists example operations with ready queries and variables. The instant mock page has a URL field on the spec tab, a GraphQL tab and a GraphQL explorer; there is a new `/graphql-mock-server/` page. While testing, a site-wide bug where example code blocks made feature pages scroll sideways on phones was fixed.

### T2 — 2FA code generator ✅ done
`/tools/totp/` turns a Base32 secret, an `otpauth://totp/` link or a QR screenshot (chosen, or pasted with Ctrl/Cmd+V) into the current code with a countdown and the previous and next codes, all in the browser with Web Crypto. QR codes are read with the browser's BarcodeDetector where it has one, else with vendored jsQR 1.4.0 (Apache-2.0), loaded only when needed. **New test secret** makes a random one. `GET|POST /__public/totp` gives the same codes to scripts, with `format=text`, `time` and the previous and next codes; tested against the RFC 6238 vectors for SHA1, SHA256 and SHA512.

### T3 — JSON server and CSV to REST API ✅ done
`POST /__public/mocks` takes `{"from": "json", "data": {...}}`: each top-level key becomes a route, a list of objects becomes a CRUD resource with its records and anything else answers `GET`; bad route names are listed in `skipped`. Two tool pages sit on it: `/tools/json-server/` (paste a document, see the planned routes before creating) and `/tools/csv-to-api/` (upload, preview as a table). Both show every endpoint and a records table to list, add, change and delete records through the live API, with the last call and its status. Instant mocks now take up to 100 records to start (was 50), the same as fake data's largest set, and still keep up to 200. An XML payload is served by adding a stub in the instant mock editor; there is no separate XML page.

### T4 — Test data generator extras ✅ done
The fake data tool has four tabs. **Fields** as before. **Template** renders a Handlebars template once per record in one call to the public render sandbox (`{{#each (range 1 N)}}` with a record separator), so every helper and `locale=` on `fake` work, and each record must be JSON. **Sample** turns a pasted record into fields from its key names and value shapes (e-mails, URLs, UUIDs, dates, timestamps, numbers, booleans, nested objects). **AI**, for signed-in users on a server with an AI provider, sends a description to `POST /__public/fake/draft`, which returns fields, a count and a locale; every field is generated once before it is offered. While building it, the per-locale Faker cache, which any public caller could grow without limit by sending new locale tags, was capped at 32.

### T5 — Vendor sandboxes ✅ first three
Chosen with the user: build sandboxes only from published, openly licensed specs, so their paths and answers match the real APIs, instead of writing them from memory. `scripts/sandbox-specs.mjs` downloads GitHub's `rest-api-description`, Stripe's `openapi` and Twilio's `twilio-oai` (all MIT), keeps 12, 11 and 5 operations and the components they reference, and writes `sandbox-*.json` with the source, copyright and licence in `info`. Each becomes an example (`sandbox-github`, `sandbox-stripe`, `sandbox-twilio`) that the demo installs on deploy and instant mocks can start from. GitHub and Twilio answer with their own examples; Stripe's spec has none, so its answers follow the schemas with generated values. Fixed on the way: the OpenAPI importer served `{"$ref": ...}` as the body for named examples that point at `#/components/examples` (GitHub's spec does this everywhere). Not stateful: MockHub's CRUD routes are single-segment with a bare list, which matches neither GitHub's nested issue paths nor Stripe's list envelope. DHL, FedEx, UPS and Amadeus keep their specs behind sign-ups and AWS publishes Smithy models rather than OpenAPI, so they are left for later.

### T6 — Traffic monitor and local tunnel pages ✅ done
Two tool pages over the webhook bin. `/tools/traffic-monitor/` takes a public API (with an optional base path), creates a bin and sets it to forward there, and shows the watching URL, where an example call goes, a cURL line and a link to the live log. `/tools/local-tunnel/` takes a port or local address, creates a bin in tunnel mode, shows the `java -jar mockhub.jar tunnel` command with the bin's own key, and polls until the tunnel is connected. Both add the bin to **My bins**. Checked end to end with a real tunnel client reaching a local server.

### P1 — Template helpers ✅ done
Checked against Beeceptor's template docs (control flow, array, date, string, comparison and miscellaneous pages) and the live render endpoint. Already present: `eq`/`gt`/…, `contains`, `split`, `replace`, `trim`, `base64`, `urlEncode`, `slugify`, `stripTags`, `len`, `object`, `json`, `jsonMerge`, `isNumber`. Added, with Beeceptor's names, argument order and documented outputs as the tests: `switch`/`case`/`default` (the existing `default` value helper becomes the switch default when used as a block), `repeat`, `sort`, `reverse`, `oneOf`, `someOf`, `concat`, `objectMerge`, `coalesce`, `jsonParse`, `padStart`, `padEnd`, `urlDecode`, `base64Decode`, `lowercase`, `uppercase`, `isInteger`, `isDate`, `dateParse`, `dateAdd`, `dateDiff` (UTC, with Beeceptor's format shorthands and day.js patterns). They run in stubs, bins and the public playground, where `repeat` counts against the loop budget and `padStart`/`padEnd` against the size cap, and they are in the Insert dynamic value catalogue.

### P2 — Fix template with AI in the app ✅ done
A broken template used to come back from **Try it** as the server's raw HTML error page. Try it now reads the Handlebars message out of it (`templateError`), shows it in plain words and, for editors on a server with AI, offers **Fix with AI**: `POST /projects/{id}/ai/template/fix` sends the template, the error and the request that was tried, and the fix goes into the body to check and save. Bins and projects share one `TemplateFixer` (one prompt and set of checks; bins add their memory helpers, projects add state and dataset helpers).

### P3 — OpenTelemetry export ✅ done
`io.mockhub.otel` sends OTLP over HTTP with JSON (no SDK dependency): a server span per mock request (joining the caller's trace through `traceparent`, 5xx as errors), a log record per request, and cumulative `mockhub.requests` and `http.server.request.duration` metrics split by method, status, project and match. Configured only by the standard `OTEL_*` variables; off without an endpoint. `HistoryRecorder` hands each served request to a `ServedRequestListener`; the exporter queues up to 10,000, sends from a daemon thread, rate-limits warnings and flushes when the server stops (`MockHubExtensionFactory.serverStopped`, since WireMock's `stop()` doesn't stop extensions). Bodies and headers are not exported.

### P4 — Workspaces ✅ done
Beeceptor's workspaces (Enterprise) group endpoints with Read/Write/Manager members, an access policy of *Restricted* or *Organization read-only*, endpoints that move between workspaces, and deletion only when empty. MockHub's teams already had all of that except the policy: an organisation-wide role always applied to every team. Teams now have `restricted` (`TeamAccess` registry kept in sync by `TeamService`; `User.roleInTeam` drops the organisation and server role for restricted teams, except organisation managers and admins). Set under the team's Settings; the project dialog's hint and the team header say so.

### P5 — Header masking ✅ done
Beeceptor (Enterprise): org admins list headers that are masked wherever they're shown or stored, case-insensitively. MockHub: `Organization.maskedHeaders`, set by organisation managers in the org settings. `HistoryStore` masks before writing and on every read (search, detail, `each` for HAR/MCP), before the search filter so a masked value can't be found by searching; `RequestLogService` masks the in-memory log too. Requests recorded before a header was added stay on disk but are masked when shown. WireMock's admin-only `/__admin/requests` journal isn't masked.

### T7 — gRPC mocks without login
Split out of T1. Three things block it on the public demo: MockHub reads only compiled descriptor sets (no `.proto` text parser); gRPC routes by service name, so two visitors uploading the same service would collide on a shared server; and the demo's nginx has no HTTP/2 or `grpc_pass`. Needs a `.proto` parser, a per-mock service namespace, and HTTP/2 gRPC in MockHub's own nginx site file.

## 4. Signed-in platform gaps

| Beeceptor | Ours | Status | Phase |
|---|---|---|---|
| Template helpers `dateAdd`, `dateDiff`, `coalesce`, `objectMerge`, `switch`, `repeat`, `someOf`, sort by key, `reverse`, `isInteger`, `isDate`, `base64Decode`, `urlDecode`, `padStart`/`padEnd`, `concat` | All of them, with Beeceptor's names and argument order, plus `oneOf`, `jsonParse`, `dateParse`, `lowercase`, `uppercase` | ✅ | P1 |
| **Fix template with AI** | Try it shows template errors with **Fix with AI** (`POST /projects/{id}/ai/template/fix`) | ✅ | P2 |
| **OpenTelemetry (OTLP) export** of traffic, logs and metrics | Traces, logs and metrics over OTLP/HTTP JSON from the standard `OTEL_*` variables | ✅ | P3 |
| **Workspaces** (group projects inside an organisation) | Teams: members with roles, projects that move between teams, delete only when empty, and now restricted teams | ✅ | P4 |
| **Header masking** in request history | Per organisation, applied on write and on every read | ✅ | P5 |
| QUERY method in stubs | ❔ | ❔ | P6 |
| gRPC client, server and bidi streaming; GraphQL subscriptions | ❔ | ❔ | P6 |
| Published performance benchmark (load, payload sizes, 8-hour soak) | — | ❌ | P7 |
| **Import a Postman collection** into stubs | Export to Postman only | ❌ | P8 |

---

## 5. Website gaps (header menus and footer)

Every page under Beeceptor's three header menus was read, plus the footer.

| Beeceptor page | Ours | Status | Phase |
|---|---|---|---|
| AI for API Virtualization | `/ai/` | ✅ | — |
| Service Virtualization | `/service-virtualization/` | ✅ | — |
| Performance Testing | `/performance-testing/` | ✅ | — |
| Development Bottlenecks (why mock servers) | `/contract-first/`, `/test-environments/` cover parts | 🟡 | W1 |
| Testing Limitations (API virtualization for CI, flaky tests, failure simulation) | Spread over pages | 🟡 | W1 |
| OpenAPI, SOAP mock server pages | ✅ | ✅ | — |
| **GraphQL mock server page** | `/graphql-mock-server/` | ✅ | T1 |
| **gRPC mock server page** | — | ❌ | W2 (with T7) |
| CRUD REST APIs | `/crud-api/` | ✅ | — |
| JSON/XML server | `/mock-api/` | ✅ page, 🟡 paste flow (T3) | T3 |
| Ready-to-use mock servers | `/examples/`, 16 projects including 3 vendor sandboxes | 🟡 | T5 |
| **CSV to REST API page** | — | ❌ | T3 |
| **2FA code generator page** | — | ❌ | T2 |
| HTTP echo, webhook testing, generate test data | ✅ | ✅ | — |
| **Local tunnel page** | Docs only | ❌ | T6 |
| **HTTP traffic monitoring page** | — | ❌ | T6 |
| Forward proxy page | Docs only | 🟡 | W2 |
| Compare pages: vs WireMock, vs Postman | ✅ (plus vs Mockoon) | ✅ | — |
| Compare: vs Broadcom DevTest, vs Mockbin, vs Apidog, **vs Beeceptor** | — | ❌ | W3 |
| **Status page** and footer "All systems operational" badge | — | ❌ | W4 |
| **Changelog** page | — | ❌ | W5 |
| **Trust centre** (security and compliance) | — | ❌ | W5 |
| **FAQ** page | FAQs live on each page | 🟡 | W5 |
| About and contact | ❔ | ❔ | W5 |

W4 status page: a public `/status/` page and a footer badge. The badge reads the live demo's health (`/__admin/health`), the website, and the public tools (create a bin, echo). It shows "All systems operational", "Degraded" or "Down" with the last check time. It keeps a short uptime history from a small checker that runs on the VPS, writes a JSON file, and is served as a static file. It only claims what it measures.

---

## 5. Order

| # | Phase | Size | Why here |
|---|---|---|---|
| 1 | B1 Matching parity | M | Most visible gap in the bin editor |
| 2 | B2 Response editor parity | S | Same editor, small items |
| 3 | B3 Dynamic values in the bin | M | Shared picker also helps the app |
| 4 | B4 Request log parity | M | Daily-use features |
| 5 | B7 Bin settings | S | Small, self-contained |
| 6 | B5 Callout rules | M | Reuses forward and the app's callout code |
| 7 | B6 CRUD routes | M | Reuses the app's CRUD engine |
| 8 | B9 Bin API parity | S | Follows the new rule kinds |
| 9 | B8 AI parity | M | Needs B5 and B6 to draft every rule kind |
| 10 | B10 Save to my MockHub | M | Joins the bin to the app |
| 11 | B11 Onboarding | S | Polish |
| 12 | T1 GraphQL and spec URLs without login ✅ | M | Importers exist, free path missing |
| 13 | T2 2FA code generator ✅ | M | New tool |
| 14 | T3 Paste a payload, CSV to API page ✅ | S | Mostly pages over existing features |
| 15 | T4 Test data generator extras ✅ | M | |
| 16 | T5 Virtual sandboxes catalog ✅ first three | L | Content heavy |
| 17 | T6 Traffic monitoring and tunnel pages ✅ | S | Pages |
| 17b | T7 gRPC mocks without login | L | Split out of T1: needs a .proto parser, per-mock service names and HTTP/2 on the demo |
| 18 | P1 Template helpers ✅ | S | Verify, then add the missing ones |
| 19 | P2 Fix template with AI ✅ | S | |
| 20 | P3 OpenTelemetry export ✅ | M | |
| 21 | P4 Workspaces ✅ | M | |
| 22 | P5 Header masking ✅ | S | |
| 23 | P6 Verify QUERY, streaming, subscriptions | S–M | |
| 24 | P7 Benchmark report | M | |
| 25 | P8 Postman collection import | S | |
| 26 | W4 Status page and footer badge | S | Asked for; cheap trust signal |
| 27 | W2 gRPC and forward-proxy pages | S | The GraphQL page shipped with T1; the gRPC page waits for T7 |
| 28 | W1 Development bottlenecks and testing limitations pages | S | Content |
| 29 | W3 More compare pages | S | Content, facts checked |
| 30 | W5 Changelog, trust centre, FAQ, about | S | Content |

Every ❔ is checked at the start of its phase. If it turns out to work already, it is marked ✅ here with a test that proves it, and nothing is built.
