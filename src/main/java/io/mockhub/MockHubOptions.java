package io.mockhub;

import com.github.tomakehurst.wiremock.core.MappingsSaver;
import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import com.github.tomakehurst.wiremock.standalone.MappingsSource;
import io.mockhub.stub.BoundedFilenameMaker;
import io.mockhub.stub.SplittingMappingsSource;
import java.nio.file.Path;

final class MockHubOptions extends CommandLineOptions {

  private final MappingsSource mappings;

  MockHubOptions(String... args) {
    super(args);
    mappings = new SplittingMappingsSource(filesRoot().child("mappings"), new BoundedFilenameMaker(getFilenameMaker()),
        Path.of(filesRoot().getPath(), "mockhub", "mapping-order.txt"));
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
