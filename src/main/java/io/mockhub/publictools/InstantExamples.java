package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.example.Example;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.project.Project;
import java.util.ArrayList;
import java.util.List;

public final class InstantExamples {
  private static final Project PREVIEW = new Project("instant-preview", "Preview", "m-preview000", "", null, null, null, null, null, null, false, null);

  private final ExampleCatalog catalog;
  private final ExampleInstaller installer;
  private List<Available> available;

  public InstantExamples(ExampleCatalog catalog, ExampleInstaller installer) {
    this.catalog = catalog;
    this.installer = installer;
  }

  public record Available(String id, String name, String description, int stubs) {}

  public synchronized List<Available> list() {
    if (available == null) available = List.copyOf(compute());
    return available;
  }

  public boolean has(String id) {
    return list().stream().anyMatch(a -> a.id().equals(id));
  }

  public InstantSource source(String id) {
    if (!has(id)) throw new InstantStubRules.Refused("That example isn't available as an instant mock");
    return InstantSources.example(installer, id);
  }

  private List<Available> compute() {
    List<Available> out = new ArrayList<>();
    for (Example example : catalog.all()) {
      try {
        ExampleInstaller.Plan plan = installer.plan(example.id(), PREVIEW);
        if (plan.usesFiles() || plan.usesDescriptors() || plan.stubs().isEmpty() || plan.stubs().size() > InstantMocks.MAX_STUBS) continue;
        if (plan.stubs().stream().allMatch(InstantExamples::runs)) {
          out.add(new Available(example.id(), example.name(), example.description(), plan.stubs().size()));
        }
      } catch (RuntimeException e) {
        continue;
      }
    }
    return out;
  }

  private static boolean runs(JsonNode stub) {
    try {
      InstantStubRules.checkImported(stub);
      return true;
    } catch (InstantStubRules.Refused e) {
      return false;
    }
  }
}
