package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.ApiException;
import io.mockhub.auth.Principal;
import io.mockhub.history.HistoryEntry;
import io.mockhub.history.HistoryStore;
import io.mockhub.project.Project;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class DriftService {
  private static final System.Logger LOG = System.getLogger(DriftService.class.getName());

  private record Compiled(String updatedAt, ApiContract contract) {}

  private final ContractStore store;
  private final HistoryStore history;
  private final Function<String, Optional<Project>> projects;
  private final DriftNotifier notifier;
  private final Function<Project, String> driftLink;
  private final Clock clock;
  private final ContractChecker checker = new ContractChecker();
  private final Map<String, Compiled> compiled = new ConcurrentHashMap<>();
  private final Map<String, Object> locks = new ConcurrentHashMap<>();

  public DriftService(ContractStore store, HistoryStore history, Function<String, Optional<Project>> projects, DriftNotifier notifier,
      Function<Project, String> driftLink, Clock clock) {
    this.store = store;
    this.history = history;
    this.projects = projects;
    this.notifier = notifier;
    this.driftLink = driftLink;
    this.clock = clock;
  }

  public Optional<Contract> contract(String projectId) {
    return store.contract(projectId);
  }

  public Contract attach(String projectId, JsonNode spec, String source, String fileName, Principal by) {
    return attach(projectId, spec, source, fileName, null, by);
  }

  public Contract attach(String projectId, JsonNode spec, String source, String fileName, String stripPrefix, Principal by) {
    ContractDocument.check(spec);
    ApiContract.of(spec);
    String prefix = stripPrefix == null || stripPrefix.isBlank() ? null : stripPrefix.trim();
    if (prefix != null && !prefix.startsWith("/")) throw ApiException.badRequest("The prefix must start with /, like /api/v1");
    synchronized (lock(projectId)) {
      String now = Instant.now(clock).toString();
      Optional<Contract> existing = store.contract(projectId);
      String name = fileName != null && !fileName.isBlank() ? fileName : existing.map(Contract::fileName).orElse(null);
      String kept = stripPrefix != null ? prefix : existing.map(Contract::stripPrefix).orElse(null);
      Contract contract = new Contract(spec, source, name, now, by == null ? null : by.username(), existing.map(Contract::notifyUrl).orElse(null), kept);
      store.save(projectId, contract);
      store.saveDrift(projectId, store.drift(projectId).restarted());
      compiled.remove(projectId);
      return contract;
    }
  }

  public void detach(String projectId) {
    synchronized (lock(projectId)) {
      store.delete(projectId);
      compiled.remove(projectId);
    }
  }

  public Contract setNotifyUrl(String projectId, String url) {
    String clean = url == null || url.isBlank() ? null : url.trim();
    if (clean != null) notifier.checkUrl(clean);
    synchronized (lock(projectId)) {
      Contract contract = required(projectId).withNotifyUrl(clean);
      store.save(projectId, contract);
      return contract;
    }
  }

  public DriftState state(String projectId) {
    return store.drift(projectId);
  }

  public DriftState setExpected(String projectId, String key, boolean expected) {
    synchronized (lock(projectId)) {
      required(projectId);
      DriftState state = store.drift(projectId);
      if (state.findings().stream().noneMatch(f -> f.key().equals(key))) throw ApiException.notFound("Finding " + key);
      DriftState next = state.withExpected(key, expected);
      store.saveDrift(projectId, next);
      return next;
    }
  }

  public DriftState restart(String projectId) {
    synchronized (lock(projectId)) {
      required(projectId);
      store.saveDrift(projectId, store.drift(projectId).restarted());
    }
    return check(projectId);
  }

  public DriftState check(String projectId) {
    synchronized (lock(projectId)) {
      Contract contract = required(projectId);
      Project project = projects.apply(projectId).orElseThrow(() -> ApiException.notFound("Project " + projectId));
      ApiContract api = compiled(projectId, contract);
      DriftState state = store.drift(projectId);
      Map<String, DriftState.Finding> findings = new LinkedHashMap<>();
      state.findings().forEach(f -> findings.put(f.key(), f));
      long[] newest = {state.checkedUpTo()};
      boolean[] capped = {false};
      List<String> fresh = new ArrayList<>();
      String base = project.basePath();
      int scanned = history.each(projectId, state.checkedUpTo(), entry -> {
        newest[0] = Math.max(newest[0], entry.at());
        String path = entry.path();
        String relative = !base.isEmpty() && path.startsWith(base) ? path.substring(base.length()) : path;
        if (relative.isEmpty()) relative = "/";
        List<Drift> drifts;
        try {
          drifts = checker.check(api, entry, relative);
        } catch (RuntimeException e) {
          LOG.log(System.Logger.Level.DEBUG, "Drift check failed for " + entry.id(), e);
          return;
        }
        for (Drift d : drifts) {
          String key = d.key();
          DriftState.Finding existing = findings.get(key);
          if (existing == null) {
            if (findings.size() >= DriftState.MAX_FINDINGS) {
              capped[0] = true;
              continue;
            }
            findings.put(key, finding(key, d, entry, 1, entry.at(), entry.at()));
            fresh.add(key);
          } else {
            findings.put(key, merge(existing, entry));
          }
        }
      });
      String now = Instant.now(clock).toString();
      List<DriftState.Finding> ordered = findings.values().stream()
          .sorted(java.util.Comparator.comparing(DriftState.Finding::lastSeen).reversed()).toList();
      DriftState next = state.withFindings(newest[0], now, scanned, capped[0], ordered);
      java.util.Set<String> expected = state.expected();
      List<DriftState.Finding> newcomers = fresh.stream().map(findings::get).filter(f -> !expected.contains(f.key())).toList();
      if (!newcomers.isEmpty() && contract.notifyUrl() != null) {
        next = next.withNotice(notifier.notify(contract.notifyUrl(), now, projectId, project.name(), driftLink.apply(project), newcomers));
      }
      store.saveDrift(projectId, next);
      return next;
    }
  }

  public void checkAll() {
    for (String projectId : store.projectsWithContracts()) {
      try {
        if (projects.apply(projectId).isPresent()) check(projectId);
      } catch (RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Contract drift check failed for project " + projectId + ": " + e.getMessage());
      }
    }
  }

  private Contract required(String projectId) {
    return store.contract(projectId).orElseThrow(() -> new ApiException(404, "This project has no contract. Attach an OpenAPI document first"));
  }

  private ApiContract compiled(String projectId, Contract contract) {
    Compiled cached = compiled.get(projectId);
    if (cached != null && cached.updatedAt().equals(contract.updatedAt())) return cached.contract();
    ApiContract api = contract.compile();
    compiled.put(projectId, new Compiled(contract.updatedAt(), api));
    return api;
  }

  private Object lock(String projectId) {
    return locks.computeIfAbsent(projectId, k -> new Object());
  }

  private static DriftState.Finding finding(String key, Drift d, HistoryEntry e, long count, long first, long last) {
    return new DriftState.Finding(key, d.kind(), d.method(), d.path(), d.detail(), count, Instant.ofEpochMilli(first).toString(),
        Instant.ofEpochMilli(last).toString(), e.id(), e.url(), e.status());
  }

  private static DriftState.Finding merge(DriftState.Finding f, HistoryEntry e) {
    Instant at = Instant.ofEpochMilli(e.at());
    boolean newer = at.isAfter(Instant.parse(f.lastSeen()));
    boolean older = at.isBefore(Instant.parse(f.firstSeen()));
    return new DriftState.Finding(f.key(), f.kind(), f.method(), f.path(), f.detail(), f.count() + 1,
        older ? at.toString() : f.firstSeen(), newer ? at.toString() : f.lastSeen(),
        newer ? e.id() : f.exampleId(), newer ? e.url() : f.exampleUrl(), newer ? e.status() : f.exampleStatus());
  }
}
