# Plan: Git sync

## Why

Mocks change as often as the code that calls them, yet they live only inside MockHub. Teams want to review a mock
change in a pull request, see who changed what in `git log`, keep mocks next to the service they belong to, and set up
a fresh MockHub from a repository.

## Goal

A project can be linked to a folder in a Git repository. **Push** writes the project there as readable files and makes a
commit; **Pull** shows what the repository would change and then applies it, with a safety snapshot first.

## Design

### Files in the repository

```
<folder>/                       default mockhub/<project slug>
  mockhub.json                  format, project name, runtime behaviour, API key header, stub order
  mappings/<name>-<id>.json     one WireMock stub per file, pretty printed
  __files/<name>                response files as they are
```

- One stub per file keeps diffs small and reviewable. File names come from the stub name, or method and path, plus the
  first 8 characters of its id, so renaming a stub renames one file.
- `mockhub.json` keeps the matching order; stubs it doesn't list are added after, sorted by file name.
- Pull also reads a plain WireMock folder with no `mockhub.json`: each file in `mappings/` holds one stub or
  `{"mappings": [...]}`.
- Push replaces `mockhub.json`, `mappings/` and `__files/` in the folder, so deleted stubs disappear from the repository too. Nothing else is touched, so the folder can be the root of a service repository.

### Connection

- An HTTPS repository URL, a branch (default `main`) and an access token with write access. A username is optional;
  GitHub and GitLab tokens work with any.
- The token is encrypted with the server's secret key, like connections, and never returned by the API.
- Private and local hosts are refused unless `MOCKHUB_GIT_ALLOW_PRIVATE=true` (which also allows `file:` repositories for tests and air-gapped setups).
- A branch that doesn't exist yet is created on the first push.

### Roles

- Linking, changing or unlinking the repository: manager of the project's team.
- Push and Pull: editor. Viewers see the link and the last push and pull.

### API

| Call | Does |
| --- | --- |
| `GET /projects/{id}/git` | the link without the token, last push and pull |
| `PUT /projects/{id}/git` | `{url, branch, folder, username, token}`; a missing token keeps the stored one |
| `DELETE /projects/{id}/git` | unlink |
| `POST /projects/{id}/git/push` | `{message}`; commits as the signed-in person and returns the commit |
| `GET /projects/{id}/git/preview` | what Pull would change, in the snapshot compare format |
| `POST /projects/{id}/git/pull` | applies the repository, after a safety snapshot |

## Phases

- ✅ **G1 — Server:** layout (files ↔ snapshot), JGit remote, settings store, service, controller, access rule; unit,
  integration (a local bare repository) and system tests.
- ✅ **G2 — UI:** a Git panel on the Snapshots tab: link form, Push with a message, Pull with the diff preview.
- ✅ **G3 — Agents and docs:** MCP `git_push`, `git_preview`, `git_pull`; a docs page; changelog.

Later, if asked for: push automatically after each change, and pull on a repository webhook.
