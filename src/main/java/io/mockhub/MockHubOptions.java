package io.mockhub;

import com.github.tomakehurst.wiremock.core.MappingsSaver;
import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import io.mockhub.stub.BoundedFilenameMaker;

final class MockHubOptions extends CommandLineOptions {

  private final JsonFileMappingsSource mappings;

  MockHubOptions(String... args) {
    super(args);
    mappings = new JsonFileMappingsSource(filesRoot().child("mappings"), new BoundedFilenameMaker(getFilenameMaker()));
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
