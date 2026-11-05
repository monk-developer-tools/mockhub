package io.mockhub.grpc;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GrpcCatalog implements ProjectListener {
  private static final Logger LOG = LoggerFactory.getLogger(GrpcCatalog.class);
  private static final String WELL_KNOWN_PREFIX = "google/protobuf/";
  public static final int MAX_DESCRIPTORS_PER_PROJECT = 20;

  private final DescriptorStore store;
  private final Function<String, String> projectName;
  private final Map<String, TreeMap<String, DescriptorSet>> sets = new TreeMap<>();
  private final List<Runnable> listeners = new ArrayList<>();
  private volatile List<FileDescriptor> merged = List.of();
  private volatile Map<String, String> owners = Map.of();

  public GrpcCatalog(DescriptorStore store, Function<String, String> projectName) {
    this.store = store;
    this.projectName = projectName;
    store.loadAll().forEach((projectId, files) -> files.forEach((name, bytes) -> {
      try {
        List<DescriptorSet> candidate = new ArrayList<>(all());
        DescriptorSet set = new DescriptorSet(projectId, name, ProtoFiles.parse(bytes), bytes);
        candidate.add(set);
        merge(candidate);
        sets.computeIfAbsent(projectId, k -> new TreeMap<>()).put(name, set);
      } catch (GrpcException e) {
        LOG.warn("Skipping gRPC descriptor {} of project {}: {}", name, projectId, e.getMessage());
      }
    }));
    publish();
  }

  public synchronized void onChange(Runnable listener) {
    listeners.add(listener);
  }

  public List<FileDescriptor> fileDescriptors() {
    return merged;
  }

  public Optional<String> ownerOf(String serviceName) {
    return Optional.ofNullable(owners.get(serviceName));
  }

  public synchronized List<DescriptorSet> setsOf(String projectId) {
    return List.copyOf(sets.getOrDefault(projectId, new TreeMap<>()).values());
  }

  public synchronized DescriptorSet add(String projectId, String name, byte[] bytes) {
    DescriptorNames.require(name);
    TreeMap<String, DescriptorSet> current = sets.getOrDefault(projectId, new TreeMap<>());
    if (!current.containsKey(name) && current.size() >= MAX_DESCRIPTORS_PER_PROJECT) {
      throw new GrpcException("A project can have at most " + MAX_DESCRIPTORS_PER_PROJECT + " descriptor files");
    }
    DescriptorSet set = new DescriptorSet(projectId, name, ProtoFiles.parse(bytes), bytes);
    if (set.services().isEmpty()) throw new GrpcException("The descriptor set defines no services");
    List<DescriptorSet> candidate = new ArrayList<>(all().stream().filter(s -> !(s.projectId().equals(projectId) && s.name().equals(name))).toList());
    candidate.add(set);
    merge(candidate);
    store.save(projectId, name, bytes);
    sets.computeIfAbsent(projectId, k -> new TreeMap<>()).put(name, set);
    publish();
    return set;
  }

  public synchronized boolean remove(String projectId, String name) {
    TreeMap<String, DescriptorSet> project = sets.get(projectId);
    if (project == null || project.remove(name) == null) return false;
    if (project.isEmpty()) sets.remove(projectId);
    store.delete(projectId, name);
    publish();
    return true;
  }

  @Override
  public synchronized void onProjectDeleted(Project project) {
    sets.remove(project.id());
    store.deleteProject(project.id());
    publish();
  }

  private List<DescriptorSet> all() {
    return sets.values().stream().flatMap(m -> m.values().stream()).toList();
  }

  private Merged merge(List<DescriptorSet> candidate) {
    Map<String, Owned> files = new LinkedHashMap<>();
    Map<String, String> messageFiles = new HashMap<>();
    Map<String, String> serviceOwners = new HashMap<>();
    for (DescriptorSet set : candidate) {
      for (FileDescriptor file : set.files()) {
        Owned existing = files.get(file.getName());
        if (existing != null) {
          if (!file.getName().startsWith(WELL_KNOWN_PREFIX) && !existing.file().toProto().equals(file.toProto())) {
            throw new GrpcException(file.getName() + " differs from the copy in " + describe(existing.set())
                + ". Give the file a different name or upload the same version");
          }
          if (!file.getServices().isEmpty()) {
            throw new GrpcException("Service " + file.getServices().get(0).getFullName() + " is already served by " + describe(existing.set()));
          }
          continue;
        }
        files.put(file.getName(), new Owned(file, set));
        for (Descriptor message : file.getMessageTypes()) claimMessages(message, file, messageFiles);
        for (ServiceDescriptor service : file.getServices()) {
          String owner = serviceOwners.putIfAbsent(service.getFullName(), set.projectId());
          if (owner != null) {
            throw new GrpcException("Service " + service.getFullName() + " is already served by " + describe(ownerSet(files, service.getFullName())));
          }
        }
      }
    }
    return new Merged(files.values().stream().map(Owned::file).toList(), serviceOwners);
  }

  private static DescriptorSet ownerSet(Map<String, Owned> files, String service) {
    return files.values().stream()
        .filter(o -> o.file().getServices().stream().anyMatch(s -> s.getFullName().equals(service)))
        .map(Owned::set).findFirst().orElseThrow();
  }

  private void claimMessages(Descriptor message, FileDescriptor file, Map<String, String> messageFiles) {
    String other = messageFiles.putIfAbsent(message.getFullName(), file.getName());
    if (other != null && !other.equals(file.getName())) {
      throw new GrpcException("Message " + message.getFullName() + " is defined in both " + other + " and " + file.getName());
    }
    for (Descriptor nested : message.getNestedTypes()) claimMessages(nested, file, messageFiles);
  }

  private String describe(DescriptorSet set) {
    String project = projectName.apply(set.projectId());
    return set.name() + " in project " + (project == null ? set.projectId() : project);
  }

  private void publish() {
    Merged m = merge(all());
    merged = m.files();
    owners = Map.copyOf(m.owners());
    for (Runnable listener : listeners) listener.run();
  }

  private record Owned(FileDescriptor file, DescriptorSet set) {}

  private record Merged(List<FileDescriptor> files, Map<String, String> owners) {}

  public synchronized Map<String, byte[]> snapshot(String projectId) {
    Map<String, byte[]> out = new TreeMap<>();
    setsOf(projectId).forEach(s -> out.put(s.name(), s.bytes().clone()));
    return out;
  }

  public record DescriptorSet(String projectId, String name, List<FileDescriptor> files, byte[] bytes) {
    public int size() {
      return bytes.length;
    }

    public List<ServiceDescriptor> services() {
      return files.stream().flatMap(f -> f.getServices().stream()).toList();
    }

    public List<MethodDescriptor> methods() {
      return services().stream().flatMap(s -> s.getMethods().stream()).toList();
    }
  }
}
