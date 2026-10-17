package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import io.mockhub.Config;
import io.mockhub.ai.AiProviders;
import io.mockhub.ai.AiSettingsStore;
import io.mockhub.ai.AssistantJobs;
import io.mockhub.ai.AssistantService;
import io.mockhub.ai.BodyGenerator;
import io.mockhub.chaos.ErrorRateTransformer;
import io.mockhub.stub.DisabledStubMatcher;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.ai.JdkLlmTransport;
import io.mockhub.controller.AiController;
import io.mockhub.backup.BackupService;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudTransformer;
import io.mockhub.data.DataStore;
import io.mockhub.data.FileDataStore;
import io.mockhub.data.ProjectDataCleaner;
import io.mockhub.fake.DataGenerator;
import io.mockhub.fake.FakeData;
import io.mockhub.controller.PlaygroundController;
import io.mockhub.playground.JdkHttpSender;
import io.mockhub.playground.PlaygroundHistory;
import io.mockhub.playground.PlaygroundService;
import io.mockhub.playground.TargetGuard;
import java.net.InetAddress;
import io.mockhub.files.BodyFiles;
import io.mockhub.fake.FakeHelpers;
import io.mockhub.auth.ProjectScope;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Principal;
import io.mockhub.auth.TokenService;
import io.mockhub.controller.AuthController;
import io.mockhub.controller.BackupController;
import io.mockhub.controller.DataController;
import io.mockhub.controller.FilesController;
import io.mockhub.controller.BaseUrls;
import io.mockhub.controller.ExampleController;
import io.mockhub.controller.ImportExportController;
import io.mockhub.controller.OrganizationController;
import io.mockhub.controller.ProjectController;
import io.mockhub.controller.ProjectViews;
import io.mockhub.controller.RequestLogController;
import io.mockhub.controller.SsoController;
import io.mockhub.controller.StubController;
import io.mockhub.controller.SystemController;
import io.mockhub.controller.TeamChoice;
import io.mockhub.controller.TeamController;
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
import io.mockhub.team.FileTeamRepository;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.user.FileUserRepository;
import io.mockhub.user.PasswordHasher;
import io.mockhub.user.UserService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.wiremock.extensions.state.CaffeineStore;
import org.wiremock.extensions.state.StateExtension;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MockHubExtensionFactory implements ExtensionFactory {
  private final Config config;
  private final AtomicInteger port = new AtomicInteger();

  public MockHubExtensionFactory(Config config) {
    this.config = config;
  }

  public void serverStarted(int actualPort) {
    port.set(actualPort);
  }

  @Override
  public List<Extension> create(WireMockServices services) {
    Clock clock = Clock.systemUTC();
    Path dataDir = Path.of(services.getOptions().filesRoot().getPath());
    StubGateway gateway = new WireMockStubGateway(services.getAdmin());

    AtomicReference<ProjectService> projectsRef = new AtomicReference<>();
    StubService stubs = new StubService(gateway, projectsRef::get);
    DataStore dataStore = new FileDataStore(dataDir);
    BodyFiles bodyFiles = new BodyFiles(dataDir);
    CrudService crud = new CrudService(dataStore);
    ProjectService projects = new ProjectService(new FileProjectRepository(dataDir), List.of(stubs, new ProjectDataCleaner(dataStore, bodyFiles)), clock);
    projectsRef.set(projects);
    UserService users = new UserService(new FileUserRepository(dataDir), config.users, new PasswordHasher(), clock);
    TeamService teams = new TeamService(new FileTeamRepository(dataDir), List.of(users), clock);
    OrganizationService orgs = new OrganizationService(new FileOrganizationRepository(dataDir), List.of(projects, teams, users), clock);
    projects.assignOrphans(orgs.ensureDefault().id());
    orgs.findAll().forEach(o -> teams.ensureDefault(o.id()));
    projects.assignTeams(orgId -> teams.ensureDefault(orgId).id());
    RequestLogService requestLog = new RequestLogService(gateway, projects);

    SsoSettingsStore ssoSettings = new SsoSettingsStore(dataDir, config.ssoClientSecret);
    JdkHttpTransport http = new JdkHttpTransport();
    SsoService sso = new SsoService(
        List.of(new AzureEntraProvider(http, new JwtVerifier(http, clock), config.ssoAuthority)),
        ssoSettings, users, new RoleMapper(orgs::exists, id -> teams.find(id).map(Team::orgId)), clock);
    AuthService auth = new AuthService(users, TokenService.fromDataDir(dataDir, clock), ssoSettings::active);
    AccessPolicy policy = new AccessPolicy(id -> projects.find(id).map(p -> new ProjectScope(p.orgId(), p.teamId())));

    BaseUrls urls = new BaseUrls(config.publicUrl);
    ExporterRegistry exporters = new ExporterRegistry(List.of(new PostmanExporter(), new BrunoExporter(), new WireMockMappingsExporter()));
    ImporterRegistry importers = new ImporterRegistry(List.of(new WireMockJsonImporter(), new WsdlStubImporter(), new OpenApiImporter()));
    ProjectViews views = new ProjectViews(stubs, requestLog, urls, clock);
    UserViews userViews = new UserViews(orgs, teams);
    TeamChoice teamChoice = new TeamChoice(orgs, teams);
    ExampleCatalog examples = ExampleCatalog.fromClasspath();
    AiSettingsStore aiSettings = new AiSettingsStore(dataDir, config.aiApiKey);
    AiProviders aiProviders = AiProviders.defaults();
    ChatCompletions chat = new ChatCompletions(new JdkLlmTransport(), aiProviders, config.publicUrl);
    AssistantService assistant = new AssistantService(aiSettings, chat, stubs, requestLog);
    AssistantJobs assistantJobs = new AssistantJobs(assistant, Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mockhub-assistant");
      t.setDaemon(true);
      return t;
    }), clock);

    FakeData fakeData = new FakeData(config.fakeLocale);
    PlaygroundHistory playgroundHistory = new PlaygroundHistory(dataDir);

    List<Extension> extensions = new ArrayList<>(List.of(
        new PersistenceListener(),
        new CrudTransformer(crud),
        new ErrorRateTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        new DisabledStubMatcher(),
        new ApiKeyFilter(projects::findBySlug),
        new FakeHelpers(fakeData),
        new AuthFilter(auth, policy),
        new MockHubAdminExtension(
            request -> auth.principal(request.getHeader("Authorization")).orElse(null),
            List.of(
                new SystemController(auth, urls, dataDir.toString(), config.journalMaxEntries),
                new AuthController(auth, users, userViews, sso, ssoSettings, urls, config.loginNote),
                new OrganizationController(orgs, projects, users, userViews, teams),
                new TeamController(orgs, teams, projects, users, userViews),
                new UserController(users, userViews, auth, orgs, teams),
                new SsoController(ssoSettings, sso, orgs, teams, urls),
                new ProjectController(projects, views, teamChoice),
                new ExampleController(examples, new ExampleInstaller(examples, projects, stubs, importers), views, teamChoice),
                new StubController(stubs),
                new RequestLogController(requestLog),
                new ImportExportController(projects, stubs, exporters, importers, urls),
                new AiController(aiSettings, aiProviders, chat, assistant, assistantJobs, projects, urls, new BodyGenerator(aiSettings, chat)),
                new BackupController(new BackupService(orgs, teams, projects, gateway, dataStore, bodyFiles, clock)),
                new DataController(projects, stubs, dataStore, crud),
                new FilesController(projects, stubs, bodyFiles),
                new PlaygroundController(
                    new PlaygroundService(new JdkHttpSender(), new TargetGuard(config.playgroundAllowPrivate, InetAddress::getAllByName),
                        playgroundHistory, () -> "http://127.0.0.1:" + (port.get() > 0 ? port.get() : services.getOptions().portNumber()), clock),
                    playgroundHistory, new DataGenerator(fakeData), config.publicUrl)))));
    extensions.addAll(new StateExtension(new CaffeineStore()).create(services));
    return extensions;
  }
}
