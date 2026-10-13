package io.mockhub.export;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.api.Jsons;
import io.mockhub.project.Project;
import java.util.List;

public final class WireMockMappingsExporter implements CollectionExporter {
  @Override
  public String format() {
    return "wiremock";
  }

  @Override
  public String contentType() {
    return "application/json";
  }

  @Override
  public String fileName(Project project) {
    return project.slug() + "-wiremock-mappings.json";
  }

  @Override
  public byte[] export(Project project, List<StubMapping> stubs, String mockUrl) throws Exception {
    ObjectNode out = Jsons.object();
    out.putObject("meta").put("mockhubBasePath", project.basePath()).put("project", project.name());
    ArrayNode mappings = out.putArray("mappings");
    for (StubMapping s : stubs) {
      ObjectNode node = (ObjectNode) Jsons.fromWireMock(s);
      node.remove("insertionIndex");
      mappings.add(node);
    }
    return Jsons.MAPPER.writeValueAsBytes(out);
  }
}
