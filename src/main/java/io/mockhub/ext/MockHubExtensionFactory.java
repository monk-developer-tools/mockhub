package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import io.mockhub.Config;
import io.mockhub.controller.BackupController;
import io.mockhub.controller.BaseUrls;
import io.mockhub.controller.ImportExportController;
import io.mockhub.controller.ExampleController;
import io.mockhub.controller.ProjectController;
import io.mockhub.controller.ProjectViews;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.controller.RequestLogController;
import io.mockhub.controller.StubController;
import io.mockhub.controller.SystemController;
import io.mockhub.export.BrunoExporter;
import io.mockhub.export.ExporterRegistry;
import io.mockhub.export.PostmanExporter;
import io.mockhub.export.WireMockMappingsExporter;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.OpenApiImporter;
import io.mockhub.importer.WireMockJsonImporter;
import io.mockhub.importer.WsdlStubImporter;
import io.mockhub.journal.RequestLogService;
import io.mockhub.project.FileProjectRepository;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubGateway;
import io.mockhub.stub.StubService;
import io.mockhub.stub.WireMockStubGateway;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class MockHubExtensionFactory implements ExtensionFactory {
  private final Config config;

  public MockHubExtensionFactory(Config config) {
    this.config = config;
  }

  @Override
  public List<Extension> create(WireMockServices services) {
    String dataDir = services.getOptions().filesRoot().getPath();
    StubGateway gateway = new WireMockStubGateway(services.getAdmin());

    AtomicReference<ProjectService> projectsRef = new AtomicReference<>();
    StubService stubs = new StubService(gateway, projectsRef::get);
    ProjectService projects = new ProjectService(new FileProjectRepository(Path.of(dataDir)), List.of(stubs), Clock.systemUTC());
    projectsRef.set(projects);
    RequestLogService requestLog = new RequestLogService(gateway, projects);

    AuthFilter auth = new AuthFilter(config.users);
    BaseUrls urls = new BaseUrls(config.publicUrl);
    ExporterRegistry exporters = new ExporterRegistry(List.of(new PostmanExporter(), new BrunoExporter(), new WireMockMappingsExporter()));
    ImporterRegistry importers = new ImporterRegistry(List.of(new WireMockJsonImporter(), new WsdlStubImporter(), new OpenApiImporter()));

    ProjectViews views = new ProjectViews(stubs, requestLog, urls);
    ExampleCatalog examples = ExampleCatalog.fromClasspath();

    return List.of(
        new PersistenceListener(),
        auth,
        new MockHubAdminExtension(List.of(
            new SystemController(auth, urls, dataDir, config.journalMaxEntries, config.loginNote),
            new ProjectController(projects, views),
            new ExampleController(examples, new ExampleInstaller(examples, projects, stubs, importers), views),
            new StubController(stubs),
            new RequestLogController(requestLog),
            new ImportExportController(projects, stubs, exporters, importers, urls),
            new BackupController(projects, gateway))));
  }
}
