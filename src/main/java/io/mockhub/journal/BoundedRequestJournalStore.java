package io.mockhub.journal;

import com.github.tomakehurst.wiremock.store.RequestJournalStore;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

public final class BoundedRequestJournalStore implements RequestJournalStore {
  static final long ENTRY_OVERHEAD = 2048;

  private record Entry(ServeEvent event, long bytes) {}

  private final Deque<UUID> order = new ConcurrentLinkedDeque<>();
  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
  private final int maxEntries;
  private final long maxBytes;
  private int size;
  private long bytes;

  public BoundedRequestJournalStore(int maxEntries, long maxBytes) {
    this.maxEntries = Math.max(0, maxEntries);
    this.maxBytes = Math.max(0, maxBytes);
  }

  @Override
  public void add(ServeEvent event) {
    put(event.getId(), event);
  }

  @Override
  public Stream<ServeEvent> getAll() {
    return order.stream().map(entries::get).filter(Objects::nonNull).map(Entry::event);
  }

  @Override
  public synchronized void removeLast() {
    UUID oldest = order.pollLast();
    if (oldest == null) return;
    forget(entries.remove(oldest));
  }

  @Override
  public Stream<UUID> getAllKeys() {
    return getAll().map(ServeEvent::getId);
  }

  @Override
  public Optional<ServeEvent> get(UUID id) {
    return Optional.ofNullable(entries.get(id)).map(Entry::event);
  }

  @Override
  public synchronized void put(UUID id, ServeEvent event) {
    Entry entry = new Entry(event, weigh(event));
    Entry before = entries.put(id, entry);
    if (before == null) {
      order.addFirst(id);
      size++;
    } else {
      bytes -= before.bytes();
    }
    bytes += entry.bytes();
    while (size > maxEntries || (bytes > maxBytes && size > 1)) removeLast();
  }

  @Override
  public synchronized void remove(UUID id) {
    Entry removed = entries.remove(id);
    if (removed == null) return;
    order.remove(id);
    forget(removed);
  }

  @Override
  public synchronized void clear() {
    order.clear();
    entries.clear();
    size = 0;
    bytes = 0;
  }

  synchronized int size() {
    return size;
  }

  synchronized long bytes() {
    return bytes;
  }

  private void forget(Entry entry) {
    if (entry == null) return;
    size--;
    bytes -= entry.bytes();
  }

  static long weigh(ServeEvent event) {
    long weight = ENTRY_OVERHEAD;
    if (event.getRequest() != null && event.getRequest().getBody() != null) weight += event.getRequest().getBody().length;
    if (event.getResponse() != null && event.getResponse().getBody() != null) weight += event.getResponse().getBody().length;
    return weight;
  }
}
