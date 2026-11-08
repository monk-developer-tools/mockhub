# Plan: a clear signed-in journey

## Why

After signing in, MockHub shows a project list, or for a new user, "Create your first mock project" with an empty
dialog and a list of examples. Everything MockHub can start from is there, but hidden:

| What a user wants | Where it is today | Steps |
|---|---|---|
| Mock from an OpenAPI file | New project → open it → Import / export tab → choose OpenAPI → upload | 5+ |
| Mock from a WSDL, GraphQL schema or gRPC `.proto` | Same tab, a different format, gRPC in a separate panel lower down | 5+ |
| A CRUD API with records | New project → Data tab → New resource | 4 |
| A stub from a JSON or XML answer | New project → Stubs → New stub → form | 4+ |
| Describe an API and let AI build it | New project → Assistant tab → write a message | 3 |
| Ready-made examples and vendor sandboxes | Sidebar → Examples | 2 |
| Mock an OpenAPI URL | Sidebar → "Mock an OpenAPI URL" (its own page, the one flow that is already direct) | 2 |

Beeceptor's header menu has one page per kind of mock (OpenAPI, SOAP, GraphQL, gRPC, CRUD, JSON/XML, ready-made), a separate Webhook Testing page,
and each page leads with one card: give it the file or payload, press **Create mock server**, and you land on a working
mock. Every page was reviewed on 8 November 2026. MockHub can do all of it, plus AI drafting, but the user has to know
the order of tabs.

## Goal

From the moment someone signs in, they can see every way to start and reach a working, callable mock in one screen,
with no detours through empty projects and tabs.

## Design

### 1. A "New mock" page with one card per way to start (`/new`)

A grid of start cards, the same set as the website's Mock Servers menu, so the site and the app speak the same language:

| Card | Input | What it creates |
|---|---|---|
| OpenAPI or Swagger | File, URL or paste | One stub per operation, example responses; optional prefix strip and contract |
| SOAP from WSDL | File or paste | One stub per operation, the `?wsdl` endpoint, a fault for unknown operations |
| GraphQL | SDL or introspection file, or paste | A `/graphql` endpoint answering every query and mutation |
| gRPC | `.proto` file or paste | The services, plus one starter stub per method |
| CRUD API | Resource name and records (JSON or CSV), or none | A saved REST resource: list, get, create, update, delete |
| From a JSON or XML answer | Payload, method, path, status | One stub answering with the payload |
| Webhooks | *Receive*: a path; *send*: the endpoint to mock, the URL to call back, a delay | *Receive*: a catch-all receiver that answers at once and records every webhook. *Send*: a stub that answers, then calls your URL with a body built from the request |
| Describe it to AI | A sentence or two | The assistant drafts the stubs for review |
| Postman collection | File | One stub per saved example |
| Ready-made | — | Opens the examples and vendor sandboxes |
| Empty project | — | The current project dialog |

The AI card shows only when AI is set up; otherwise it says that an admin can turn it on under AI settings.

### 1b. The same three groups as the website menus

The page has three sections, matching Beeceptor's (and MockHub's website's) header menus, so nothing someone saw on the
site is missing once they sign in:

**Tools** (each one either creates something or opens the place it lives):

| Card | In the app |
|---|---|
| CSV to REST API | The CRUD card with the CSV tab open |
| Webhook testing | The Webhooks card, *receive* |
| HTTP traffic monitoring | New create screen: a project whose catch-all stub forwards every call to your service (an `https://` URL, or a `tunnel://` name for a local service) and records it in Requests |
| HTTP echo | New create screen: an endpoint that answers with the request it received |
| Local tunnel | The traffic-monitoring screen with a `tunnel://` target, and the command to run |
| Generate test data | The test data generator |
| 2FA code generator | The 2FA tool |

**Service virtualization** (goal cards that lead to the right place, with a line on what it does):

| Card | Leads to |
|---|---|
| AI for API virtualization | The *Describe it to AI* card |
| Performance and chaos testing | The Chaos example, and the latency, failures and rate limits in a project's runtime settings |
| Test environments for teams | Teams & people |
| Contract checks | Contract drift docs and a project's Contract tab |
| API tests in CI | Access tokens and the API docs |

### 2. One screen per card (`/new/:kind`)

Every create screen has the same shape:

1. **The input** (upload, URL or paste, with a ready sample to try in one click).
2. **Where it goes:** *a new project* (name filled in from the file or title, team picked) for managers, or *an
   existing project* the user can edit. Users who can do neither are told who can give them access.
3. **Create mock**, then straight to the result. Errors from the importer show inline with the line or field.

### 3. A "ready" view after creating

The project overview opens with a **Your mock is ready** panel: the base URL with a copy button, the first few
endpoints with a working *Try it* and a cURL command (grpcurl for gRPC, a query for GraphQL), and next steps: change an
answer, add a delay or failure, watch requests, invite the team. It can be closed and doesn't come back.

### 4. Make it findable everywhere

- A primary **New mock** button at the top of the sidebar, always visible to editors and managers.
- The dashboard: new users see the start cards instead of an empty box; returning users get a slim "Start something
  new" row of the cards above the project list.
- The command palette gets "New mock from OpenAPI…", "…from WSDL", and so on.
- "Mock an OpenAPI URL" folds into the OpenAPI card (URL tab); its old route keeps working, including the
  `?openapi=` links behind the "Mock these APIs" badge.
- A project's **Import / export** tab keeps working for adding to an existing project.

### 5. Roles

| Role | Sees |
|---|---|
| Viewer | Projects to browse; the New mock page explains that creating needs the editor role and who grants it |
| Editor | Every card, with *existing project* as the target |
| Manager / admin | Every card, *new project* by default |

## Phases

| # | Phase | Ships |
|---|---|---|
| J1 ✅ | Start page and entry points | `/new` with the cards, sidebar button, dashboard start row and empty state, command palette entries |
| J2 ✅ | Create screens | `/new/:kind` for OpenAPI (file, URL, paste), WSDL, GraphQL, gRPC, CRUD, JSON/XML answer, webhooks (receive and send), traffic monitoring (proxy and record, including tunnels), echo, Postman, AI, with new or existing project |
| J3 ✅ | Ready panel | The overview's first-run panel with base URL, endpoints, Try it, commands and next steps |
| J4 ✅ | Website and docs | A "Start a mock" docs page and tutorial with screenshots; feature pages link to the matching card in the app |

Each phase ships with unit, component and browser tests, docs, a deploy and a live check, like the earlier plans.

## Not changing

- Projects stay the unit of organisation; a mock is always a project or part of one.
- The public no-login tools stay as they are; this plan is about the signed-in app.
