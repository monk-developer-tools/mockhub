package io.mockhub.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationListener;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DatasetService implements OrganizationListener {
  public static final int MAX_IDENTITY = 3;
  public static final int MAX_INDEXES = 5;
  public static final int MAX_FILTERS = 3;
  private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
  private static final int MAX_NAME = 100;
  private static final int MAX_DESCRIPTION = 500;

  public record Limits(long maxUploadBytes, int maxRecords, int maxRecordBytes, int maxDatasets) {
    public static final Limits DEFAULT = new Limits(20L * 1024 * 1024, 100_000, 64 * 1024, 100);
  }

  public record UploadResult(Dataset dataset, int added, int updated, int skipped) {}

  public record Page(List<ObjectNode> records, int total) {}

  private final DatasetRepository repository;
  private final Limits limits;
  private final Clock clock;
  private final Map<String, DatasetIndex> indexes = new ConcurrentHashMap<>();

  public DatasetService(DatasetRepository repository, Limits limits, Clock clock) {
    this.repository = repository;
    this.limits = limits;
    this.clock = clock;
  }

  public Limits limits() {
    return limits;
  }

  public List<Dataset> list(String orgId) {
    return repository.findAll(orgId).stream().map(StoredDataset::dataset).toList();
  }

  public Dataset get(String orgId, String key) {
    return stored(orgId, key).dataset();
  }

  public synchronized Dataset create(String orgId, JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send {\"key\": \"customers\", \"name\": \"Customers\"}");
    String key = body.path("key").asText("").trim();
    if (!KEY.matcher(key).matches()) {
      throw ApiException.badRequest("The key must be lowercase letters, digits, dashes or underscores, e.g. customers; helpers use it");
    }
    if (repository.find(orgId, key).isPresent()) throw new ApiException(409, "A dataset called " + key + " already exists");
    if (repository.findAll(orgId).size() >= limits.maxDatasets()) {
      throw ApiException.badRequest("An organisation can have up to " + limits.maxDatasets() + " datasets");
    }
    String now = clock.instant().toString();
    Dataset dataset = new Dataset(orgId, key, name(body, key), description(body), fields(body, "identity", MAX_IDENTITY),
        fields(body, "indexes", MAX_INDEXES), List.of(), 0, 0, now, now);
    save(new StoredDataset(dataset, List.of()));
    return dataset;
  }

  public synchronized Dataset update(String orgId, String key, JsonNode body) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send the dataset's name, description, identity and indexes");
    StoredDataset current = stored(orgId, key);
    Dataset d = current.dataset();
    List<String> identity = body.has("identity") ? fields(body, "identity", MAX_IDENTITY) : d.identity();
    if (!identity.isEmpty()) checkUnique(current.records(), identity);
    Dataset updated = d.withSettings(body.has("name") ? name(body, key) : d.name(), body.has("description") ? description(body) : d.description(),
        identity, body.has("indexes") ? fields(body, "indexes", MAX_INDEXES) : d.indexes(), clock.instant().toString());
    save(new StoredDataset(updated, current.records()));
    return updated;
  }

  public synchronized boolean delete(String orgId, String key) {
    indexes.remove(cacheKey(orgId, key));
    return repository.delete(orgId, key);
  }

  public synchronized UploadResult upload(String orgId, String key, DatasetFormat format, UploadMode mode, byte[] content) {
    if (content == null || content.length == 0) throw ApiException.badRequest("The upload is empty");
    if (content.length > limits.maxUploadBytes()) {
      throw new ApiException(413, "Uploads can be at most " + size(limits.maxUploadBytes()));
    }
    StoredDataset current = stored(orgId, key);
    Dataset d = current.dataset();
    DatasetParser.Parsed parsed = DatasetParser.parse(format, new String(content, StandardCharsets.UTF_8));
    checkSizes(parsed.records());
    List<String> identity = d.identity();
    if (!identity.isEmpty()) checkUnique(parsed.records(), identity);
    if (mode == UploadMode.UPSERT && identity.isEmpty()) {
      throw ApiException.badRequest("Set identity fields, such as id, to update records; or upload with mode add or replace");
    }

    List<ObjectNode> records;
    int added = 0;
    int updated = 0;
    int skipped = 0;
    if (mode == UploadMode.REPLACE) {
      records = parsed.records();
      added = records.size();
    } else {
      records = new ArrayList<>(current.records());
      Map<String, Integer> positions = new HashMap<>();
      if (!identity.isEmpty()) for (int i = 0; i < records.size(); i++) positions.put(identityOf(records.get(i), identity, i), i);
      for (int i = 0; i < parsed.records().size(); i++) {
        ObjectNode record = parsed.records().get(i);
        Integer at = identity.isEmpty() ? null : positions.get(identityOf(record, identity, i));
        if (at == null) {
          records.add(record);
          added++;
        } else if (mode == UploadMode.UPSERT) {
          records.set(at, record);
          updated++;
        } else {
          skipped++;
        }
      }
    }
    if (records.size() > limits.maxRecords()) {
      throw ApiException.badRequest("A dataset holds up to " + String.format(Locale.ROOT, "%,d", limits.maxRecords())
          + " records; this upload would make " + String.format(Locale.ROOT, "%,d", records.size()));
    }
    List<Column> columns = mode == UploadMode.REPLACE ? parsed.columns() : merge(d.columns(), parsed.columns());
    long bytes = records.stream().mapToLong(r -> r.toString().getBytes(StandardCharsets.UTF_8).length).sum();
    Dataset saved = d.withRecords(columns, records.size(), bytes, clock.instant().toString());
    save(new StoredDataset(saved, records));
    return new UploadResult(saved, added, updated, skipped);
  }

  public Page records(String orgId, String key, String search, Map<String, String> filters, int offset, int limit) {
    StoredDataset stored = stored(orgId, key);
    checkFilters(filters);
    String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) {
      DatasetIndex index = index(stored);
      return new Page(copies(index.find(filters, offset, limit)), index.count(filters));
    }
    List<ObjectNode> matching = stored.records().stream()
        .filter(r -> DatasetIndex.matchesAll(r, filters))
        .filter(r -> r.toString().toLowerCase(Locale.ROOT).contains(q))
        .toList();
    return new Page(copies(matching.stream().skip(offset).limit(limit).toList()), matching.size());
  }

  public List<ObjectNode> find(String orgId, String key, Map<String, String> filters, int limit) {
    checkFilters(filters);
    return copies(index(stored(orgId, key)).find(filters, 0, limit));
  }

  public List<StoredDataset> snapshot(String orgId) {
    return repository.findAll(orgId);
  }

  public synchronized int restore(String orgId, List<StoredDataset> sets, boolean replaceExisting) {
    int restored = 0;
    for (StoredDataset set : sets) {
      Dataset d = set.dataset();
      if (d == null || d.key() == null || !KEY.matcher(d.key()).matches()) continue;
      if (!replaceExisting && repository.find(orgId, d.key()).isPresent()) continue;
      Dataset moved = new Dataset(orgId, d.key(), d.name(), d.description(), d.identity(), d.indexes(), d.columns(),
          set.records().size(), d.bytes(), d.createdAt(), d.updatedAt());
      save(new StoredDataset(moved, set.records()));
      restored++;
    }
    return restored;
  }

  @Override
  public void onOrganizationDeleted(Organization organization) {
    indexes.keySet().removeIf(k -> k.startsWith(organization.id() + "/"));
    repository.deleteOrg(organization.id());
  }

  private StoredDataset stored(String orgId, String key) {
    return repository.find(orgId, key).orElseThrow(() -> ApiException.notFound("Dataset " + key));
  }

  private void save(StoredDataset stored) {
    repository.save(stored);
    indexes.remove(cacheKey(stored.dataset().orgId(), stored.dataset().key()));
  }

  private DatasetIndex index(StoredDataset stored) {
    Dataset d = stored.dataset();
    return indexes.computeIfAbsent(cacheKey(d.orgId(), d.key()), k -> {
      Set<String> fields = new LinkedHashSet<>(d.identity());
      fields.addAll(d.indexes());
      return new DatasetIndex(stored.records(), fields);
    });
  }

  private void checkSizes(List<ObjectNode> records) {
    for (int i = 0; i < records.size(); i++) {
      int size = records.get(i).toString().getBytes(StandardCharsets.UTF_8).length;
      if (size > limits.maxRecordBytes()) {
        throw ApiException.badRequest("Record " + (i + 1) + " is " + size(size) + "; records can be at most " + size(limits.maxRecordBytes()));
      }
    }
  }

  private static void checkUnique(List<ObjectNode> records, List<String> identity) {
    Map<String, Integer> seen = new HashMap<>();
    for (int i = 0; i < records.size(); i++) {
      Integer before = seen.put(identityOf(records.get(i), identity, i), i);
      if (before != null) {
        throw ApiException.badRequest("Records " + (before + 1) + " and " + (i + 1) + " have the same " + String.join(" + ", identity));
      }
    }
  }

  private static String identityOf(ObjectNode record, List<String> identity, int position) {
    List<String> parts = new ArrayList<>();
    for (String field : identity) {
      Set<String> keys = DatasetIndex.keys(DatasetIndex.at(record, field));
      if (keys.isEmpty()) throw ApiException.badRequest("Record " + (position + 1) + " has no " + field + ", which identifies records");
      parts.add(keys.iterator().next());
    }
    return String.join("\u0000", parts);
  }

  private static void checkFilters(Map<String, String> filters) {
    if (filters.size() > MAX_FILTERS) throw ApiException.badRequest("Filter on up to " + MAX_FILTERS + " fields");
  }

  private static List<Column> merge(List<Column> existing, List<Column> added) {
    Map<String, Column> out = new LinkedHashMap<>();
    existing.forEach(c -> out.put(c.name(), c));
    added.forEach(c -> out.putIfAbsent(c.name(), c));
    return List.copyOf(out.values());
  }

  private static List<ObjectNode> copies(List<ObjectNode> records) {
    return records.stream().map(ObjectNode::deepCopy).toList();
  }

  static String size(long bytes) {
    if (bytes >= 1024 * 1024) return bytes / (1024 * 1024) + " MB";
    if (bytes >= 1024) return bytes / 1024 + " KB";
    return bytes + " bytes";
  }

  private static String cacheKey(String orgId, String key) {
    return orgId + "/" + key;
  }

  private static String name(JsonNode body, String fallback) {
    String name = body.path("name").asText("").trim();
    if (name.length() > MAX_NAME) throw ApiException.badRequest("Keep the name under " + MAX_NAME + " characters");
    return name.isEmpty() ? fallback : name;
  }

  private static String description(JsonNode body) {
    String text = body.path("description").asText("").trim();
    if (text.length() > MAX_DESCRIPTION) throw ApiException.badRequest("Keep the description under " + MAX_DESCRIPTION + " characters");
    return text;
  }

  private static List<String> fields(JsonNode body, String name, int max) {
    JsonNode node = body.path(name);
    if (node.isMissingNode() || node.isNull()) return List.of();
    if (!node.isArray()) throw ApiException.badRequest(name + " must be a list of field names, e.g. [\"id\"]");
    Set<String> out = new LinkedHashSet<>();
    node.forEach(f -> {
      String field = f.asText("").trim();
      if (!f.isTextual() || field.isEmpty()) throw ApiException.badRequest(name + " must be a list of field names, e.g. [\"id\"]");
      out.add(field);
    });
    if (out.size() > max) throw ApiException.badRequest("Use up to " + max + " " + name + " fields");
    return List.copyOf(out);
  }
}
