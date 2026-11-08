package io.mockhub;

import com.github.tomakehurst.wiremock.core.MappingsSaver;
import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import com.github.tomakehurst.wiremock.standalone.MappingsSource;
import com.github.tomakehurst.wiremock.store.Stores;
import io.mockhub.journal.MockHubStores;
import io.mockhub.stub.BoundedFilenameMaker;
import io.mockhub.stub.SplittingMappingsSource;
import java.nio.file.Path;
import java.util.Optional;

final class MockHubOptions extends CommandLineOptions {

  private final MappingsSource mappings;
  private final Stores stores;

  MockHubOptions(int defaultJournalEntries, int journalMaxMb, String... args) {
    super(args);
    mappings = new SplittingMappingsSource(filesRoot().child("mappings"), new BoundedFilenameMaker(getFilenameMaker()),
        Path.of(filesRoot().getPath(), "mockhub", "mapping-order.txt"));
    stores = new MockHubStores(filesRoot(), super.maxRequestJournalEntries().orElse(defaultJournalEntries), journalMaxMb);
  }

  @Override
  public Stores getStores() {
    return stores;
  }

  @Override
  public Optional<Integer> maxRequestJournalEntries() {
    return Optional.empty();
  }

  @Override
  public MappingsLoader mappingsLoader() {
    return mappings;
  }

  @Override
  public MappingsSaver mappingsSaver() {
    return mappings;
  }
}
