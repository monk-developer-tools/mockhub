package io.mockhub.example;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.WsdlStubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExampleInstallerTest {

  private final FakeStubGateway gateway = new FakeStubGateway();
  private ProjectService projects;
  private ExampleInstaller installer;

  @BeforeEach
  void setUp() {
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    StubService stubs = new StubService(gateway, ref::get);
    projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    installer = new ExampleInstaller(ExampleCatalog.fromClasspath(), projects, stubs, new ImporterRegistry(List.of(new WsdlStubImporter())));
  }

  @Test
  void installsAnExampleAsANewProject() {
    // When
    Project project = installer.install("rest-basics");

    // Then
    assertThat(project.slug()).isEqualTo("example-petstore");
    assertThat(gateway.findAll()).hasSize(8).allSatisfy(s -> assertThat(StubMetadata.projectId(s)).isEqualTo(project.id()));
  }

  @Test
  void installingTwiceGetsAFreeBasePath() {
    // Given
    projects.create(tree("{'name':'taken','slug':'example-petstore'}"));

    // When
    Project project = installer.install("rest-basics");

    // Then
    assertThat(project.slug()).isEqualTo("example-petstore-2");
  }

  @Test
  void runsTheExamplesImportsBeforeItsOwnStubs() {
    // When
    installer.install("soap-calculator");

    // Then
    assertThat(gateway.findAll()).hasSize(6).allSatisfy(s -> assertThat(StubMetadata.isSoap(s)).isTrue());
  }
}
