package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import io.mockhub.Config;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import io.mockhub.auth.TokenService;
import io.mockhub.controller.AuthController;
import io.mockhub.controller.BackupController;
import io.mockhub.controller.BaseUrls;
import io.mockhub.controller.ExampleController;
import io.mockhub.controller.ImportExportController;
import io.mockhub.controller.OrgChoice;
import io.mockhub.controller.OrganizationController;
import io.mockhub.controller.ProjectController;
import io.mockhub.controller.ProjectViews;
import io.mockhub.controller.RequestLogController;
import io.mockhub.controller.SsoController;
import io.mockhub.controller.StubController;
import io.mockhub.controller.SystemController;
import io.mockhub.controller.UserController;
import io.mockhub.controller.UserViews;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.export.BrunoExporter;
import io.mockhub.export.ExporterRegistry;
import io.mockhub.export.PostmanExporter;
import io.mockhub.export.WireMockMappingsExporter;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.OpenApiImporter;
import io.mockhub.importer.WireMockJsonImporter;
import io.mockhub.importer.WsdlStubImporter;
import io.mockhub.journal.RequestLogService;
import io.mockhub.org.FileOrganizationRepository;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.FileProjectRepository;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.sso.AzureEntraProvider;
import io.mockhub.sso.JdkHttpTransport;
import io.mockhub.sso.JwtVerifier;
import io.mockhub.sso.RoleMapper;
import io.mockhub.sso.SsoService;
import io.mockhub.sso.SsoSettingsStore;
import io.mockhub.stub.StubGateway;
import io.mockhub.stub.StubService;
import io.mockhub.stub.WireMockStubGateway;
import io.mockhub.user.FileUserRepository;
import io.mockhub.user.PasswordHasher;
import io.mockhub.user.UserService;
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
    Clock clock = Clock.systemUTC();
    Path dataDir = Path.of(services.getOptions().filesRoot().getPath());
    StubGateway gateway = new WireMockStubGateway(services.getAdmin());

    AtomicReference<ProjectService> projectsRef = new AtomicReference<>();
    StubService stubs = new StubService(gateway, projectsRef::get);
    ProjectService projects = new ProjectService(new FileProjectRepository(dataDir), List.of(stubs), clock);
    projectsRef.set(projects);
    UserService users = new UserService(new FileUserRepository(dataDir), config.users, new PasswordHasher(), clock);
    OrganizationService orgs = new OrganizationService(new FileOrganizationRepository(dataDir), List.of(projects, users), clock);
    projects.assignOrphans(orgs.ensureDefault().id());
    RequestLogService requestLog = new RequestLogService(gateway, projects);

    SsoSettingsStore ssoSettings = new SsoSettingsStore(dataDir, config.ssoClientSecret);
    JdkHttpTransport http = new JdkHttpTransport();
    SsoService sso = new SsoService(
        List.of(new AzureEntraProvider(http, new JwtVerifier(http, clock), config.ssoAuthority)),
        ssoSettings, users, new RoleMapper(orgs::exists), clock);
    AuthService auth = new AuthService(users, TokenService.fromDataDir(dataDir, clock), ssoSettings::active);
    AccessPolicy policy = new AccessPolicy(id -> projects.find(id).map(Project::orgId));

    BaseUrls urls = new BaseUrls(config.publicUrl);
    ExporterRegistry exporters = new ExporterRegistry(List.of(new PostmanExporter(), new BrunoExporter(), new WireMockMappingsExporter()));
    ImporterRegistry importers = new ImporterRegistry(List.of(new WireMockJsonImporter(), new WsdlStubImporter(), new OpenApiImporter()));
    ProjectViews views = new ProjectViews(stubs, requestLog, urls, clock);
    UserViews userViews = new UserViews(orgs);
    OrgChoice orgChoice = new OrgChoice(orgs);
    ExampleCatalog examples = ExampleCatalog.fromClasspath();

    return List.of(
        new PersistenceListener(),
        new AuthFilter(auth, policy),
        new MockHubAdminExtension(
            request -> auth.principal(request.getHeader("Authorization")).orElse(null),
            List.of(
                new SystemController(auth, urls, dataDir.toString(), config.journalMaxEntries),
                new AuthController(auth, users, userViews, sso, ssoSettings, urls, config.loginNote),
                new OrganizationController(orgs, projects, users, userViews),
                new UserController(users, userViews, auth),
                new SsoController(ssoSettings, sso, orgs, urls),
                new ProjectController(projects, views, orgChoice),
                new ExampleController(examples, new ExampleInstaller(examples, projects, stubs, importers), views, orgChoice),
                new StubController(stubs),
                new RequestLogController(requestLog),
                new ImportExportController(projects, stubs, exporters, importers, urls),
                new BackupController(orgs, projects, gateway))));
  }
}
