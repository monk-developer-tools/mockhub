package io.mockhub.example;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.files.BodyFiles;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.OpenApiImporter;
import io.mockhub.support.InMemoryDataStore;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import io.mockhub.importer.WsdlStubImporter;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubMetadata;
import io.mockhub.stub.StubService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryProjectRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExampleInstallerTest {

  private final FakeStubGateway gateway = new FakeStubGateway();
  private ProjectService projects;
  private ExampleInstaller installer;
  private CrudService crud;
  private BodyFiles files;

  @TempDir
  Path dataDir;

  @BeforeEach
  void setUp() {
    AtomicReference<ProjectService> ref = new AtomicReference<>();
    StubService stubs = new StubService(gateway, ref::get);
    projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), Clock.systemUTC());
    ref.set(projects);
    crud = new CrudService(new InMemoryDataStore());
    files = new BodyFiles(dataDir);
    installer = new ExampleInstaller(ExampleCatalog.fromClasspath(), projects, stubs, new ImporterRegistry(List.of(new WsdlStubImporter(), new OpenApiImporter())), crud, files);
  }

  @Test
  void installsAnExampleAsANewProject() {
    // When
    Project project = installer.install("rest-basics", "org1", "team1");

    // Then
    assertThat(project.slug()).isEqualTo("example-petstore");
    assertThat(gateway.findAll()).hasSize(8).allSatisfy(s -> assertThat(StubMetadata.projectId(s)).isEqualTo(project.id()));
  }

  @Test
  void installingTwiceGetsAFreeBasePath() {
    // Given
    projects.create(tree("{'orgId':'org1','name':'taken','slug':'example-petstore'}"));

    // When
    Project project = installer.install("rest-basics", "org1", "team1");

    // Then
    assertThat(project.slug()).isEqualTo("example-petstore-2");
  }

  @Test
  void runsTheExamplesImportsBeforeItsOwnStubs() {
    // When
    installer.install("soap-calculator", "org1", "team1");

    // Then
    assertThat(gateway.findAll()).hasSize(6).allSatisfy(s -> assertThat(StubMetadata.isSoap(s)).isTrue());
  }

  @Test
  void createsCrudResourcesWithTheirStartingRecords() {
    // When
    Project project = installer.install("crud", "org1", "team1");

    // Then
    CrudResource orders = new CrudResource("orders", "id", CrudResource.IdType.NUMBER);
    assertThat(crud.list(project.id(), orders, Map.of()).total()).isEqualTo(3);
    assertThat(gateway.findAll()).extracting(s -> s.getName()).contains("CRUD: orders", "CRUD: customers", "Order summary next to the CRUD resource");
  }

  @Test
  void savesTheExamplesResponseFilesAndPointsItsStubsAtThem() {
    // When
    Project project = installer.install("bookshop", "org1", "team1");

    // Then
    assertThat(files.list(project.id())).extracting(BodyFiles.Info::name).containsExactly("cover.svg", "price-list.csv");
    assertThat(gateway.findAll()).filteredOn(s -> "Price list as a CSV file".equals(s.getName())).singleElement()
        .satisfies(s -> assertThat(s.getResponse().getBodyFileName()).isEqualTo("projects/" + project.id() + "/price-list.csv"));
  }
}
