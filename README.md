# MockHub

A team mock server for REST and SOAP services, built on [WireMock](https://wiremock.org). Developers and testers
group mocks into **projects**. Each project gets its own URL, has a web UI for editing stubs, and exports to
**Postman** and **Bruno**. Everything is saved to disk, so mocks survive restarts and redeploys.

It ships as one JAR inside one small Docker image (about 115 MB, non-root, distroless), ready for AWS ECS.

New here? The [step-by-step tutorials](https://mockhub.devops-monk.com/tutorials/) walk through every feature with
screenshots, and the [live demo](https://mockhub.devops-monk.com/__admin/mockhub/ui/) runs the example projects.

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
  Admins can back up everything, whole organisations or single projects to one file, and later restore all of it
  or just the projects they pick, replacing existing ones or adding copies, into the original or another organisation.
- **AI assistant.** Connect OpenRouter, a LiteLLM proxy or any OpenAI-compatible API, then describe the mocks you
  need in plain English or paste a spec. The assistant stages stubs, and you review each change as a diff before
  applying it.
- **Organisations and teams.** Group projects into organisations. Owners add people as viewers, editors or owners,
  and each person sees only their organisations.
- **Users and single sign-on.** Create users in the UI, or let people sign in with **Microsoft Entra ID** (Azure AD).
  Entra groups and app roles can map to organisation roles. Mock endpoints always stay open.

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
| `MOCKHUB_USERS` | empty | Optional break-glass accounts: `user:password:role` entries separated by commas. Roles: `admin`, `editor`, `viewer` (server-wide). A password can be `sha256:<hex>`. |
| `MOCKHUB_AI_API_KEY` | empty | API key for the AI provider. Overrides the one saved in the UI. |
| `MOCKHUB_SSO_CLIENT_SECRET` | empty | Entra client secret. Overrides the one saved in the UI, so it can come from a secret store. |
| `MOCKHUB_LOGIN_NOTE` | empty | Text shown on the sign-in page, e.g. demo credentials |
| `MOCKHUB_JOURNAL_MAX_ENTRIES` | `5000` | How many requests the request log keeps in memory |
| `MOCKHUB_STUB_CORS` | `true` | Adds CORS headers to mock responses so browser apps can call them |
| `WIREMOCK_OPTIONS` | empty | Any extra [WireMock CLI option](https://wiremock.org/docs/standalone/java-jar/), e.g. `--async-response-enabled true --verbose` |

## Organisations, users and sign-in

Sign-in is off until there is a user. While it's off, anyone who can reach MockHub can change everything. To turn it
on, either open **Users** in the UI and create the first admin, or start with a break-glass admin:

```bash
docker run -p 8080:8080 -v mockhub-data:/data \
  -e MOCKHUB_USERS="root:change-me:admin" \
  -e MOCKHUB_PUBLIC_URL=https://mocks.acme.internal \
  abhaypratap/mockhub
```

**Organisations** hold projects. On first start, MockHub creates one called *Default* and moves existing projects
into it. Admins create more from the organisation switcher at the top of the sidebar. Mock URLs don't change when a
project moves between organisations, and base paths stay unique across the server.

| Organisation role | Can |
|---|---|
| viewer | see the organisation's projects, stubs and request logs, and download exports |
| editor | also create, edit and delete projects and stubs, import, and record |
| owner | also rename the organisation, add and remove members, and delete it |

Each user also has a **server role**. With *viewer everywhere* or *editor everywhere*, a user has that role in every
organisation. With *admin*, a user also manages users, single sign-on, backups and the native WireMock admin API.
Users from `MOCKHUB_USERS` keep the role given there. Use them as a break-glass account.

The UI keeps a session token for 12 hours. Scripts can keep using HTTP Basic auth with a username and password.
Changing a password or disabling a user signs out that user's other sessions.

### Single sign-on with Microsoft Entra ID

1. In the [Entra admin centre](https://entra.microsoft.com), go to **App registrations → New registration**. Choose
   **Web** as the redirect URI type and paste the redirect URI from **MockHub → Single sign-on**:
   `https://<your MockHub>/__admin/mockhub/api/auth/sso/azure/callback`.
2. Copy the **Application (client) ID** and the **Directory (tenant) ID**.
3. Under **Certificates & secrets**, create a client secret and copy its value.
4. Optional: to use groups, add a **groups claim** under **Token configuration**, or define **App roles**.
5. In MockHub, paste the three values and choose **Test connection**. Then pick:
   - the allowed e-mail domains
   - the organisation and role every new user gets
   - mappings from group object IDs or app role values to organisation roles or a server role

   Turn it on and save.

The login page then shows **Sign in with Microsoft**. MockHub uses the authorisation code flow with PKCE. It checks
the ID token's signature against the tenant's keys, and checks the issuer, audience, expiry and nonce. People are
created on their first sign-in. Roles that come from groups are refreshed at every sign-in. Roles you set by hand
in the UI are kept.

Set `MOCKHUB_PUBLIC_URL` when MockHub runs behind a proxy, so the redirect URI matches what you registered. Use
**Also allow MockHub passwords** to make SSO the only way in for everyone except admins.

## AI assistant

An admin connects a model under **Administration → AI assistant**:

| Provider | Base URL | Notes |
|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1` (default) | One key for hundreds of models. `openrouter/auto` picks one, or name a model that supports tool calling. |
| LiteLLM proxy | e.g. `http://litellm:4000` | Uses your proxy's virtual keys, budgets, logging and approved models. The model is the proxy's `model_name`. |
| Other OpenAI-compatible | e.g. `https://api.openai.com/v1` | OpenAI, Azure OpenAI, Ollama, vLLM, LM Studio and others. |

Then, in any project, open **Assistant** and say what you need. For example:

- *Payments API: POST /payments creates a payment, GET /payments/{id} returns it, 404 for unknown ids*
- *Create stubs for the requests that matched nothing*
- *Make every POST answer after 2 seconds*
- *Add a SOAP GetCustomer operation*

You can also paste an OpenAPI spec, a WSDL or curl commands. The new-project dialog has a **Describe the API** box
that starts the conversation for you.

The assistant uses tool calling. It reads the project's stubs and recent requests, and it **stages** creates,
updates and deletes. Each staged change appears as a diff. You apply or discard each one, so nothing changes until
someone with the editor role applies it. Viewers can draft changes but can't apply them. Request log contents are
treated as data, not instructions.

The conversation, the project's stubs and, when the assistant asks for them, recent requests are sent to the
provider you configure. Choose one your organisation allows, or run models yourself behind LiteLLM.

## How it works

MockHub is a WireMock extension running inside a regular WireMock server:

- Mocks are plain WireMock stubs. A project is a base path plus a tag (`metadata.mockhub.projectId`) on its stubs.
  The UI edits paths relative to the project. A stub listener marks every stub persistent, so WireMock writes it to
  `/data/mappings`.
- Projects, organisations, users and SSO settings are stored as JSON in `/data/mockhub/`. Passwords are PBKDF2
  hashes. `secret.key` signs session tokens, so keep it, and back up `/data` as a whole.
- The UI is served from the JAR at `/__admin/mockhub/ui/`, and the MockHub API at `/__admin/mockhub/api/`. The
  native WireMock admin API stays available at `/__admin/`.

### MockHub API

| Method | Path | |
|---|---|---|
| POST | `/__admin/mockhub/api/auth/login` | `{username, password}` → session token |
| GET | `/__admin/mockhub/api/auth/me` | the signed-in user and their organisations |
| GET/POST | `/__admin/mockhub/api/orgs` | list / create organisations |
| GET/PUT/DELETE | `/__admin/mockhub/api/orgs/{id}` | read / rename / delete an organisation (and its projects) |
| GET | `/__admin/mockhub/api/orgs/{id}/members` | members with their roles |
| PUT/DELETE | `/__admin/mockhub/api/orgs/{id}/members/{userId}` | add or change (`{role}`) / remove a member |
| GET/POST | `/__admin/mockhub/api/users` | list / create users (admin) |
| PUT/DELETE | `/__admin/mockhub/api/users/{id}` | update / delete a user; `POST …/password` resets it |
| GET/PUT | `/__admin/mockhub/api/sso` | single sign-on settings (admin); `POST /sso/test` checks the tenant |
| GET/POST | `/__admin/mockhub/api/projects?orgId=` | list / create projects (`orgId` in the body) |
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
| POST | `/__admin/mockhub/api/projects/{id}/ai/chat` | `{messages, pending}` → reply plus staged changes (waits for the model) |
| POST | `/__admin/mockhub/api/projects/{id}/ai/jobs` | Same body; starts the chat in the background and returns a job id |
| GET | `/__admin/mockhub/api/projects/{id}/ai/jobs/{jobId}` | Job status, live steps, then the reply or error (kept 30 minutes, visible to whoever started it) |
| POST | `/__admin/mockhub/api/projects/{id}/ai/apply` | `{changes}` → apply staged changes (editor) |
| GET/PUT | `/__admin/mockhub/api/ai/settings` | AI provider settings (admin); `POST /ai/test` lists models |
| GET | `/__admin/mockhub/api/backup` | everything as one JSON file (admin) |
| POST | `/__admin/mockhub/api/backup` | `{organizations:[ids], projects:[ids]}` or `{everything:true}` → backup of just that (admin) |
| POST | `/__admin/mockhub/api/restore/preview` | backup file → organisations and projects inside, and what each would do here (admin) |
| POST | `/__admin/mockhub/api/restore` | `{backup, organizations, projects, targetOrgId?, mode: replace\|copy}`, or a bare backup file to restore all of it (admin) |

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
  org/ user/                     organisations and users: models, repository ports + file implementations, services
  auth/                          roles, principal, session tokens, access policy
  sso/                           single sign-on: provider strategy (Entra ID), OIDC token check, role mapping
  ai/                            AI assistant: provider strategy (OpenRouter, LiteLLM, OpenAI-compatible), tool loop
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

## Security updates

- The image is rebuilt every Monday, and on every push to `main`, from the latest distroless base, so operating
  system fixes such as OpenSSL arrive without a code change.
- WireMock runs on Jetty 12 through `org.wiremock:wiremock-jetty12`, because Jetty 11 no longer gets fixes.
- `build.gradle.kts` pins patched versions of libraries that WireMock pulls in (Jackson, Apache HttpComponents,
  Handlebars, commons-lang3). Only the core Handlebars jar is upgraded: `handlebars-helpers` stays on 4.3.1, because
  newer versions moved classes that WireMock 3 loads.

## Limits

- Run **one** task per data directory. WireMock keeps stubs in memory and only reads the files at startup, so two
  replicas would drift apart. One task handles thousands of requests per second, which is plenty for test environments.
- Scenario states and the request log live in memory and reset on restart.
