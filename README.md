# MockHub

A team mock server for REST and SOAP services, built on [WireMock](https://wiremock.org). Developers and testers
group mocks into **projects**. Each project gets its own URL, has a web UI for editing stubs, and exports to
**Postman** and **Bruno**. Everything is saved to disk, so mocks survive restarts and redeploys.

It ships as one JAR inside one small Docker image (about 115 MB, non-root, distroless), ready for AWS ECS.

```
https://mocks.acme.internal/__admin/mockhub/ui/          the UI
https://mocks.acme.internal/payment-service/...          mocks of project "Payment service"
https://mocks.acme.internal/legacy-crm/ws/customers      SOAP mocks of project "Legacy CRM"
```

## Features

- **Projects with their own base URL.** Point a service's config at `…/payment-service` instead of the real dependency.
- **Every WireMock feature.** URL, query, header, cookie and body matching (JSON, JSONPath, XML, XPath, regex),
  response templating, scenarios (stateful mocks), delays, faults, proxying and recording. The form editor covers
  the common cases, and the JSON editor takes anything WireMock accepts.
- **SOAP support.** A SOAP mode in the editor (operation matching, SOAP 1.1/1.2, envelope and fault helpers), plus
  **WSDL import**. Import creates one stub per operation with sample envelopes generated from the schema, serves
  `?wsdl` pointing at the mock, and adds a fault stub for operations that aren't mocked.
- **Import** OpenAPI 3 / Swagger 2 (JSON or YAML), WSDL, or WireMock mappings.
- **Export** a Postman v2.1 collection (with a status test per request), a Bruno collection, or WireMock mappings.
  A single `baseUrl` variable switches between the mock and the real service.
- **Try it.** Send a request to a stub from the editor and see the response.
- **Live request log** per project, with near misses for unmatched requests and *create stub from request*.
- **Built-in examples:** REST basics, templating, stateful cart, faults and delays, and a SOAP calculator from WSDL.
  Install one with a click and copy what you need. The editor also has a template gallery.
- **Persistence.** Every stub, including ones created through the native `/__admin` API, is written to `/data`.
  There's also full backup and restore.
- **Optional sign-in** with `admin`, `editor` and `viewer` roles. Mock endpoints always stay open.

## Quick start

```bash
docker run -p 8080:8080 -v mockhub-data:/data abhaypratap/mockhub
```

Open <http://localhost:8080/__admin/mockhub/ui/>, go to **Examples**, and install *Petstore — REST basics*. Then:

```bash
curl http://localhost:8080/example-petstore/pets/42
```

With Docker Compose: `docker compose up`.

## Configuration

All configuration is through environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port for UI, API and mocks |
| `MOCKHUB_DATA_DIR` | `/data` in Docker, `./mockhub-data` otherwise | Where projects, stubs and body files are stored. Mount a volume (EFS on ECS). |
| `MOCKHUB_PUBLIC_URL` | taken from the request | The URL people use to reach MockHub, e.g. `https://mocks.acme.internal`. Used in exports. |
| `MOCKHUB_USERS` | empty, so sign-in is off | `user:password:role` entries separated by commas. Roles: `admin`, `editor`, `viewer`. A password can be `sha256:<hex>`. |
| `MOCKHUB_LOGIN_NOTE` | empty | Text shown on the sign-in page, e.g. demo credentials |
| `MOCKHUB_JOURNAL_MAX_ENTRIES` | `5000` | How many requests the request log keeps in memory |
| `MOCKHUB_STUB_CORS` | `true` | Adds CORS headers to mock responses so browser apps can call them |
| `WIREMOCK_OPTIONS` | empty | Any extra [WireMock CLI option](https://wiremock.org/docs/standalone/java-jar/), e.g. `--async-response-enabled true --verbose` |

Example with sign-in:

```bash
docker run -p 8080:8080 -v mockhub-data:/data \
  -e MOCKHUB_USERS="lead:s3cret:admin,qa:qa-pass:editor,dev:dev-pass:viewer" \
  -e MOCKHUB_PUBLIC_URL=https://mocks.acme.internal \
  abhaypratap/mockhub
```

| Role | Can |
|---|---|
| viewer | see projects, stubs and request logs, and download exports |
| editor | everything a viewer can, plus create, edit and delete projects and stubs, import, record |
| admin | everything, plus global settings, restore, reset and shutdown through the native API |

## How it works

MockHub is a WireMock extension running inside a regular WireMock server:

- Mocks are plain WireMock stubs. A project is a base path plus a tag (`metadata.mockhub.projectId`) on its stubs.
  The UI edits paths relative to the project. A stub listener marks every stub persistent, so WireMock writes it to
  `/data/mappings`.
- Projects are stored in `/data/mockhub/projects.json`.
- The UI is served from the JAR at `/__admin/mockhub/ui/`, and the MockHub API at `/__admin/mockhub/api/`. The
  native WireMock admin API stays available at `/__admin/`.

### MockHub API

| Method | Path | |
|---|---|---|
| GET/POST | `/__admin/mockhub/api/projects` | list / create projects |
| GET/PUT/DELETE | `/__admin/mockhub/api/projects/{id}` | read / update / delete a project (and its stubs) |
| GET/POST | `/__admin/mockhub/api/projects/{id}/stubs` | list / create stubs (project-relative URLs) |
| GET/PUT/DELETE | `/__admin/mockhub/api/projects/{id}/stubs/{stubId}` | read / update / delete a stub |
| POST | `/__admin/mockhub/api/projects/{id}/stubs/{stubId}/clone?target=` | copy a stub |
| POST | `/__admin/mockhub/api/projects/{id}/stubs/{stubId}/move?target=` | move a stub to another project |
| GET/DELETE | `/__admin/mockhub/api/projects/{id}/requests?unmatched=&limit=` | request log |
| GET | `/__admin/mockhub/api/projects/{id}/export/{postman,bruno,wiremock}` | download an export |
| POST | `/__admin/mockhub/api/projects/{id}/import/{openapi,wsdl,wiremock}` | import a document (request body) |
| GET/POST | `/__admin/mockhub/api/examples`, `/examples/{id}/install` | built-in examples |
| GET | `/__admin/mockhub/api/stubs/unassigned` | stubs not in any project |
| POST | `/__admin/mockhub/api/projects/{id}/adopt/{stubId}?prefix=` | put such a stub into a project |
| GET/POST | `/__admin/mockhub/api/backup`, `/restore` | everything as one JSON file |

Example: a CI job that loads mocks before running tests.

```bash
curl -X POST "$MOCKHUB/__admin/mockhub/api/projects/$PROJECT/import/openapi" --data-binary @openapi.yaml
```

## Deploying to AWS ECS

`deploy/terraform` is a tested Terraform module for Fargate (ARM64 or x86), an EFS volume for `/data`, and an ALB
with optional HTTPS. See [docs/DEPLOYMENT-AWS.md](docs/DEPLOYMENT-AWS.md).

```bash
cd deploy/terraform && cp terraform.tfvars.example terraform.tfvars && terraform init && terraform apply
```

## Development

Requirements: JDK 17+ and Node 20+.

```bash
make build          # UI + fat JAR + all tests -> build/libs/mockhub.jar
make run            # run it on :8080 with ./mockhub-data
make ui-dev         # Vite dev server on :5173, proxying the API to :8080
make test           # Java unit, integration and system tests + UI tests
make docker         # build abhaypratap/mockhub locally
```

Gradle tasks: `unitTest`, `integrationTest` (in-process server), `systemTest` (runs the packaged JAR as a process,
including a restart), `uiTest`, and `test` for all the Java levels.

### Code layout

```
src/main/java/io/mockhub
  Main, Config, HealthCheck      entry points and env configuration
  ext/                           WireMock extensions: factory (composition root), admin API, auth, persistence
  api/                           tiny router, request context, JSON helpers
  controller/                    one controller per resource
  project/                       projects: model, repository port + file implementation, service, listener
  stub/                          project stubs: gateway port to WireMock, path re-rooting, metadata
  journal/                       request log per project
  importer/ export/ soap/        import and export strategies, WSDL/XSD sampling
  example/                       bundled example catalog and installer
ui/src
  api/ model/ state/             API client, stub form model, app state
  components/ pages/             UI
```

### Releasing

Pushes to `main` publish `abhaypratap/mockhub:latest`. A tag `vX.Y.Z` publishes `X.Y.Z` and `X.Y`. Images are
multi-arch (amd64 and arm64). The pipeline needs two repository secrets: `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`
(a Docker Hub access token with read and write access).

## Limits

- Run **one** task per data directory. WireMock keeps stubs in memory and only reads the files at startup, so two
  replicas would drift apart. One task handles thousands of requests per second, which is plenty for test environments.
- Scenario states and the request log live in memory and reset on restart.
