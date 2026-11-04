package io.mockhub.state;

import io.mockhub.data.DataStore;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class StateRepository {
  public static final String RESOURCE = "_state";

  private final DataStore data;
  private final Supplier<List<String>> projectIds;
  private final int maxContexts;

  public StateRepository(DataStore data, Supplier<List<String>> projectIds, int maxContexts) {
    this.data = data;
    this.projectIds = projectIds;
    this.maxContexts = Math.max(0, maxContexts);
  }

  public record Found(String projectId, StateRecord record) {}

  public List<StateRecord> list(String projectId) {
    return data.list(projectId, RESOURCE).stream().map(StateRecord::of).sorted(Comparator.comparing(StateRecord::name)).toList();
  }

  public Optional<Found> find(String name, Optional<String> preferred) {
    return searchOrder(preferred)
        .map(p -> data.get(p, RESOURCE, name).map(r -> new Found(p, StateRecord.of(r))))
        .flatMap(Optional::stream)
        .findFirst();
  }

  public Stream<String> names() {
    return projectIds.get().stream().flatMap(p -> list(p).stream().map(StateRecord::name));
  }

  public void save(String projectId, StateRecord record) {
    projectIds.get().stream().filter(p -> !p.equals(projectId)).forEach(p -> data.delete(p, RESOURCE, record.name()));
    data.put(projectId, RESOURCE, record.name(), record.toJson());
    if (maxContexts > 0) dropLeastRecentlyChanged(projectId);
  }

  private void dropLeastRecentlyChanged(String projectId) {
    int over = data.resources(projectId).getOrDefault(RESOURCE, 0) - maxContexts;
    if (over <= 0) return;
    data.list(projectId, RESOURCE).stream().map(StateRecord::of)
        .sorted(Comparator.comparing(StateRepository::changedAt))
        .limit(over)
        .forEach(old -> data.delete(projectId, RESOURCE, old.name()));
  }

  private static Instant changedAt(StateRecord record) {
    try {
      return Instant.parse(record.updatedAt());
    } catch (DateTimeParseException e) {
      return Instant.EPOCH;
    }
  }

  public boolean delete(String name) {
    boolean deleted = false;
    for (String p : projectIds.get()) deleted |= data.delete(p, RESOURCE, name);
    return deleted;
  }

  public boolean delete(String projectId, String name) {
    return data.delete(projectId, RESOURCE, name);
  }

  public void clear(String projectId) {
    data.clear(projectId, RESOURCE);
  }

  public void clearAll() {
    projectIds.get().forEach(this::clear);
  }

  private Stream<String> searchOrder(Optional<String> preferred) {
    List<String> order = new ArrayList<>();
    preferred.ifPresent(order::add);
    projectIds.get().stream().filter(p -> !order.contains(p)).forEach(order::add);
    return order.stream();
  }
}
