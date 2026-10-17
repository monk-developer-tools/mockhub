<p align="center">
  <img src="https://mockhub.devops-monk.com/assets/img/logo.svg" width="72" height="72" alt="MockHub">
</p>

<h1 align="center">MockHub</h1>

<p align="center">
  <strong>Every dependency. Mocked in minutes.</strong><br>
  A team mock server for REST and SOAP, built on WireMock 3, with a web UI, an AI assistant and one small image.
</p>

<p align="center">
  <a href="https://mockhub.devops-monk.com/__admin/mockhub/ui/"><strong>Live demo</strong></a> ·
  <a href="https://mockhub.devops-monk.com/tutorials/">Tutorials</a> ·
  <a href="https://mockhub.devops-monk.com/docs/">Docs</a> ·
  <a href="https://github.com/monk-developer-tools/mockhub">GitHub</a>
</p>

![The MockHub stub editor](https://mockhub.devops-monk.com/assets/img/dockerhub-editor.jpg)

## Quick start

```bash
docker run -d --name mockhub -p 8080:8080 -v mockhub-data:/data abhaypratap/mockhub
```

Open **http://localhost:8080/__admin/mockhub/ui/**, create a project such as *Payments* (base path `payments`), add a
stub, and call it:

```bash
curl http://localhost:8080/payments/payments/42
```

Point your service or tests at `http://<host>:8080/<base path>` instead of the real dependency.

## What you get

- **One URL per service.** Each project lives under its own base path, so one server mocks many services.
- **A real UI.** Form and JSON stub editor, *Try it* inside the editor, a live request log with near misses, and
  *create stub from request*.
- **Every WireMock feature.** URL, header, query, cookie and body matching (JSONPath, XPath, regex), response
  templating, stateful scenarios, delays, faults, proxying and recording.
- **SOAP.** Import a WSDL for one stub per operation, `?wsdl` served, faults for unknown operations.
- **Import and export.** OpenAPI 3, Swagger 2, WSDL and WireMock mappings in; Postman and Bruno collections out.
- **AI assistant.** Bring your own model through OpenRouter, a LiteLLM proxy or any OpenAI-compatible API. It drafts
  stubs, and you review the diff before anything changes.
- **Teams.** Organisation › teams › people, with viewer, editor and owner roles, and Microsoft Entra ID sign-in.
- **CRUD resources.** `/customers` with list, read, create, update and delete backed by real stored records, editable
  in a Data tab.
- **Realistic fake data.** `{{fake 'Name.fullName'}}` and hundreds more, in many languages.
- **Webhooks and response files.** Call other services after answering; serve PDFs, images or large JSON.
- **Real-world conditions.** Delays, network faults, random failures (say 10% of calls), and API keys.
- **On, off and usage.** Pause a stub without deleting it, and see how often each one is called.
- **Snippets** in cURL, HTTPie, JavaScript, Python, Java, C# and Apex.
- **Nothing gets lost.** Every stub is saved to `/data`, and backups cover the whole server or single projects.

![A project overview](https://mockhub.devops-monk.com/assets/img/dockerhub-overview.jpg)

## Docker Compose

```yaml
services:
  mockhub:
    image: abhaypratap/mockhub
    ports: ["8080:8080"]
    volumes: ["mockhub-data:/data"]
    environment:
      MOCKHUB_PUBLIC_URL: https://mocks.acme.internal
      MOCKHUB_USERS: "root:change-me:admin"
    restart: unless-stopped
volumes:
  mockhub-data:
```

## Configuration

Everything is set with environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Port for the UI, the API and the mocks |
| `MOCKHUB_DATA_DIR` | `/data` | Where stubs, projects, teams and settings are stored. Mount a volume here. |
| `MOCKHUB_PUBLIC_URL` | from the request | The URL people use to reach MockHub; used in exports and snippets |
| `MOCKHUB_USERS` | empty | Break-glass accounts, `user:password:role` separated by commas (`admin`, `editor`, `viewer`) |
| `MOCKHUB_AI_API_KEY` | empty | Key for the AI provider; overrides the one saved in the UI |
| `MOCKHUB_SSO_CLIENT_SECRET` | empty | Entra ID client secret; overrides the one saved in the UI |
| `MOCKHUB_LOGIN_NOTE` | empty | Text shown on the sign-in page |
| `MOCKHUB_JOURNAL_MAX_ENTRIES` | `5000` | How many requests the request log keeps |
| `MOCKHUB_STUB_CORS` | `true` | CORS headers on mock responses, for browser apps |
| `MOCKHUB_FAKE_LOCALE` | `en` | Default language for `{{fake ...}}` data |
| `WIREMOCK_OPTIONS` | empty | Extra WireMock options, e.g. `--async-response-enabled true` |

Sign-in is off until the first user exists. Create an admin in the UI, or set `MOCKHUB_USERS`. Mock endpoints never
need a password; only managing them does.

## Image details

| | |
|---|---|
| Base | `gcr.io/distroless/base-nossl-debian12`, with a trimmed Java 21 runtime and no shell, package manager or OpenSSL |
| User | non-root, uid `65532` |
| Port | `8080` |
| Volume | `/data` |
| Health check | built in; also `GET /__admin/health` |
| Platforms | `linux/amd64`, `linux/arm64` |
| Updates | rebuilt on every change and every Monday, so base-image security fixes arrive automatically |

## Tags

| Tag | Meaning |
|---|---|
| `latest` | The newest build from `main` |
| `sha-<commit>` | A specific build, for pinning |
| `<version>` | Released versions, when tagged |

## Deploying

Run it anywhere Docker runs. For AWS, the repository has a Terraform module for ECS Fargate with EFS persistence and an
HTTPS load balancer: see [Deploying to AWS ECS](https://github.com/monk-developer-tools/mockhub#deploying-to-aws-ecs).

![The AI assistant reviewing staged stubs](https://mockhub.devops-monk.com/assets/img/dockerhub-assistant.jpg)

---

MockHub is built on [WireMock](https://wiremock.org) and is not affiliated with WireMock Inc.
Source, issues and docs: [github.com/monk-developer-tools/mockhub](https://github.com/monk-developer-tools/mockhub).
