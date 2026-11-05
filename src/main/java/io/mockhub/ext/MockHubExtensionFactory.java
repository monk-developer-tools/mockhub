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
import io.mockhub.controller.ConnectionController;
import io.mockhub.controller.DatasetController;
import io.mockhub.controller.HistoryController;
import io.mockhub.Main;
import io.mockhub.callout.SyncCalloutTransformer;
import io.mockhub.controller.GraphQlController;
import io.mockhub.controller.GrpcController;
import io.mockhub.grpc.FileDescriptorStore;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcRequestFilter;
import io.mockhub.controller.TlsController;
import io.mockhub.tls.TlsService;
import io.mockhub.tls.TlsStore;
import io.mockhub.graphql.ExampleOperations;
import io.mockhub.graphql.GraphQlEngine;
import io.mockhub.graphql.GraphQlTransformer;
import io.mockhub.graphql.MockSchemas;
import io.mockhub.importer.GraphQlStubImporter;
import io.mockhub.importer.SoapSampleImporter;
import io.mockhub.soap.SoapEndpointTransformer;
import io.mockhub.echo.EchoTransformer;
import io.mockhub.connection.ConnectionAuth;
import io.mockhub.connection.ConnectionProxyTransformer;
import io.mockhub.controller.CalloutController;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.mockhub.stub.DisabledStubMatcher;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.ai.JdkLlmTransport;
import io.mockhub.controller.AiController;
import io.mockhub.backup.BackupService;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.ai.MockTools;
import io.mockhub.ai.StagedChanges;
import io.mockhub.api.ApiRouter;
import io.mockhub.controller.AccessTokenController;
import io.mockhub.controller.ApiController;
import io.mockhub.controller.StagedController;
import io.mockhub.mcp.InternalApi;
import io.mockhub.mcp.McpServer;
import io.mockhub.mcp.McpTask;
import io.mockhub.mcp.McpTools;
import io.mockhub.mcp.ReviewStager;
import io.mockhub.token.AccessTokenService;
import io.mockhub.token.FileAccessTokenRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.Optional;
import com.github.tomakehurst.wiremock.http.Request;
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
import io.mockhub.data.RecordPolicy;
import io.mockhub.connection.ConnectionService;
import io.mockhub.connection.FileConnectionRepository;
import io.mockhub.connection.SecretBox;
import io.mockhub.connection.TokenClient;
import io.mockhub.dataset.DatasetService;
import io.mockhub.store.JsonFile;
import io.mockhub.dataset.FileDatasetRepository;
import io.mockhub.fake.DataGenerator;
import io.mockhub.fake.FakeData;
import io.mockhub.controller.PlaygroundController;
import io.mockhub.playground.JdkHttpSender;
import io.mockhub.playground.PlaygroundHistory;
import io.mockhub.playground.PlaygroundService;
import io.mockhub.playground.TargetGuard;
import io.mockhub.publictools.BinTool;
import io.mockhub.publictools.Bins;
import io.mockhub.publictools.EchoTool;
import io.mockhub.publictools.FakeTool;
import io.mockhub.publictools.InstantMockFilter;
import io.mockhub.publictools.InstantMockTool;
import io.mockhub.publictools.InstantExamples;
import io.mockhub.publictools.InstantMocks;
import io.mockhub.publictools.InstantRecordPolicy;
import io.mockhub.publictools.SandboxTemplateTransformer;
import io.mockhub.org.Organization;
import io.mockhub.publictools.PublicLimits;
import io.mockhub.publictools.PublicToolsFilter;
import io.mockhub.publictools.RenderTool;
import io.mockhub.publictools.TemplatePlayground;
import java.net.InetAddress;
import io.mockhub.files.BodyFiles;
import io.mockhub.fake.FakeHelpers;
import io.mockhub.template.JwtHelpers;
import io.mockhub.template.JwtKeys;
import io.mockhub.template.ValidationHelpers;
import io.mockhub.template.ValidationTransformer;
import io.mockhub.template.DatasetHelpers;
import io.mockhub.template.ValueHelpers;
import io.mockhub.template.WeightedTransformer;
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
import io.mockhub.runtime.IpAllowList;
import io.mockhub.controller.ProxyController;
import io.mockhub.proxy.ProxyService;
import io.mockhub.controller.OpenApiLinkController;
import io.mockhub.importer.SpecFetcher;
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
import io.mockhub.snapshot.FileSnapshotRepository;
import io.mockhub.snapshot.SnapshotCleanup;
import io.mockhub.snapshot.SnapshotRepository;
import io.mockhub.snapshot.SnapshotService;
import io.mockhub.controller.SnapshotController;
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
import io.mockhub.controller.StateController;
import io.mockhub.state.ProjectStateStore;
import io.mockhub.state.StateRepository;
import io.mockhub.state.StateScope;
import io.mockhub.state.StateScopeListener;
import org.wiremock.extensions.state.StateExtension;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
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
    CrudService crud = new CrudService(dataStore, config.publicTools
        ? new InstantRecordPolicy(id -> projectsRef.get().find(id).map(p -> Organization.INSTANT_ID.equals(p.orgId())).orElse(false))
        : RecordPolicy.NONE, config.maxRecords);
    Path historyDir = dataDir.resolve("mockhub").resolve("history");
    HistoryStore history = new HistoryStore(historyDir, clock, Duration.ofDays(config.historyDays), config.historyMaxMb * 1024L * 1024L);
    TrafficCounters traffic = new TrafficCounters(historyDir, clock);
    StagedChanges staged = new StagedChanges(dataDir, clock);
    SnapshotRepository snapshotStore = new FileSnapshotRepository(dataDir);
    UserService users = new UserService(new FileUserRepository(dataDir), config.users, new PasswordHasher(), clock);
    AccessTokenService accessTokens = new AccessTokenService(new FileAccessTokenRepository(dataDir), users::find,
        id -> projectsRef.get().find(id).isPresent(), clock);
    GrpcCatalog grpc = new GrpcCatalog(new FileDescriptorStore(dataDir), id -> projectsRef.get().find(id).map(Project::name).orElse(null));
    SecretBox secrets = SecretBox.of(config.secretKey, JsonFile.inDataDir(dataDir, "secret.key"));
    TlsService tls = new TlsService(new TlsStore(dataDir, secrets));
    ProxyService proxy = new ProxyService(config.proxyEnabled, IpAllowList.ofText(config.proxyAllow), config.proxyCaKeystore(),
        id -> projectsRef.get().find(id));
    MockHubServerFactory server = new MockHubServerFactory(services.getStores().getBlobStore("grpc"), grpc::fileDescriptors, tls::attach,
        host -> projectsRef.get().findForHost(host).map(Project::basePath), proxy);
    grpc.onChange(server::loadFileDescriptors);
    ProjectService projects = new ProjectService(new FileProjectRepository(dataDir),
        List.of(stubs, grpc, new ProjectDataCleaner(dataStore, bodyFiles), history, traffic, staged, accessTokens, new SnapshotCleanup(snapshotStore), proxy), clock);
    projectsRef.set(projects);
    TeamService teams = new TeamService(new FileTeamRepository(dataDir), List.of(users), clock);
    DatasetService datasets = new DatasetService(new FileDatasetRepository(dataDir), DatasetService.Limits.DEFAULT, clock);
    CalloutTargets calloutTargets = new CalloutTargets(new TargetGuard(config.calloutAllowPrivate, InetAddress::getAllByName, "MOCKHUB_CALLOUT_ALLOW_PRIVATE"),
        () -> port.get() > 0 ? port.get() : services.getOptions().portNumber(), config.publicUrl);
    ConnectionService connections = new ConnectionService(new FileConnectionRepository(dataDir),
        secrets,
        new TokenClient(new JdkHttpSender(), url -> calloutTargets.resolve(url, null).uri()), clock);
    OrganizationService orgs = new OrganizationService(new FileOrganizationRepository(dataDir), List.of(projects, teams, users, datasets, connections), clock);
    projects.assignOrphans(orgs.ensureDefault().id());
    orgs.findAll().forEach(o -> teams.ensureDefault(o.id()));
    projects.assignTeams(orgId -> teams.ensureDefault(orgId).id());
    RequestLogService requestLog = new RequestLogService(gateway, projects);
    StateScope stateScope = new StateScope();
    StateRepository stateRepository = new StateRepository(dataStore, () -> projects.findAll().stream().map(Project::id).toList(), config.maxRecords);
    CalloutLog calloutLog = new HistoryCalloutLog(history);
    ConnectionAuth connectionAuth = new ConnectionAuth(connections, id -> projects.find(id).map(Project::orgId));
    CalloutService callouts = new CalloutService(
        new CalloutRenderer(services::getTemplateEngine),
        calloutTargets,
        new JdkHttpSender(), calloutLog, clock, connectionAuth, Thread::sleep);
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
    maintenance.scheduleWithFixedDelay(() -> safely(accessTokens::flushUse), 1, 1, TimeUnit.MINUTES);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      traffic.save();
      history.close();
    }, "mockhub-history-shutdown"));

    SsoSettingsStore ssoSettings = new SsoSettingsStore(dataDir, config.ssoClientSecret);
    JdkHttpTransport http = new JdkHttpTransport();
    SsoService sso = new SsoService(
        List.of(new AzureEntraProvider(http, new JwtVerifier(http, clock), config.ssoAuthority)),
        ssoSettings, users, new RoleMapper(orgs::exists, id -> teams.find(id).map(Team::orgId)), clock);
    AuthService auth = new AuthService(users, TokenService.fromDataDir(dataDir, clock), ssoSettings::active, accessTokens::principal);
    AccessPolicy policy = new AccessPolicy(id -> projects.find(id).map(p -> new ProjectScope(p.orgId(), p.teamId())), config.publicTools);

    BaseUrls urls = new BaseUrls(config.publicUrl);
    ExporterRegistry exporters = new ExporterRegistry(List.of(new PostmanExporter(), new BrunoExporter(), new WireMockMappingsExporter()));
    MockSchemas graphQlSchemas = new MockSchemas();
    ExampleOperations graphQlExamples = new ExampleOperations(graphQlSchemas);
    ImporterRegistry importers = new ImporterRegistry(List.of(new WireMockJsonImporter(), new WsdlStubImporter(), new OpenApiImporter(),
        new GraphQlStubImporter(graphQlExamples), new SoapSampleImporter()));
    ProjectViews views = new ProjectViews(stubs, requestLog, urls, clock);
    UserViews userViews = new UserViews(orgs, teams);
    TeamChoice teamChoice = new TeamChoice(orgs, teams);
    ExampleCatalog examples = ExampleCatalog.fromClasspath();
    ExampleInstaller exampleInstaller = new ExampleInstaller(examples, projects, stubs, importers, crud, bodyFiles, grpc);
    AiSettingsStore aiSettings = new AiSettingsStore(dataDir, config.aiApiKey);
    AiProviders aiProviders = AiProviders.defaults();
    ChatCompletions chat = new ChatCompletions(new JdkLlmTransport(), aiProviders, config.publicUrl);
    RateLimiter rateLimiter = new RateLimiter(clock);
    RuntimeService runtimeService = new RuntimeService(projects, new DefaultResponseStubs(stubs), rateLimiter);
    SnapshotService snapshots = new SnapshotService(snapshotStore, projects, stubs, runtimeService, bodyFiles, clock);
    AssistantService assistant = new AssistantService(aiSettings, chat, stubs, history, clock, calloutLog, runtimeService, snapshots);
    AssistantJobs assistantJobs = new AssistantJobs(assistant, Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mockhub-assistant");
      t.setDaemon(true);
      return t;
    }), clock);

    AuditLog auditLog = new LoggingAuditLog(new FileAuditLog(dataDir, Duration.ofDays(config.auditRetentionDays), clock));
    AuditRecorder auditRecorder = new AuditRecorder(auditLog, new ServiceAuditNames(projects, stubs, teams, orgs, users), clock);

    FakeData fakeData = new FakeData(config.fakeLocale);
    FakeHelpers fakeHelpers = new FakeHelpers(fakeData);
    ValueHelpers valueHelpers = new ValueHelpers();
    DatasetHelpers datasetHelpers = new DatasetHelpers(datasets, () -> stateScope.current().flatMap(projects::find).map(Project::orgId));
    ValidationHelpers validationHelpers = new ValidationHelpers();
    JwtHelpers jwtHelpers = new JwtHelpers(new JwtKeys(dataDir), clock);
    CorsHeaders corsHeaders = new CorsHeaders(config.stubCors);
    LoadMeter loadMeter = new LoadMeter(clock);
    Function<String, Optional<Project>> projectForPath = segment -> grpc.ownerOf(segment).flatMap(projects::find).or(() -> projects.findForPath(segment));
    CorsForRequest corsForRequest = new CorsForRequest(projectForPath, corsHeaders);
    PlaygroundHistory playgroundHistory = new PlaygroundHistory(dataDir);

    Function<Request, Principal> principals = request -> auth.principal(request.getHeader("Authorization")).orElse(null);
    ApiRouter api = new ApiRouter(principals, auditRecorder);
    List<ApiController> controllers = List.of(
        new AuditController(auditLog, config.auditRetentionDays),
        new SystemController(auth, urls, dataDir.toString(), config.journalMaxEntries),
        new AuthController(auth, users, userViews, sso, ssoSettings, urls, config.loginNote),
        new OrganizationController(orgs, projects, users, userViews, teams),
        new TeamController(orgs, teams, projects, users, userViews),
        new UserController(users, userViews, auth, orgs, teams),
        new SsoController(ssoSettings, sso, orgs, teams, urls),
        new ProjectController(projects, views, teamChoice),
        new RuntimeController(runtimeService),
        new ExampleController(examples, exampleInstaller, views, teamChoice),
        new StubController(stubs, projects),
        new RequestLogController(requestLog, history),
        new HistoryController(projects, history, traffic, Main.VERSION),
        new CalloutController(calloutLog),
        new GraphQlController(projects, graphQlExamples),
        new GrpcController(projects, grpc),
        new TlsController(tls),
        new ProxyController(proxy),
        new ImportExportController(projects, stubs, exporters, importers, urls, new WireMockArchiveImporter(bodyFiles)),
        new OpenApiLinkController(new SpecFetcher(new JdkHttpSender(), new TargetGuard(config.playgroundAllowPrivate, InetAddress::getAllByName)), importers),
        new AiController(aiSettings, aiProviders, chat, assistant, assistantJobs, projects, urls, new BodyGenerator(aiSettings, chat)),
        new BackupController(new BackupService(orgs, teams, projects, gateway, dataStore, bodyFiles, datasets, connections, grpc, clock)),
        new DataController(projects, stubs, dataStore, crud),
        new StateController(projects, stateRepository, clock),
        new DatasetController(orgs, datasets),
        new ConnectionController(orgs, connections),
        new FilesController(projects, stubs, bodyFiles),
        new PlaygroundController(
            new PlaygroundService(new JdkHttpSender(), new TargetGuard(config.playgroundAllowPrivate, InetAddress::getAllByName),
                playgroundHistory, () -> "http://127.0.0.1:" + (port.get() > 0 ? port.get() : services.getOptions().portNumber()), clock),
            playgroundHistory, new DataGenerator(fakeData), config.publicUrl),
        new AccessTokenController(accessTokens, auth, projects, clock),
        new StagedController(projects, staged, assistant),
        new SnapshotController(snapshots, projects));
    controllers.forEach(c -> c.register(api));
    McpTools mcpTools = new McpTools(new InternalApi(api.task(), policy),
        new ReviewStager(staged, (projectId, pending) -> new MockTools(stubs, history, clock, calloutLog, runtimeService, snapshots::list, projectId, pending), policy),
        resource("/mcp/reference.md"), clock);
    McpServer mcpServer = new McpServer(mcpTools, Main.VERSION);

    PublicLimits publicLimits = new PublicLimits(rateLimiter);
    Bins bins = new Bins(clock);
    TemplatePlayground templatePlayground = new TemplatePlayground(List.of(fakeHelpers, valueHelpers, validationHelpers, jwtHelpers), renderWorkers(), 2000);
    if (config.publicTools) orgs.ensureInstant();
    InstantMocks instantMocks = new InstantMocks(dataDir, projects, stubs, crud, () -> teams.ensureDefault(Organization.INSTANT_ID).id(), clock);
    PublicToolsFilter publicTools = new PublicToolsFilter(config.publicTools, publicLimits, List.of(
        new EchoTool(),
        new BinTool(bins, publicLimits),
        new FakeTool(new DataGenerator(fakeData), publicLimits),
        new RenderTool(templatePlayground, publicLimits),
        new InstantMockTool(instantMocks, history, publicLimits, importers, new InstantExamples(examples, exampleInstaller))));
    maintenance.scheduleWithFixedDelay(() -> safely(bins::prune), 10, 10, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(instantMocks::prune), 5, 60, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(rateLimiter::prune), 10, 10, TimeUnit.MINUTES);

    List<Extension> extensions = new ArrayList<>(List.of(
        server,
        new GrpcRequestFilter(),
        publicTools,
        new InstantMockFilter(projectForPath, instantMocks),
        new SandboxTemplateTransformer(templatePlayground),
        new PersistenceListener(),
        new CrudTransformer(crud),
        new ErrorRateTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        new DisabledStubMatcher(),
        new CalloutListener(callouts, calloutScheduler),
        new HistoryRecorder(projectForPath, history, traffic),
        new StateScopeListener(projectForPath, stateScope),
        new SyncCalloutTransformer(callouts),
        new GraphQlTransformer(new GraphQlEngine(graphQlSchemas)),
        new SoapEndpointTransformer(),
        new EchoTransformer(projects::find),
        new ConnectionProxyTransformer(connectionAuth),
        new RuntimeFilter(projectForPath, corsHeaders, rateLimiter, loadMeter),
        new ApiKeyFilter(projectForPath, corsForRequest::headers),
        new RuntimeTransformer(projects::find, rateLimiter, loadMeter),
        new CorsTransformer(corsForRequest),
        new ProjectScopeMatcher(projectForPath),
        fakeHelpers,
        valueHelpers,
        datasetHelpers,
        validationHelpers,
        new ValidationTransformer(),
        new WeightedTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        jwtHelpers,
        new AuthFilter(auth, policy),
        new MockHubAdminExtension(api, new McpTask(mcpServer, principals))));
    extensions.addAll(new StateExtension(new ProjectStateStore(stateRepository, stateScope, clock)).create(services));
    return extensions;
  }

  private static String resource(String path) {
    try (InputStream in = MockHubExtensionFactory.class.getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing resource " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ExecutorService renderWorkers() {
    return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
      Thread t = new Thread(r, "mockhub-public-render");
      t.setDaemon(true);
      return t;
    });
  }

  private static void safely(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException e) {
      System.getLogger(MockHubExtensionFactory.class.getName()).log(System.Logger.Level.WARNING, "Maintenance failed: " + e.getMessage());
    }
  }
}
