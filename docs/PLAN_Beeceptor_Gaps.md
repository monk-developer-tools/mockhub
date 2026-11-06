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

### B1 — Request matching parity
| Beeceptor | Ours | Status |
|---|---|---|
| Methods GET, POST, PUT, PATCH, DELETE, **QUERY**, OPTIONS, **TRACE**, Any | No QUERY, no TRACE (we have HEAD) | ❌ |
| Path: exactly, starts with, contains, **doesn't contain**, **matches template `/users/:id`**, regex | No "doesn't contain", no path template | ❌ |
| Query parameter: equals, **not equals**, contains, **doesn't contain**, regex, **is absent**, is present | No not-equals, doesn't-contain or absent | ❌ |
| Body parameter (JSON, XML **or form data**): equals, not equals, contains, doesn't contain, **is null**, **is not null** | JSON path only, no form or XML fields, no null tests | 🟡 |
| Body contains, body regex | ✅ | ✅ |
| Header regex | ✅ (and more ops) | ✅ |
| **SOAP action matches** | — | ❌ |
| **GraphQL query (operation) matches** | — | ❌ |

Build: add `QUERY`/`TRACE`, negative operators (`not_equals`, `not_contains`, `absent`) for every target, `is_null`/`not_null` for body fields, a `template` path operator that also fills `pathParam`, `form` and `xml` (XPath) targets, `soap_action` and `graphql_operation` targets.

### B2 — Response editor parity
| Beeceptor | Ours | Status |
|---|---|---|
| **Set Content-Type** picker (JSON, XML, HTML, JavaScript, CSV, text) | Free text header | ❌ |
| **Send a file/blob** as the body | Text only | ❌ |
| `{}` pretty-print the body | — | ❌ |
| **Rule description** (Additional information) | Name only | ❌ |
| Weighted answers with a **description** and **percent** shown | Weights, no description, no % | 🟡 |
| Single vs weighted switch | Add answers one by one | 🟡 |

### B3 — Dynamic values in the bin
| Beeceptor | Ours | Status |
|---|---|---|
| Picker grouped into 13 categories: request components, control flow, data manipulation, JWT, stateful, comparison, string, arithmetic, date, array, validation, response components, Faker (300+) | Flat picker with 10 items | 🟡 |
| Stateful helpers per endpoint (step-counter, data-store, list) | Not in bin templates | ❌ ❔ |
| `pathParam`, `cookie` | — | ❌ |

Build: a grouped, searchable picker (shared with the app's picker), and per-bin state for the stateful helpers. First check which helpers the bin's template sandbox allows today ❔.

### B4 — Request log parity
| Beeceptor | Ours | Status |
|---|---|---|
| Pretty-print toggle per body | Always formatted | 🟡 |
| Copy body | — | ❌ |
| Copy as cURL | ✅ | ✅ |
| **Share one request and its response** as a permanent link | Whole-bin view link only | ❌ |
| "A rule matched" icon that opens the rule | Rule name as text | 🟡 |
| Make a rule from a captured request | — | ❌ |
| Search and filter by path, method, status, event | — | ❌ |
| Delete one request; download a multipart file from a request | Clear all only | ❌ |
| Export the log (JSON or HAR) | — | ❌ |

### B5 — Callout rules in the bin
Beeceptor "New Callout Rule": either wait for the target (synchronous) or answer at once with a mock and call out in the background (asynchronous). It has a method that can be "same as original", a target, the original or a custom (templated) payload, authentication, and a minimum and maximum trigger delay. We only have forward at routing level. ❌

### B6 — CRUD routes in the bin
Beeceptor "New CRUD Route": an entity path and an id field give GET list, GET one, POST, PUT, PATCH and DELETE, with stored records and a data view. ❌

### B7 — Bin settings
| Beeceptor (endpoint settings) | Ours | Status |
|---|---|---|
| CORS on every answer, preflight passes | ❔ to verify on bins | ❔ |
| Simulated rate limit (429) | — | ❌ |
| Required security header | — | ❌ |
| Default latency | — | ❌ |

### B8 — AI parity
| Beeceptor | Ours | Status |
|---|---|---|
| AI chat that makes static, dynamic, CRUD and callout rules, several at once | One rule per draft | 🟡 |
| **Fix template with AI** when a template fails | — | ❌ (also missing in the app) |

### B9 — Bin API parity
Beeceptor API v2 has rules one by one (get, create, replace, patch, delete, reorder, bulk replace), request history one by one (get, delete, multipart download) and the state store (list, upsert, delete). We only have PUT for all rules and GET for a page of requests. ❌

### B10 — Keep a bin
Beeceptor keeps free endpoints 30 days, and 90 once claimed. Ours are deleted 24 hours after the last request. Build: **Save to my MockHub**, which turns a bin and its rules into a project for signed-in users. That also joins the free tool to the full app. ❌

### B11 — Onboarding
Beeceptor shows a "Get started" checklist: add a rule, test the endpoint, inspect requests, generate fake data. We have the waiting card with a test webhook. 🟡

---

## 3. Free tools gaps (Tools and Mock Servers menus)

| Beeceptor tool | Ours | Status | Phase |
|---|---|---|---|
| OpenAPI mock server, no login, from a file **or a URL** | Instant mock from a file | 🟡 URL import ❔ | T1 |
| SOAP mock from WSDL file or URL | Instant mock from WSDL | 🟡 URL ❔ | T1 |
| **GraphQL mock from SDL, no login**, with a query explorer | Signed-in only | ❌ | T1 |
| **gRPC mock from .proto, no login** | Signed-in only | ❌ | T1 |
| JSON/XML server: paste a payload, get a live API | ❔ instant mock starts from stubs or records | 🟡 | T3 |
| CSV to REST API (upload, preview, read/add/update/delete) | Instant mock from CSV records | 🟡 needs its own page and CRUD check | T3 |
| **2FA code generator** (QR screenshot or secret to TOTP codes, plus an API) | — | ❌ | T2 |
| Test data generator: Handlebars templates, a sample JSON to imitate, AI picks the locale | Fields and locale picker | 🟡 | T4 |
| **Virtual sandboxes**: ready mock copies of DHL, FedEx, UPS, AWS S3, GitHub, Amadeus and others | 10 MockHub examples | 🟡 | T5 |
| HTTP traffic monitoring page (wrap a domain, inspect traffic) | Bin forward does it, no page | 🟡 | T6 |
| Local tunnel page | Tunnel docs; bin tunnel | 🟡 | T6 |
| HTTP echo | ✅ | ✅ | — |

---

## 4. Signed-in platform gaps

| Beeceptor | Ours | Status | Phase |
|---|---|---|---|
| Template helpers `dateAdd`, `dateDiff`, `coalesce`, `objectMerge`, `switch`, `repeat`, `someOf`, sort by key, `reverse`, `isInteger`, `isDate`, `base64Decode`, `urlDecode`, `padStart`/`padEnd`, `concat` | Some exist under other names (`jsonMerge`, `ljust`/`rjust`, `join`, `pickRandom`) | 🟡 ❔ | P1 |
| **Fix template with AI** | — | ❌ | P2 |
| **OpenTelemetry (OTLP) export** of traffic, logs and metrics | — | ❌ | P3 |
| **Workspaces** (group projects inside an organisation) | — | ❌ | P4 |
| **Header masking** in request history | ❔ | ❔ | P5 |
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
| **GraphQL mock server page** | — | ❌ | W2 (with T1) |
| **gRPC mock server page** | — | ❌ | W2 (with T1) |
| CRUD REST APIs | `/crud-api/` | ✅ | — |
| JSON/XML server | `/mock-api/` | ✅ page, 🟡 paste flow (T3) | T3 |
| Ready-to-use mock servers | `/examples/` | 🟡 smaller catalog (T5) | T5 |
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
| 12 | T1 GraphQL, gRPC and spec URLs without login | M | Importers exist, free path missing |
| 13 | T2 2FA code generator | M | New tool |
| 14 | T3 Paste a payload, CSV to API page | S | Mostly pages over existing features |
| 15 | T4 Test data generator extras | M | |
| 16 | T5 Virtual sandboxes catalog | L | Content heavy |
| 17 | T6 Traffic monitoring and tunnel pages | S | Pages |
| 18 | P1 Template helpers | S | Verify, then add the missing ones |
| 19 | P2 Fix template with AI | S | |
| 20 | P3 OpenTelemetry export | M | |
| 21 | P4 Workspaces | M | |
| 22 | P5 Header masking | S | |
| 23 | P6 Verify QUERY, streaming, subscriptions | S–M | |
| 24 | P7 Benchmark report | M | |
| 25 | P8 Postman collection import | S | |
| 26 | W4 Status page and footer badge | S | Asked for; cheap trust signal |
| 27 | W2 GraphQL, gRPC and forward-proxy pages | S | Ships with T1 |
| 28 | W1 Development bottlenecks and testing limitations pages | S | Content |
| 29 | W3 More compare pages | S | Content, facts checked |
| 30 | W5 Changelog, trust centre, FAQ, about | S | Content |

Every ❔ is checked at the start of its phase. If it turns out to work already, it is marked ✅ here with a test that proves it, and nothing is built.
