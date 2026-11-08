# Plan: set up anything by asking the AI

## Why

The assistant and the MCP server could only change stubs, runtime behaviour, CRUD records and snapshots.
Most ways to start a mock (an OpenAPI or WSDL file, a GraphQL schema, a `.proto`, a CRUD resource, a contract)
still needed the UI, so "mock this OpenAPI URL and watch for drift" could not be done in one message.

## Goal

Everything a person sets up for a project can be asked for in plain words, in the Assistant tab or from a coding agent over MCP.
Every change still goes through review: the assistant stages it, a person applies it.
Team admin (members, roles, tokens, SSO) and connection secrets stay out of the AI's reach on purpose.

## Design

| Ask | Assistant tool | MCP tool | Staged as |
| --- | --- | --- | --- |
| Mock an OpenAPI, Swagger, WSDL, GraphQL, Postman or WireMock document, pasted or by URL | `import_spec` | `import_spec` | one New change per stub |
| Keep the OpenAPI document as the contract to watch drift | `import_spec` with `keep_contract`, `attach_contract` | same | Contract |
| Serve a gRPC service from a `.proto` | `add_grpc_service` | `add_grpc_service` | gRPC |
| A REST resource that stores records | `create_crud_resource` | `create_crud_resource` (already there) | CRUD |
| See drift against the contract | `get_drift` | `get_drift`, `check_drift` | read only |
| A new project | — | `create_project` | direct |
| Rename, describe, custom domains, API key | — | `update_project` | direct; the API key stays admin only |
| Datasets shared across projects | — | `list_datasets`, `save_dataset` | direct |

- Imports are parsed when they are staged, so a broken document fails at once with the parser's message.
- At most 200 stubs per import from the AI; bigger documents go through the Import dialog.
- A gRPC `.proto` is compiled when staged, so syntax errors show before review.
- A review-only MCP token stages the new project-level changes too; direct-only tools tell the agent to use a direct token.
- Webhook bins need no sign-in and stay on the Webhook testing page; inside a project, a webhook receiver is a stub the assistant already writes.

## Phases

- ✅ **A1 — Assistant tools:** `import_spec`, `attach_contract`, `add_grpc_service`, `create_crud_resource`, `get_drift`;
  new staged kinds `contract`, `grpc`, `crud`; change cards in the Assistant tab; system prompt. Tests at all three levels.
- ✅ **A2 — MCP tools:** the same tools through the stager, plus `create_project`, `update_project`, `list_datasets`, `save_dataset`, `check_drift`.
- ✅ **A3 — Docs:** AI assistant and MCP pages list every tool; changelog.
