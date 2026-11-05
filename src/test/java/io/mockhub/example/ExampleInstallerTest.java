package io.mockhub.example;

import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.data.CrudQuery;
import io.mockhub.data.CrudResource;
import io.mockhub.data.CrudService;
import io.mockhub.files.BodyFiles;
import io.mockhub.grpc.FileDescriptorStore;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.api.ApiException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExampleInstallerTest {

  private final FakeStubGateway gateway = new FakeStubGateway();
  private ProjectService projects;
  private ExampleInstaller installer;
  private CrudService crud;
  private BodyFiles files;
  private GrpcCatalog grpc;

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
    grpc = new GrpcCatalog(new FileDescriptorStore(dataDir), id -> id);
    installer = new ExampleInstaller(ExampleCatalog.fromClasspath(), projects, stubs, new ImporterRegistry(List.of(new WsdlStubImporter(), new OpenApiImporter())), crud, files, grpc);
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
    assertThat(crud.list(project.id(), orders, CrudQuery.ALL).total()).isEqualTo(3);
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

  @Test
  void installsTheDescriptorSetsOfAGrpcExample() {
    // When
    Project project = installer.install("grpc-inventory", "org1", "team1");

    // Then
    assertThat(grpc.setsOf(project.id())).singleElement().satisfies(set -> assertThat(set.name()).isEqualTo("inventory.dsc"));
    assertThat(grpc.ownerOf("inventory.v1.StockService")).contains(project.id());
    assertThat(gateway.findAll()).hasSize(6).allSatisfy(s -> assertThat(s.getRequest().getUrlPath()).startsWith("/inventory.v1.StockService/"));
  }

  @Test
  void aSecondGrpcExampleIsRefusedWithoutLeavingAProjectBehind() {
    // Given
    installer.install("grpc-inventory", "org1", "team1");

    // When / Then
    assertThatThrownBy(() -> installer.install("grpc-inventory", "org1", "team1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already served by");
    assertThat(projects.findAll()).hasSize(1);
  }
}
