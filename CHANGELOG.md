# Changelog

What changed in MockHub, newest first. Every change ships to the Docker image `abhaypratap/mockhub:latest` and the public demo the same day.

## 2026-11-08

- UPS (tracking, rating, address validation, transit times) and Amadeus (airport search, flight offers, pricing, orders) sandboxes built from their published specs.
- Fixed: OpenAPI imports left deeply nested fields empty (null); every field now gets a sample value.
- gRPC mocks from a `.proto` file without an account, on the public demo over TLS; projects accept `.proto` uploads too.
- Fixed: HTTPS through the forward proxy didn't work in the Docker image, because WireMock can't create certificates on Java 21. The image now runs on Java 17.
- gRPC server reflection is off when the public tools are on, so instant mock ids can't be listed.
- CVE-2026-84243 in glibc is marked not affected, with an OpenVEX statement attached to each image.
- Fixed: an imported path whose first segment equals the project's base path, such as `/orders/{id}` in project `/orders`, kept that segment again instead of losing it.
- Fixed: WireMock's request journal was counted on every request; small requests went from about 8,400 to 47,000 a second in our benchmark.
- Fixed: large responses could fill the memory through the request journal or the history write queue. Both are now capped (`MOCKHUB_JOURNAL_MAX_BODY_KB`, `MOCKHUB_JOURNAL_MAX_MB`).
- Fixed: request history could grow far past `MOCKHUB_HISTORY_MAX_MB` under heavy traffic. It now rolls into small files and keeps the cap while it writes.
- A published performance benchmark, with `scripts/benchmark.sh` to repeat it on your hardware.
- A status page for the public demo, with a badge in the website footer.
- Website pages on the forward proxy, development bottlenecks and API testing in CI, and comparisons with Beeceptor, Apidog, Mockbin and Broadcom DevTest.

## 2026-11-07

- Import Postman collections: each saved example becomes a stub.
- Masked headers: organisation managers list headers that are hidden in every request view and never written to disk.
- Restricted teams: only their members, organisation managers and admins see their projects.
- OpenTelemetry export of traces, logs and metrics for mock traffic.
- Fix a broken response template with AI from the stub editor, and template errors shown plainly in Try it.
- More template helpers: switch, repeat, sort, someOf, coalesce, padStart, dateAdd, dateDiff and others.
- QUERY in the stub editor and OpenAPI 3.2 imports; GraphQL subscriptions get a clear error.
- GitHub, Stripe and Twilio sandboxes built from their published OpenAPI specs.
- Free no-login tools: GraphQL mocks with an explorer, JSON server, CSV to REST API, fake data, a 2FA code generator, a traffic monitor and a local tunnel.
- Save a webhook bin and its rules as a MockHub project.

## 2026-11-06

- Webhook bin: rules with rich matching, weighted and file answers, callouts, CRUD routes, CORS, rate limits, request search, HAR export, sharing and AI rule drafts.

## 2026-11-05

- gRPC mocks from protobuf descriptor sets.
- SOAP without a WSDL: import sample requests and answer any operation.
- HTTPS with mutual TLS, per project.
- Custom domains: a project answers at the root of its own host names.
- Forward proxy with HTTPS: mock matching calls, record the rest.
- Local tunnel targets for proxy stubs and callouts.
- Contract drift detection against an OpenAPI document.
- Mock an OpenAPI URL, with a "Mock these APIs" badge for API docs.
- An echo response type and an echo example.

## 2026-11-04

- Project snapshots: compare, restore, pull between projects, download and upload.
- GraphQL mocks from a schema.
- Organisation datasets and connections with encrypted OAuth2 and API key secrets.
- CRUD resources: filters, sorting, nested fields and paging; saved state per project.
- JWT, value and validation template helpers, and weighted responses.
- Free no-login tools: instant mock server, webhook bins, echo, template playground, fake data, JWT tools, cURL to stub and a stub JSON checker.
- Apache-2.0 licence.

## 2026-11-03

- Searchable request history, live traffic charts and HAR export.
- Personal access tokens and an MCP server for coding agents.
- Per-project runtime behaviour: default response, rate limits, latency, CORS and IP allow list.
- Webhooks, HTTP callouts and an Insert dynamic value picker.
- A multi-page documentation site.

## 2026-10-28

- Audit log, manager role and admin-only secrets.
- Import a WireMock folder or take over a whole WireMock repository.
- Root projects that answer without a base path.
- Ten example projects that cover every feature.

## 2026-10-17

- Stubs can be switched on and off, with usage counts, random failures and per-project API keys.
- A playground for sending requests, and a data generator.
- Hugging Face as an AI assistant provider.

## 2026-10-16

- Teams inside organisations with per-team roles.
- Selective backup and restore.
- CRUD resources, realistic fake data in templates and OpenAPI imports, webhooks and response files.
- A distroless Docker image without OpenSSL.

## 2026-10-15

- Organisations, user management and Microsoft Entra ID single sign-on.
- An AI assistant with OpenRouter, LiteLLM and OpenAI-compatible providers.

## 2026-10-13

- First release: projects on WireMock with a web UI, stub editor, request log, scenarios, import and export, persistence, sign-in, example projects and a Docker image.
