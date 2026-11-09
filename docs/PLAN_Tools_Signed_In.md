# Plan: the same tools signed in, with more

## Why

The free tools on the website look and work better than their signed-in versions. The webhook catcher shows each
request beside the answer it got, with *Send test*, *Make a rule from it*, *Copy as cURL* and *Share*. The signed-in
webhook mock is a general project whose Requests tab is a table, so after signing in people lose the view they liked.
The same gap exists for the traffic monitor, HTTP echo, the template playground and fake data.

## Goal

Every free tool has a signed-in screen with the same layout, words and steps, so moving from the website to an account
feels like the same product. Signed in adds what an account allows: longer history and search, the team, the stub
editor, the AI assistant, callouts, snapshots and Git.

## Mapping

| Free tool | Signed-in screen | Same as the free tool | Extra when signed in |
| --- | --- | --- | --- |
| Webhook catcher | **Inspect** tab of a webhook project | URL card with Copy, Answers, Routing, Settings, Share; Get started steps; requests beside *Request and answer*; Send test; Copy as cURL; Make a stub from it | Search days of history, HAR export, callouts, stub editor, AI assistant, team, snapshots, Git |
| Traffic monitor | **Inspect** tab of a proxy project | requests beside the answer the real API gave | Record as stubs, delays and faults, contract drift |
| HTTP echo | **Inspect** tab of an echo project | the echoed request beside the call | everything above |
| Local tunnel | **Inspect** tab of a tunnel project, with the tunnel command | same as webhook | tunnels for any stub, connections |
| Template playground | **Tools → Template playground** | template, sample request, rendered answer | your datasets and JWT keys, *Save as a stub* into a project |
| Fake data | **Tools → Fake data** | fields, count, locale, preview, download | *Save as a CRUD resource* or *Save as a dataset* |
| Instant mock, JSON server, CSV to API | New mock screens (already there) | the *You get* list before creating | stub editor, CRUD data tab |
| JWT, TOTP, cURL to stub, Stub checker | **Tools** page links to them | — | cURL import and stub checks are built into the stub editor |

## Design

### Inspect tab

- Shown first for projects made from the Webhook, Proxy, Echo or Tunnel cards, and offered on every project.
- Top card: project name, mock URL with Copy, and buttons **Answers** (stubs), **Routing** (proxy and tunnel),
  **Settings** (runtime), **Share** (copy a link for the team), **AI** (assistant).
- **Get started** steps like the free tool: catch a request, look inside one, answer with a stub, invite the team.
  Hidden once done or dismissed, per project.
- Left: live request list with search, method and status filters, *Send test* and *Clear*.
- Right: **Request and answer** for the selected request: method, path, time, headers, query, body (pretty JSON),
  and the answer with status, headers, body, stub and time. Buttons: *Copy as cURL*, *Make a stub from it*, *Replay*,
  *Copy link*. *Make a stub from it* opens the stub editor filled from the request.
- Uses the existing history API, so it shows days of history, not only the latest 100.

### Tools page

- A **Tools** item in the sidebar opens a page with every tool, the same groups and icons as the website menu.
- Template playground and Fake data run in the app against this server, so they work on self-hosted MockHub too.

## Phases

- ✅ **S1 — Inspect tab:** component, route, default for webhook, proxy, echo and tunnel projects; Send test; Make a stub
  from it; tests at all three levels.
- ✅ **S2 — Tools page:** sidebar item, Template playground and Fake data screens with their save actions.
- ✅ **S3 — Wording and docs:** the same names on the website tools and the app; each tool page says what signing in adds.
