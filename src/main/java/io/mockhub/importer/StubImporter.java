package io.mockhub.importer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.project.Project;
import java.util.List;
import java.util.Map;

public interface StubImporter {
  String format();

  Result parse(Request request);

  record Request(Project project, String body, Map<String, String> options) {
    public String option(String name) {
      String v = options.get(name);
      return v == null || v.isBlank() ? null : v;
    }
  }

  record Result(List<ObjectNode> stubs, ObjectNode details) {}
}
