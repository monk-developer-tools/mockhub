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
import io.mockhub.controller.RuntimeController;
import io.mockhub.runtime.CorsForRequest;
import io.mockhub.runtime.CorsHeaders;
import io.mockhub.runtime.CorsTransformer;
import io.mockhub.runtime.DefaultResponseStubs;
import io.mockhub.runtime.LoadMeter;
import io.mockhub.runtime.ProjectScopeMatcher;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.runtime.RuntimeService;
import io.mockhub.runtime.RuntimeFilter;
import io.mockhub.runtime.RuntimeTransformer;
import io.mockhub.callout.CalloutListener;
import io.mockhub.callout.CalloutLog;
import io.mockhub.callout.CalloutRenderer;
import io.mockhub.callout.CalloutService;
import io.mockhub.callout.CalloutTargets;
import io.mockhub.history.HistoryCalloutLog;
import io.mockhub.history.HistoryRecorder;
import io.mockhub.history.HistoryStore;
import io.mockhub.history.TrafficCounters;
import io.mockhub.controller.HistoryController;
import io.mockhub.Main;
import io.mockhub.callout.SyncCalloutTransformer;
import io.mockhub.controller.CalloutController;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.mockhub.stub.DisabledStubMatcher;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.ai.JdkLlmTransport;
import io.mockhub.controller.AiController;
import io.mockhub.backup.BackupService;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.audit.AuditLog;
import io.mockhub.audit.AuditRecorder;
import io.mockhub.audit.FileAuditLog;
import io.mockhub.audit.LoggingAuditLog;
import io.mockhub.audit.ServiceAuditNames;
import io.mockhub.controller.AuditController;
import java.time.Duration;
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
import io.mockhub.importer.WireMockArchiveImporter;
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
    Path historyDir = dataDir.resolve("mockhub").resolve("history");
    HistoryStore history = new HistoryStore(historyDir, clock, Duration.ofDays(config.historyDays), config.historyMaxMb * 1024L * 1024L);
    TrafficCounters traffic = new TrafficCounters(historyDir, clock);
    ProjectService projects = new ProjectService(new FileProjectRepository(dataDir),
        List.of(stubs, new ProjectDataCleaner(dataStore, bodyFiles), history, traffic), clock);
    projectsRef.set(projects);
    UserService users = new UserService(new FileUserRepository(dataDir), config.users, new PasswordHasher(), clock);
    TeamService teams = new TeamService(new FileTeamRepository(dataDir), List.of(users), clock);
    OrganizationService orgs = new OrganizationService(new FileOrganizationRepository(dataDir), List.of(projects, teams, users), clock);
    projects.assignOrphans(orgs.ensureDefault().id());
    orgs.findAll().forEach(o -> teams.ensureDefault(o.id()));
    projects.assignTeams(orgId -> teams.ensureDefault(orgId).id());
    RequestLogService requestLog = new RequestLogService(gateway, projects);
    CalloutLog calloutLog = new HistoryCalloutLog(history);
    CalloutService callouts = new CalloutService(
        new CalloutRenderer(services::getTemplateEngine),
        new CalloutTargets(new TargetGuard(config.calloutAllowPrivate, InetAddress::getAllByName, "MOCKHUB_CALLOUT_ALLOW_PRIVATE"),
            () -> port.get() > 0 ? port.get() : services.getOptions().portNumber(), config.publicUrl),
        new JdkHttpSender(), calloutLog, clock);
    ScheduledExecutorService calloutScheduler = Executors.newScheduledThreadPool(4, r -> {
      Thread t = new Thread(r, "mockhub-callout");
      t.setDaemon(true);
      return t;
    });
    ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mockhub-history-maintenance");
      t.setDaemon(true);
      return t;
    });
    maintenance.scheduleWithFixedDelay(() -> safely(history::prune), 1, 30, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(traffic::save), 1, 1, TimeUnit.MINUTES);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      traffic.save();
      history.close();
    }, "mockhub-history-shutdown"));

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
    RateLimiter rateLimiter = new RateLimiter(clock);
    RuntimeService runtimeService = new RuntimeService(projects, new DefaultResponseStubs(stubs), rateLimiter);
    AssistantService assistant = new AssistantService(aiSettings, chat, stubs, history, clock, calloutLog, runtimeService);
    AssistantJobs assistantJobs = new AssistantJobs(assistant, Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mockhub-assistant");
      t.setDaemon(true);
      return t;
    }), clock);

    AuditLog auditLog = new LoggingAuditLog(new FileAuditLog(dataDir, Duration.ofDays(config.auditRetentionDays), clock));
    AuditRecorder auditRecorder = new AuditRecorder(auditLog, new ServiceAuditNames(projects, stubs, teams, orgs, users), clock);

    FakeData fakeData = new FakeData(config.fakeLocale);
    CorsHeaders corsHeaders = new CorsHeaders(config.stubCors);
    LoadMeter loadMeter = new LoadMeter(clock);
    CorsForRequest corsForRequest = new CorsForRequest(projects::findForPath, corsHeaders);
    PlaygroundHistory playgroundHistory = new PlaygroundHistory(dataDir);

    List<Extension> extensions = new ArrayList<>(List.of(
        new PersistenceListener(),
        new CrudTransformer(crud),
        new ErrorRateTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        new DisabledStubMatcher(),
        new CalloutListener(callouts, calloutScheduler),
        new HistoryRecorder(projects::findForPath, history, traffic),
        new SyncCalloutTransformer(callouts),
        new RuntimeFilter(projects::findForPath, corsHeaders, rateLimiter, loadMeter),
        new ApiKeyFilter(projects::findForPath, corsForRequest::headers),
        new RuntimeTransformer(projects::find, rateLimiter, loadMeter),
        new CorsTransformer(corsForRequest),
        new ProjectScopeMatcher(projects::findForPath),
        new FakeHelpers(fakeData),
        new AuthFilter(auth, policy),
        new MockHubAdminExtension(
            request -> auth.principal(request.getHeader("Authorization")).orElse(null),
            auditRecorder,
            List.of(
                new AuditController(auditLog, config.auditRetentionDays),
                new SystemController(auth, urls, dataDir.toString(), config.journalMaxEntries),
                new AuthController(auth, users, userViews, sso, ssoSettings, urls, config.loginNote),
                new OrganizationController(orgs, projects, users, userViews, teams),
                new TeamController(orgs, teams, projects, users, userViews),
                new UserController(users, userViews, auth, orgs, teams),
                new SsoController(ssoSettings, sso, orgs, teams, urls),
                new ProjectController(projects, views, teamChoice),
                new RuntimeController(runtimeService),
                new ExampleController(examples, new ExampleInstaller(examples, projects, stubs, importers, crud, bodyFiles), views, teamChoice),
                new StubController(stubs),
                new RequestLogController(requestLog, history),
                new HistoryController(projects, history, traffic, Main.VERSION),
                new CalloutController(calloutLog),
                new ImportExportController(projects, stubs, exporters, importers, urls, new WireMockArchiveImporter(bodyFiles)),
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

  private static void safely(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException e) {
      System.getLogger(MockHubExtensionFactory.class.getName()).log(System.Logger.Level.WARNING, "History maintenance failed: " + e.getMessage());
    }
  }
}
