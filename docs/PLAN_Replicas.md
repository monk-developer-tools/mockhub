# Options: more than one MockHub server

Status: proposal for a decision. Nothing here is built yet.

## The question

Today MockHub runs as one server. On AWS that is one ECS task (`desired_count = 1`, `deployment_maximum_percent = 100`)
with its data folder on EFS; on the VPS it is one Docker container. Should it run as several, and how?

There are two different reasons to want more than one, and they lead to different answers:

1. **Staying up.** A deploy, a crash or a host failure takes mocks offline. ECS replaces the task, but with
   `deployment_maximum_percent = 100` every deploy stops the old task before the new one starts, so CI pipelines that
   hit mocks during a deploy fail until the new task has started and passed its health checks.
2. **More capacity.** One server answered about 47,000 small requests a second in our benchmark
   (see the Performance docs page). That covers load tests from a few machines and every CI and test environment we know of.
   Capacity is not today's limit.

## What lives where today

| State | Where it lives | Shared by two servers on one data folder? |
| --- | --- | --- |
| Stubs | WireMock memory, saved as mapping files | No: each server loads them at start and never rereads |
| Projects, users, teams, orgs, tokens, connections, datasets, environments, Git links, staged changes, AI and SSO settings | JSON files, cached in memory per store (about 25 stores) | No: a write on one server is invisible to the other until restart, and two servers writing the same file lose updates |
| CRUD records, response files, gRPC descriptors, snapshots, contracts, bins | Files per project or bin | Partly: reads see new files, but caches and concurrent writes are not safe |
| Request history | NDJSON segments written by one writer thread | No: two writers would interleave or overwrite segments |
| Scenario state, stub state (`state` helpers), rate-limit windows, traffic counters, request journal | Memory only | No: each server has its own |
| Tunnels, MCP sessions, AI jobs, playground calls in flight | Memory and open connections | No: a call must reach the server that holds the tunnel or session |

So two servers on the same EFS folder today would silently diverge and corrupt files. Any option has to deal with that.

## Options

### A. One server, made to restart faster and deploy without a gap

Keep a single writer. Close the deploy gap with a **leader lease**: a new task starts as a standby, loads everything,
and waits for a lock file on EFS; the old task releases it on shutdown (or it expires after a missed heartbeat); the
standby takes the lock, reloads anything written since it started, and only then reports healthy to the load balancer.
Set `deployment_maximum_percent = 200` so both tasks overlap during a deploy.

- **Gives:** deploys with a pause of a few seconds instead of a full restart, and automatic takeover when a task dies.
- **Keeps:** every feature exactly as it is; one jar and one data folder.
- **Does not give:** more capacity.
- **Effort:** small. A lease class, a "standby" health state, a reload on takeover, Terraform changes, tests that kill
  the leader.
- **Risk:** low. EFS supports the locks Java uses; the lease adds a timeout as a second guard.

### B. Shared files plus change notices

Several active servers share the EFS folder. Every write goes through the stores as now, then bumps a change counter
file per store; each server polls the counters every second and reloads stores and stubs that changed.

- **Gives:** more capacity for read-heavy mock traffic and no single point of failure.
- **Costs:** writes become eventually consistent (about a second); two servers writing the same JSON store at once
  still lose an update unless every store gets file locking; request history needs a writer per server and merged
  reads; rate limits, scenarios, stub state and the request journal stay per server, so a 10-per-minute limit becomes
  10 per server and a stateful flow breaks unless the load balancer keeps a client on one server; tunnels and MCP
  sessions need sticky routing.
- **Effort:** large. Locking and reload in about 25 stores, per-server history, sticky sessions, many race tests.
- **Risk:** high. The failure mode is silent: lost updates and flows that work locally and fail in CI.

### C. One writer, several read-only servers

One **control** server takes the UI, the API, MCP, the AI assistant and every write. Several **serving** servers only
answer mock traffic; they load stubs and settings from the shared folder and reload when the control server bumps a
counter. The load balancer sends `/__admin/*` to the control server and everything else to the serving pool.

- **Gives:** capacity for mock traffic and a clean single writer for configuration.
- **Costs:** anything that writes while serving a mock breaks or must be sent to the control server: CRUD resources,
  stub `state`, scenarios, webhook bins, the request history, rate limits, callout logs and tunnels. That is a large
  part of what makes MockHub more than WireMock, so in practice most traffic would have to go to the control server
  anyway.
- **Effort:** large, and it splits features into "works on serving servers" and "control only", which users must learn.
- **Risk:** medium.

### D. A shared database

Move every store, the history, state, rate limits and scenarios to PostgreSQL (and change notices through
`LISTEN/NOTIFY`), so any number of servers stay consistent.

- **Gives:** real horizontal scaling and correct shared state.
- **Costs:** rewrites the storage of every feature; adds a database to run, back up and upgrade; ends the "one jar and
  a data folder" setup that makes MockHub easy to self-host, unless both storage modes are kept, which doubles the
  testing.
- **Effort:** very large.
- **Risk:** medium, spread over a long time.

## Side by side

| | A. Leader lease | B. Shared files | C. One writer, read pool | D. Database |
| --- | --- | --- | --- | --- |
| No gap on deploy | Yes (seconds) | Yes | Yes | Yes |
| Survives a lost server | Yes, after a few seconds | Yes | Serving: yes; control: no | Yes |
| More capacity | No | Yes | For stateless stubs | Yes |
| Every feature keeps working | Yes | Only with sticky routing | No | Yes |
| Self-hosting stays one jar and a folder | Yes | Yes | Mostly | No |
| Effort | Small | Large | Large | Very large |
| Risk | Low | High | Medium | Medium |

## Recommendation

Build **A** now. The real problem is the gap during deploys and after a crash, not capacity, and A fixes that without
changing how any feature behaves or how people run MockHub.

Revisit capacity only when someone needs more than one server can answer. If that day comes, prefer **D** over B and C:
it is the only option where every feature stays correct, and by then the effort is justified by a real need.
Meanwhile, the documented answer for very large load tests is to run a MockHub per load generator, each pulled from
the same Git folder with Git sync.

## If A is chosen: phases

- **R1 — Lease:** a lock file with a heartbeat on the data folder, a standby state, reload on takeover, health
  endpoint reports standby as not ready; unit tests with a fake clock, integration tests with two servers on one
  folder, a system test that kills the leader.
- **R2 — Deploy:** Terraform `deployment_maximum_percent = 200`, health check on readiness, a deploy drill in CI or on
  a test stack; the VPS keeps one container.
- **R3 — Docs:** high availability in the deployment docs, with what to expect during a deploy and a failover.

## Questions for you

1. Is the main pain the gap during deploys and crashes, or do you have a load that one server can't answer?
2. Is AWS (ECS with EFS) the setup that matters, or also Kubernetes? A works on both; Kubernetes needs a
   `ReadWriteMany` volume.
3. Is adding PostgreSQL acceptable for self-hosters in the long run, or must one jar and a folder always be enough?
