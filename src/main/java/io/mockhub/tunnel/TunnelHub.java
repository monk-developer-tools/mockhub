package io.mockhub.tunnel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.playground.Header;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

public final class TunnelHub implements TunnelGateway {
  public static final int MAX_BODY = 5 * 1024 * 1024;
  public static final int MAX_IN_FLIGHT = 20;
  public static final int MAX_PER_USER = 5;
  public static final Duration POLL_WAIT = Duration.ofSeconds(25);
  public static final Duration ONLINE_GRACE = Duration.ofSeconds(45);
  private static final Pattern NAME = Pattern.compile("[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?");

  private final Map<String, Tunnel> tunnels = new ConcurrentHashMap<>();
  private final BiPredicate<Principal, String> mayServeProject;
  private final ScheduledExecutorService scheduler;
  private final Clock clock;

  public TunnelHub(BiPredicate<Principal, String> mayServeProject, ScheduledExecutorService scheduler, Clock clock) {
    this.mayServeProject = mayServeProject;
    this.scheduler = scheduler;
    this.clock = clock;
  }

  public CompletableFuture<TunnelMessages.Request> next(String name, Principal principal) {
    Tunnel tunnel = open(name, principal);
    CompletableFuture<TunnelMessages.Request> waiter = new CompletableFuture<>();
    synchronized (tunnel) {
      tunnel.lastPoll = Instant.now(clock);
      TunnelMessages.Request queued = tunnel.queue.poll();
      if (queued != null) {
        waiter.complete(queued);
        return waiter;
      }
      tunnel.waiters.add(waiter);
    }
    scheduler.schedule(() -> {
      if (waiter.complete(null)) {
        synchronized (tunnel) {
          tunnel.waiters.remove(waiter);
          tunnel.lastPoll = Instant.now(clock);
        }
      }
    }, POLL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    return waiter;
  }

  public void respond(String name, Principal principal, String id, TunnelMessages.Response response) {
    Tunnel tunnel = owned(name, principal);
    if (response.body().length > MAX_BODY) throw new TunnelException(413, "Answers through a tunnel can be up to 5 MB");
    CompletableFuture<TunnelMessages.Response> pending = tunnel.pending.remove(id);
    if (pending == null) throw new TunnelException(404, "No request " + id + " is waiting on tunnel " + name);
    pending.complete(response);
  }

  @Override
  public TunnelMessages.Response send(String projectId, URI uri, String method, List<Header> headers, byte[] body, Duration timeout) {
    String name = uri.getHost() == null ? uri.getAuthority() : uri.getHost();
    Tunnel tunnel = name == null ? null : tunnels.get(name.toLowerCase());
    if (tunnel == null || tunnel.closed || !online(tunnel)) throw new TunnelException(502, "Tunnel " + name + " isn't connected. Start mockhub tunnel on the machine that serves it");
    if (projectId == null || !mayServeProject.test(tunnel.principal, projectId)) {
      throw new TunnelException(403, "Tunnel " + name + " belongs to " + tunnel.ownerLabel + ", who can't edit this project");
    }
    if (body.length > MAX_BODY) throw new TunnelException(413, "Requests through a tunnel can be up to 5 MB");
    String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath()) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    TunnelMessages.Request request = new TunnelMessages.Request(UUID.randomUUID().toString(), method, path, headers, body);
    CompletableFuture<TunnelMessages.Response> answer = new CompletableFuture<>();
    synchronized (tunnel) {
      if (tunnel.pending.size() >= MAX_IN_FLIGHT) throw new TunnelException(503, "Tunnel " + name + " already has " + MAX_IN_FLIGHT + " requests waiting");
      tunnel.pending.put(request.id(), answer);
      tunnel.requests++;
      tunnel.bytesOut += body.length;
      if (!handToWaiter(tunnel, request)) tunnel.queue.add(request);
    }
    try {
      TunnelMessages.Response response = answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      synchronized (tunnel) {
        tunnel.bytesIn += response.body().length;
      }
      return response;
    } catch (TimeoutException e) {
      fail(tunnel, request);
      throw new TunnelException(504, "Tunnel " + name + " didn't answer within " + timeout.toSeconds() + " seconds");
    } catch (ExecutionException e) {
      fail(tunnel, request);
      throw e.getCause() instanceof TunnelException t ? t : new TunnelException(502, "Tunnel " + name + " failed: " + e.getCause().getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(tunnel, request);
      throw new TunnelException(502, "Interrupted while waiting for tunnel " + name);
    }
  }

  public ArrayNode list(Principal principal) {
    ArrayNode out = Jsons.MAPPER.createArrayNode();
    tunnels.values().stream()
        .filter(t -> principal.isAdmin() || t.ownerId.equals(principal.id()))
        .sorted((a, b) -> a.name.compareTo(b.name))
        .forEach(t -> {
          synchronized (t) {
            out.addObject()
                .put("name", t.name)
                .put("owner", t.ownerLabel)
                .put("url", SCHEME + "://" + t.name)
                .put("online", online(t))
                .put("connectedAt", t.connectedAt.toString())
                .put("lastSeenAt", t.lastPoll.toString())
                .put("requests", t.requests)
                .put("failed", t.failed)
                .put("waiting", t.pending.size())
                .put("bytesOut", t.bytesOut)
                .put("bytesIn", t.bytesIn);
          }
        });
    return out;
  }

  public void close(String name, Principal principal) {
    Tunnel tunnel = tunnels.get(name);
    if (tunnel == null || !(principal.isAdmin() || tunnel.ownerId.equals(principal.id()))) throw new TunnelException(404, "Tunnel " + name + " not found");
    shut(tunnel, "Tunnel " + name + " was closed");
  }

  public void onTokenRevoked(String tokenId) {
    tunnels.values().stream().filter(t -> tokenId.equals(t.tokenId)).toList()
        .forEach(t -> shut(t, "The access token of tunnel " + t.name + " was revoked"));
  }

  public Optional<Boolean> isOnline(String name) {
    return Optional.ofNullable(tunnels.get(name)).map(this::online);
  }

  private Tunnel open(String name, Principal principal) {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new TunnelException(400, "Tunnel names are 1 to 40 lowercase letters, digits and dashes, like laptop or alice-api");
    }
    boolean bin = principal.id().startsWith("bin:");
    if (bin && !name.equals("bin-" + principal.id().substring("bin:".length()))) {
      throw new TunnelException(403, "A bin's key opens only its own tunnel, bin-" + principal.id().substring("bin:".length()));
    }
    if (!bin && name.startsWith("bin-")) throw new TunnelException(403, "Tunnel names starting with bin- belong to webhook bins. Pick another name");
    if (principal.reviewOnly() || !principal.highestRole().atLeast(Role.EDITOR)) {
      throw new TunnelException(403, "Opening a tunnel needs the editor role in a team");
    }
    String tokenId = principal.grant() == null ? null : principal.grant().tokenId();
    synchronized (tunnels) {
      Tunnel existing = tunnels.get(name);
      if (existing != null && !existing.closed) {
        if (!existing.ownerId.equals(principal.id())) throw new TunnelException(409, "Tunnel " + name + " is in use by " + existing.ownerLabel);
        existing.principal = principal;
        existing.tokenId = tokenId;
        return existing;
      }
      long mine = tunnels.values().stream().filter(t -> !t.closed && t.ownerId.equals(principal.id())).count();
      if (mine >= MAX_PER_USER) throw new TunnelException(429, "You can have at most " + MAX_PER_USER + " tunnels. Close one first");
      Tunnel created = new Tunnel(name, principal, tokenId, Instant.now(clock));
      tunnels.put(name, created);
      return created;
    }
  }

  private Tunnel owned(String name, Principal principal) {
    Tunnel tunnel = tunnels.get(name);
    if (tunnel == null || tunnel.closed) throw new TunnelException(410, "Tunnel " + name + " is closed");
    if (!tunnel.ownerId.equals(principal.id())) throw new TunnelException(403, "Tunnel " + name + " is in use by " + tunnel.ownerLabel);
    return tunnel;
  }

  private boolean online(Tunnel tunnel) {
    synchronized (tunnel) {
      return !tunnel.closed && (tunnel.waiters.stream().anyMatch(w -> !w.isDone()) || tunnel.lastPoll.plus(ONLINE_GRACE).isAfter(Instant.now(clock)));
    }
  }

  private static boolean handToWaiter(Tunnel tunnel, TunnelMessages.Request request) {
    CompletableFuture<TunnelMessages.Request> waiter;
    while ((waiter = tunnel.waiters.poll()) != null) {
      if (waiter.complete(request)) return true;
    }
    return false;
  }

  private static void fail(Tunnel tunnel, TunnelMessages.Request request) {
    synchronized (tunnel) {
      tunnel.pending.remove(request.id());
      tunnel.queue.remove(request);
      tunnel.failed++;
    }
  }

  private void shut(Tunnel tunnel, String reason) {
    synchronized (tunnel) {
      tunnel.closed = true;
      TunnelException closed = new TunnelException(410, reason);
      tunnel.waiters.forEach(w -> w.completeExceptionally(closed));
      tunnel.waiters.clear();
      tunnel.pending.values().forEach(p -> p.completeExceptionally(closed));
      tunnel.pending.clear();
      tunnel.queue.clear();
    }
    tunnels.remove(tunnel.name, tunnel);
  }

  private static final class Tunnel {
    final String name;
    final String ownerId;
    final String ownerLabel;
    final Instant connectedAt;
    final Deque<TunnelMessages.Request> queue = new ArrayDeque<>();
    final Deque<CompletableFuture<TunnelMessages.Request>> waiters = new ArrayDeque<>();
    final Map<String, CompletableFuture<TunnelMessages.Response>> pending = new ConcurrentHashMap<>();
    volatile Principal principal;
    volatile String tokenId;
    volatile boolean closed;
    Instant lastPoll;
    long requests;
    long failed;
    long bytesOut;
    long bytesIn;

    Tunnel(String name, Principal principal, String tokenId, Instant now) {
      this.name = name;
      this.ownerId = principal.id();
      this.ownerLabel = principal.username();
      this.principal = principal;
      this.tokenId = tokenId;
      this.connectedAt = now;
      this.lastPoll = now;
    }
  }
}
