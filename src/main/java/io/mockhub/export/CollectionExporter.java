package io.mockhub.export;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.project.Project;
import java.util.List;

public interface CollectionExporter {
  String format();

  String contentType();

  String fileName(Project project);

  byte[] export(Project project, List<StubMapping> stubs, String mockUrl) throws Exception;
}
