# Plan: environments per project

## Why

Teams run the same mock for dev, qa and staging, but each needs different values (base URLs, tenant ids, feature flags)
and sometimes a different answer (qa returns the test card decline, dev always approves). Today that means copying
the project, and copies drift.

## Goal

A project has named environments, each with its own variables. Stubs read variables with `{{env 'name'}}` and can be
limited to some environments. One environment is active; a caller can pick another per request with a header.

## Design

### Model

- Per project: `active` and a list of environments `{name, variables}`. Names are lowercase letters, digits and
  dashes, up to 30 characters; at most 10 environments and 100 variables each, 4 KB per value.
- A project without environments behaves exactly as today.
- Stored next to the project in `mockhub/environments.json`.

### At request time

- A request filter works out the **effective environment**: the `X-MockHub-Environment` header when it names one of
  the project's environments, otherwise the active one. It writes that name back into the same header, so stubs,
  templates, the request history and the Inspect tab all see it.
- `{{env 'baseUrl'}}` gives the variable's value in the effective environment, `{{env}}` gives the environment's name.
  An unknown variable renders as empty.
- A stub limited to environments matches the header with a WireMock header matcher:
  `"headers": {"X-MockHub-Environment": {"matches": "qa|staging"}}`. Because it is plain WireMock, snapshots, Git,
  export and import carry it unchanged.

### API

| Call | Does | Role |
| --- | --- | --- |
| `GET /projects/{id}/environments` | `{active, environments: [{name, variables}]}` | viewer |
| `PUT /projects/{id}/environments` | replace the list and the active one | editor |
| `PUT /projects/{id}/environments/active` | `{name}`: switch | editor |

### UI

- **Settings → Environments:** add, rename and remove environments, edit variables in a table, pick the active one.
- **Project header:** the active environment as a switch, when the project has environments.
- **Stub editor:** *Only in environments* checkboxes, which write the header matcher.
- **Inspect and Requests:** the environment of each request is visible from the header.

### Agents

- MCP `get_environments`, `set_environments`, `switch_environment`; the stub reference explains `{{env}}` and the
  header matcher.
- Not yet: environments in snapshots and Git sync (the stubs that use them already are).

## Phases

- ✅ **E1 — Server:** store, service, filter, `env` helper, API, access rules; unit, integration and system tests.
- ✅ **E2 — UI:** settings panel, header switch, stub editor field.
- ✅ **E3 — Agents and docs:** MCP tools, stub reference, docs page, changelog.
