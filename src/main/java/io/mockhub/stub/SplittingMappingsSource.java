package io.mockhub.stub;

import com.github.tomakehurst.wiremock.common.AbstractFileSource;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.common.JsonException;
import com.github.tomakehurst.wiremock.common.TextFile;
import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker;
import com.github.tomakehurst.wiremock.standalone.MappingFileException;
import com.github.tomakehurst.wiremock.standalone.MappingsSource;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappingCollection;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SplittingMappingsSource implements MappingsSource {

  private final FileSource files;
  private final FilenameMaker names;
  private final Path orderFile;
  private final Map<UUID, String> paths = new HashMap<>();
  private final Map<String, List<StubMapping>> shared = new HashMap<>();
  private final List<String> order = new ArrayList<>();

  public SplittingMappingsSource(FileSource files, FilenameMaker names, Path orderFile) {
    this.files = files;
    this.names = names;
    this.orderFile = orderFile;
  }

  @Override
  public synchronized void loadMappingsInto(StubMappings stubs) {
    paths.clear();
    shared.clear();
    order.clear();
    if (!files.exists()) return;
    Map<String, TextFile> found = new LinkedHashMap<>();
    files.listFilesRecursively().stream().filter(AbstractFileSource.byFileExtension("json")).forEach(f -> found.put(relative(f), f));
    for (String path : savedOrder()) if (found.containsKey(path) && !order.contains(path)) order.add(path);
    for (String path : found.keySet()) if (!order.contains(path)) order.add(path);
    for (String path : order) {
      StubMappingCollection collection = read(found.get(path));
      for (StubMapping stub : collection.getMappingOrMappings()) {
        stub.setDirty(false);
        stubs.addMapping(stub);
        paths.put(stub.getId(), path);
        if (collection.isMulti()) shared.computeIfAbsent(path, p -> new ArrayList<>()).add(stub);
      }
    }
    writeOrder();
  }

  @Override
  public void save(List<StubMapping> stubs) {
    for (StubMapping stub : stubs) if (stub.isDirty()) save(stub);
  }

  @Override
  public synchronized void save(StubMapping stub) {
    split(stub.getId());
    String path = paths.get(stub.getId());
    if (path == null) {
      path = names.filenameFor(stub);
      order.add(path);
      writeOrder();
    }
    files.writeTextFile(path, Json.writePrivate(stub));
    paths.put(stub.getId(), path);
    stub.setDirty(false);
  }

  @Override
  @SuppressWarnings("removal")
  public void remove(StubMapping stub) {
    remove(stub.getId());
  }

  @Override
  public synchronized void remove(UUID id) {
    split(id);
    String path = paths.remove(id);
    if (path == null) return;
    files.deleteFile(path);
    order.remove(path);
    writeOrder();
  }

  @Override
  public synchronized void removeAll() {
    paths.values().stream().distinct().forEach(files::deleteFile);
    paths.clear();
    shared.clear();
    order.clear();
    writeOrder();
  }

  private void split(UUID id) {
    String path = paths.get(id);
    if (path == null) return;
    List<StubMapping> group = shared.remove(path);
    if (group == null) return;
    String folder = folderOf(path);
    List<String> slots = new ArrayList<>();
    for (StubMapping stub : group) {
      String own = inFolder(folder, names.filenameFor(stub));
      slots.add(own);
      paths.put(stub.getId(), own);
      if (!stub.getId().equals(id)) files.writeTextFile(own, Json.writePrivate(stub));
    }
    files.deleteFile(path);
    int at = order.indexOf(path);
    if (at < 0) at = order.size();
    else order.remove(at);
    order.addAll(at, slots);
    writeOrder();
  }

  private List<String> savedOrder() {
    try {
      return Files.isRegularFile(orderFile) ? Files.readAllLines(orderFile) : List.of();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeOrder() {
    try {
      Files.createDirectories(orderFile.getParent());
      Path temp = orderFile.resolveSibling(orderFile.getFileName() + ".tmp");
      Files.write(temp, order);
      Files.move(temp, orderFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String relative(TextFile file) {
    return Path.of(files.getPath()).relativize(Path.of(file.getPath())).toString().replace('\\', '/');
  }

  private static String folderOf(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? null : path.substring(0, slash);
  }

  private static String inFolder(String folder, String name) {
    return folder == null ? name : folder + "/" + name;
  }

  private static StubMappingCollection read(TextFile file) {
    try {
      return Json.read(file.readContents(), StubMappingCollection.class);
    } catch (JsonException e) {
      throw new MappingFileException(file.getPath(), e.getErrors().first().getDetail());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
